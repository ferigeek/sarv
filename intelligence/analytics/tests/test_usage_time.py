import os

os.environ.setdefault("DB_URL", "postgresql://localhost:5432/sarv")

import unittest
from datetime import datetime, timedelta, timezone
from unittest.mock import AsyncMock, patch

from analytics.db.queries._common import (
    build_bucket_starts,
    gap_fill,
    parse_interval,
)
from analytics.db.queries.usage_time import usage_activity_over_time

UTC = timezone.utc
START = datetime(2026, 1, 1, tzinfo=UTC)
END = datetime(2026, 1, 1, 3, tzinfo=UTC)


class FakeCur:
    def __init__(self, rows):
        self._rows = rows

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False

    async def execute(self, query, params):
        self.query = query
        self.params = params

    async def fetchall(self):
        return self._rows


class FakeConn:
    def __init__(self, rows):
        self._rows = rows

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False

    def cursor(self):
        return FakeCur(self._rows)


class FakePool:
    def __init__(self, rows):
        self._rows = rows

    def connection(self):
        return FakeConn(self._rows)


class ParseIntervalTest(unittest.TestCase):
    def test_valid_intervals(self):
        self.assertEqual(
            parse_interval("15m"), (timedelta(minutes=15), "15 minutes")
        )
        self.assertEqual(parse_interval("1h"), (timedelta(hours=1), "1 hours"))
        self.assertEqual(parse_interval("2d"), (timedelta(days=2), "2 days"))
        self.assertEqual(parse_interval("1w"), (timedelta(weeks=1), "1 weeks"))

    def test_whitespace_ok(self):
        self.assertEqual(parse_interval(" 1h ")[0], timedelta(hours=1))

    def test_invalid_intervals(self):
        for bad in ["", "abc", "1.5h", "0h", "1M", "1H", "1h; DROP TABLE x", "-5m"]:
            with self.subTest(bad=bad):
                with self.assertRaises(ValueError):
                    parse_interval(bad)


class BucketTest(unittest.TestCase):
    def test_build_bucket_starts(self):
        self.assertEqual(
            build_bucket_starts(START, END, timedelta(hours=1)),
            [START, START + timedelta(hours=1), START + timedelta(hours=2)],
        )

    def test_build_bucket_starts_partial_last_bucket(self):
        buckets = build_bucket_starts(START, START + timedelta(minutes=90), timedelta(hours=1))
        self.assertEqual(buckets, [START, START + timedelta(hours=1)])

    def test_gap_fill(self):
        buckets = build_bucket_starts(START, END, timedelta(hours=1))
        self.assertEqual(
            gap_fill(buckets, [(buckets[0], 5), (buckets[2], 3)]),
            [
                {"period_start": buckets[0], "active_users": 5},
                {"period_start": buckets[1], "active_users": 0},
                {"period_start": buckets[2], "active_users": 3},
            ],
        )


class UsageActivityTest(unittest.TestCase):
    def run_query(self, rows, *args):
        with patch("analytics.db.pool.pool", FakePool(rows)):
            import asyncio

            return asyncio.run(usage_activity_over_time(*args))

    def test_empty_range_returns_zeros(self):
        buckets = self.run_query([], START, END, "1h")
        self.assertEqual(len(buckets), 3)
        self.assertTrue(all(b["active_users"] == 0 for b in buckets))
        self.assertEqual(buckets[0]["period_start"], START)

    def test_sparse_rows_are_merged(self):
        rows = [(START, 5), (START + timedelta(hours=2), 3)]
        buckets = self.run_query(rows, START, END, "1h")
        self.assertEqual([b["active_users"] for b in buckets], [5, 0, 3])

    def test_reversed_range_rejected(self):
        with self.assertRaises(ValueError):
            self.run_query([], END, START, "1h")

    def test_bad_interval_rejected(self):
        with self.assertRaises(ValueError):
            self.run_query([], START, END, "bogus")

    def test_bucket_guard_rejected(self):
        far = datetime(2027, 1, 1, tzinfo=UTC)
        with self.assertRaises(ValueError):
            self.run_query([], START, far, "1m")


class UsageActivityEndpointTest(unittest.TestCase):
    def test_ok(self):
        from fastapi.testclient import TestClient
        import analytics.main as main

        buckets = [{"period_start": START, "active_users": 5}]
        with patch.object(
            main, "usage_activity_over_time", new=AsyncMock(return_value=buckets)
        ):
            resp = TestClient(main.app).get(
                "/usage/activity",
                params={
                    "start_time": "2026-01-01T00:00:00Z",
                    "end_time": "2026-01-01T02:00:00Z",
                    "interval": "1h",
                },
            )
        self.assertEqual(resp.status_code, 200)
        body = resp.json()
        self.assertEqual(body["interval"], "1h")
        self.assertEqual(body["buckets"][0]["active_users"], 5)

    def test_invalid_maps_to_422(self):
        from fastapi.testclient import TestClient
        import analytics.main as main

        with patch.object(
            main,
            "usage_activity_over_time",
            new=AsyncMock(side_effect=ValueError("start_time must be before end_time.")),
        ):
            resp = TestClient(main.app).get(
                "/usage/activity",
                params={
                    "start_time": "2026-01-01T02:00:00Z",
                    "end_time": "2026-01-01T00:00:00Z",
                    "interval": "1h",
                },
            )
        self.assertEqual(resp.status_code, 422)


if __name__ == "__main__":
    unittest.main()
