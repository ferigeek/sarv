import os

os.environ.setdefault("DB_URL", "postgresql://localhost:5432/sarv")

import asyncio
import unittest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, patch

from analytics.db.queries.peak_hours import peak_activity_hours

UTC = timezone.utc
START = datetime(2026, 1, 1, tzinfo=UTC)
END = datetime(2026, 1, 2, tzinfo=UTC)


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


def run(coro):
    return asyncio.run(coro)


class PeakHoursQueryTest(unittest.TestCase):
    def query(self, rows, *args):
        with patch("analytics.db.pool.pool", FakePool(rows)):
            return run(peak_activity_hours(*args))

    def test_all_24_hours_zero_filled(self):
        result = self.query([(9, 500, 120)], START, END)
        self.assertEqual(result["timezone"], "UTC")
        self.assertEqual(len(result["buckets"]), 24)
        self.assertEqual(
            result["buckets"][0], {"hour": 0, "event_count": 0, "active_users": 0}
        )
        self.assertEqual(
            result["buckets"][9], {"hour": 9, "event_count": 500, "active_users": 120}
        )
        self.assertEqual(result["peak_hour"]["hour"], 9)

    def test_peak_tie_breaks_toward_earlier_hour(self):
        result = self.query([(18, 500, 90), (9, 500, 120)], START, END)
        self.assertEqual(result["peak_hour"]["hour"], 9)

    def test_empty_range_peaks_at_hour_zero(self):
        result = self.query([], START, END)
        self.assertEqual(
            result["peak_hour"], {"hour": 0, "event_count": 0, "active_users": 0}
        )

    def test_reversed_range_rejected(self):
        with self.assertRaises(ValueError):
            self.query([], END, START)


class PeakHoursEndpointTest(unittest.TestCase):
    def test_ok(self):
        from fastapi.testclient import TestClient
        import analytics.main as main

        result = {
            "timezone": "UTC",
            "peak_hour": {"hour": 18, "event_count": 900, "active_users": 200},
            "buckets": [
                {"hour": h, "event_count": 0, "active_users": 0} for h in range(24)
            ],
        }
        with patch.object(
            main, "peak_activity_hours", new=AsyncMock(return_value=result)
        ):
            resp = TestClient(main.app).get(
                "/usage/peak-hours",
                params={
                    "start_time": "2026-01-01T00:00:00Z",
                    "end_time": "2026-01-08T00:00:00Z",
                },
            )
        self.assertEqual(resp.status_code, 200)
        body = resp.json()
        self.assertEqual(body["peak_hour"]["hour"], 18)
        self.assertEqual(len(body["buckets"]), 24)

    def test_invalid_maps_to_422(self):
        from fastapi.testclient import TestClient
        import analytics.main as main

        with patch.object(
            main,
            "peak_activity_hours",
            new=AsyncMock(side_effect=ValueError("start_time must be before end_time.")),
        ):
            resp = TestClient(main.app).get(
                "/usage/peak-hours",
                params={
                    "start_time": "2026-01-02T00:00:00Z",
                    "end_time": "2026-01-01T00:00:00Z",
                },
            )
        self.assertEqual(resp.status_code, 422)


if __name__ == "__main__":
    unittest.main()
