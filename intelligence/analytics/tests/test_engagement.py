import os

os.environ.setdefault("DB_URL", "postgresql://localhost:5432/sarv")

import asyncio
import unittest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, patch

from analytics.db.queries.engagement import (
    engagement_over_time,
    engagement_totals,
)

UTC = timezone.utc
START = datetime(2026, 1, 1, tzinfo=UTC)
MID = datetime(2026, 1, 1, 1, tzinfo=UTC)
END = datetime(2026, 1, 1, 2, tzinfo=UTC)


class QueuedCur:
    """Fake cursor serving a queue of result sets, one per execute."""

    def __init__(self, results):
        self._results = list(results)
        self.queries = []

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False

    async def execute(self, query, params):
        self.queries.append(query)
        self.params = params

    async def fetchall(self):
        return self._results.pop(0)

    async def fetchone(self):
        return self._results.pop(0)


class FakeConn:
    def __init__(self, results):
        self._cur = QueuedCur(results)

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False

    def cursor(self):
        return self._cur


class FakePool:
    def __init__(self, results):
        self._results = results

    def connection(self):
        return FakeConn(self._results)


def run(coro):
    return asyncio.run(coro)


class EngagementTotalsTest(unittest.TestCase):
    def query(self, results, *args):
        with patch("analytics.db.pool.pool", FakePool(results)):
            return run(engagement_totals(*args))

    def test_totals_mapping(self):
        totals = self.query([(100, 60, 10, 50)], START, END)
        self.assertEqual(
            totals,
            {
                "total_users": 100,
                "active_users": 60,
                "new_users": 10,
                "returning_users": 50,
            },
        )

    def test_reversed_range_rejected(self):
        with self.assertRaises(ValueError):
            self.query([(0, 0, 0, 0)], END, START)


class EngagementOverTimeTest(unittest.TestCase):
    def query(self, results, *args):
        with patch("analytics.db.pool.pool", FakePool(results)):
            return run(engagement_over_time(*args))

    def test_buckets_merge_and_cumulate(self):
        results = [
            [(START, 30), (MID, 30)],
            [(START, 10)],
            [(START, 25), (MID, 20)],
            (90,),
        ]
        buckets = self.query(results, START, END, "1h")
        self.assertEqual(
            buckets,
            [
                {
                    "period_start": START,
                    "total_users": 100,
                    "active_users": 30,
                    "new_users": 10,
                    "returning_users": 25,
                },
                {
                    "period_start": MID,
                    "total_users": 100,
                    "active_users": 30,
                    "new_users": 0,
                    "returning_users": 20,
                },
            ],
        )

    def test_empty_rows_return_zero_buckets_with_base_total(self):
        buckets = self.query([[], [], [], (7,)], START, END, "1h")
        self.assertEqual(len(buckets), 2)
        self.assertTrue(all(b["total_users"] == 7 for b in buckets))
        self.assertTrue(all(b["active_users"] == 0 for b in buckets))

    def test_bad_interval_rejected(self):
        with self.assertRaises(ValueError):
            self.query([[], [], [], (0,)], START, END, "bogus")

    def test_bucket_guard_rejected(self):
        far = datetime(2027, 1, 1, tzinfo=UTC)
        with self.assertRaises(ValueError):
            self.query([[], [], [], (0,)], START, far, "1m")


class EngagementEndpointTest(unittest.TestCase):
    def test_without_interval_skips_bucket_queries(self):
        from fastapi.testclient import TestClient
        import analytics.main as main

        totals = {
            "total_users": 100,
            "active_users": 60,
            "new_users": 10,
            "returning_users": 50,
        }
        with (
            patch.object(main, "engagement_totals", new=AsyncMock(return_value=totals)),
            patch.object(main, "engagement_over_time", new=AsyncMock()) as over_time,
        ):
            resp = TestClient(main.app).get(
                "/users/engagement",
                params={
                    "start_time": "2026-01-01T00:00:00Z",
                    "end_time": "2026-01-01T02:00:00Z",
                },
            )
        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.json()["totals"], totals)
        self.assertIsNone(resp.json()["buckets"])
        over_time.assert_not_awaited()

    def test_with_interval_returns_buckets(self):
        from fastapi.testclient import TestClient
        import analytics.main as main

        buckets = [
            {
                "period_start": START,
                "total_users": 100,
                "active_users": 30,
                "new_users": 10,
                "returning_users": 25,
            }
        ]
        with (
            patch.object(
                main,
                "engagement_totals",
                new=AsyncMock(
                    return_value={
                        "total_users": 100,
                        "active_users": 60,
                        "new_users": 10,
                        "returning_users": 50,
                    }
                ),
            ),
            patch.object(
                main, "engagement_over_time", new=AsyncMock(return_value=buckets)
            ),
        ):
            resp = TestClient(main.app).get(
                "/users/engagement",
                params={
                    "start_time": "2026-01-01T00:00:00Z",
                    "end_time": "2026-01-01T02:00:00Z",
                    "interval": "1h",
                },
            )
        self.assertEqual(resp.status_code, 200)
        body = resp.json()
        self.assertEqual(body["interval"], "1h")
        self.assertEqual(body["buckets"][0]["returning_users"], 25)

    def test_invalid_maps_to_422(self):
        from fastapi.testclient import TestClient
        import analytics.main as main

        with patch.object(
            main,
            "engagement_totals",
            new=AsyncMock(side_effect=ValueError("start_time must be before end_time.")),
        ):
            resp = TestClient(main.app).get(
                "/users/engagement",
                params={
                    "start_time": "2026-01-01T02:00:00Z",
                    "end_time": "2026-01-01T00:00:00Z",
                },
            )
        self.assertEqual(resp.status_code, 422)


if __name__ == "__main__":
    unittest.main()
