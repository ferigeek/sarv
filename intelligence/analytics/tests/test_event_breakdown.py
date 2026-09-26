import os

os.environ.setdefault("DB_URL", "postgresql://localhost:5432/sarv")

import unittest
from datetime import datetime, timedelta, timezone
from unittest.mock import AsyncMock, patch

from analytics.db.queries.event_breakdown import (
    KNOWN_EVENT_TYPES,
    event_breakdown_over_time,
    event_totals,
)
from fakes import FakePool, run

UTC = timezone.utc
START = datetime(2026, 1, 1, tzinfo=UTC)
END = datetime(2026, 1, 1, 2, tzinfo=UTC)


class EventTotalsTest(unittest.TestCase):
    def query(self, rows, *args):
        with patch("analytics.db.pool.pool", FakePool(rows)):
            return run(event_totals(*args))

    def test_all_types_present_and_zero_filled(self):
        totals = self.query([("LOGIN", 1240), ("VIEW_POST", 8912)], START, END)
        self.assertEqual(len(totals), len(KNOWN_EVENT_TYPES))
        by_type = {t["event_type"]: t["count"] for t in totals}
        self.assertEqual(by_type["LOGIN"], 1240)
        self.assertEqual(by_type["VIEW_POST"], 8912)
        self.assertEqual(by_type["LIKE_POST"], 0)
        self.assertEqual(by_type["QUOTE_POST"], 0)

    def test_empty_table_returns_all_zeros(self):
        totals = self.query([], START, END)
        self.assertTrue(all(t["count"] == 0 for t in totals))

    def test_reversed_range_rejected(self):
        with self.assertRaises(ValueError):
            self.query([], END, START)


class EventBreakdownOverTimeTest(unittest.TestCase):
    def query(self, rows, *args):
        with patch("analytics.db.pool.pool", FakePool(rows)):
            return run(event_breakdown_over_time(*args))

    def test_sparse_rows_pivoted_onto_full_grid(self):
        rows = [(START, "LOGIN", 41)]
        buckets = self.query(rows, START, END, "1h")
        self.assertEqual(len(buckets), 2)
        self.assertEqual(buckets[0]["counts"]["LOGIN"], 41)
        self.assertEqual(buckets[1]["counts"]["LOGIN"], 0)
        self.assertEqual(len(buckets[0]["counts"]), len(KNOWN_EVENT_TYPES))
        self.assertEqual(buckets[0]["counts"]["VIEW_POST"], 0)

    def test_empty_rows_return_zero_buckets(self):
        buckets = self.query([], START, END, "1h")
        self.assertEqual(len(buckets), 2)
        self.assertTrue(
            all(c == 0 for b in buckets for c in b["counts"].values())
        )

    def test_bad_interval_rejected(self):
        with self.assertRaises(ValueError):
            self.query([], START, END, "bogus")

    def test_bucket_guard_rejected(self):
        far = datetime(2027, 1, 1, tzinfo=UTC)
        with self.assertRaises(ValueError):
            self.query([], START, far, "1m")


class EventBreakdownEndpointTest(unittest.TestCase):
    def test_without_interval_returns_totals_only(self):
        from fastapi.testclient import TestClient
        import analytics.main as main

        totals = [{"event_type": "LOGIN", "count": 1240}]
        with (
            patch.object(main, "event_totals", new=AsyncMock(return_value=totals)),
            patch.object(main, "event_breakdown_over_time", new=AsyncMock()) as breakdown,
        ):
            resp = TestClient(main.app).get(
                "/events/breakdown",
                params={
                    "start_time": "2026-01-01T00:00:00Z",
                    "end_time": "2026-01-01T02:00:00Z",
                },
            )
        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.json()["totals"], totals)
        self.assertIsNone(resp.json()["buckets"])
        breakdown.assert_not_awaited()

    def test_with_interval_returns_buckets(self):
        from fastapi.testclient import TestClient
        import analytics.main as main

        buckets = [{"period_start": START, "counts": {"LOGIN": 41}}]
        with (
            patch.object(
                main, "event_totals", new=AsyncMock(return_value=[])
            ),
            patch.object(
                main, "event_breakdown_over_time", new=AsyncMock(return_value=buckets)
            ),
        ):
            resp = TestClient(main.app).get(
                "/events/breakdown",
                params={
                    "start_time": "2026-01-01T00:00:00Z",
                    "end_time": "2026-01-01T02:00:00Z",
                    "interval": "1h",
                },
            )
        self.assertEqual(resp.status_code, 200)
        body = resp.json()
        self.assertEqual(body["interval"], "1h")
        self.assertEqual(body["buckets"][0]["counts"], {"LOGIN": 41})

    def test_invalid_maps_to_422(self):
        from fastapi.testclient import TestClient
        import analytics.main as main

        with patch.object(
            main,
            "event_totals",
            new=AsyncMock(side_effect=ValueError("start_time must be before end_time.")),
        ):
            resp = TestClient(main.app).get(
                "/events/breakdown",
                params={
                    "start_time": "2026-01-01T02:00:00Z",
                    "end_time": "2026-01-01T00:00:00Z",
                },
            )
        self.assertEqual(resp.status_code, 422)


if __name__ == "__main__":
    unittest.main()
