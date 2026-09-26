import os

os.environ.setdefault("DB_URL", "postgresql://localhost:5432/sarv")

import unittest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, patch

from analytics.db.queries.viewing_time import (
    viewing_time_over_time,
    viewing_time_summary,
)
from fakes import FakePool, run

UTC = timezone.utc
START = datetime(2026, 1, 1, tzinfo=UTC)
MID = datetime(2026, 1, 1, 1, tzinfo=UTC)
END = datetime(2026, 1, 1, 2, tzinfo=UTC)


class ViewingSummaryTest(unittest.TestCase):
    def query(self, results, *args, **kwargs):
        with patch("analytics.db.pool.pool", FakePool(results)):
            return run(viewing_time_summary(*args, **kwargs))

    def test_overall_and_split_mapping(self):
        results = [
            (100, 450000),
            [("FEED", 60, 300000), ("DETAIL", 35, 140000), ("UNKNOWN", 5, 10000)],
        ]
        summary = self.query(results, START, END)
        self.assertEqual(
            summary["overall"], {"average_duration_ms": 4500.0, "samples": 100}
        )
        self.assertEqual(
            summary["by_source"]["FEED"],
            {"average_duration_ms": 5000.0, "samples": 60},
        )
        self.assertEqual(
            summary["by_source"]["UNKNOWN"],
            {"average_duration_ms": 2000.0, "samples": 5},
        )

    def test_missing_sources_zero_filled(self):
        summary = self.query([(10, 50000), [("FEED", 10, 50000)]], START, END)
        self.assertEqual(
            summary["by_source"]["DETAIL"],
            {"average_duration_ms": None, "samples": 0},
        )

    def test_empty_range_returns_null_average(self):
        summary = self.query([(0, 0), []], START, END)
        self.assertEqual(
            summary["overall"], {"average_duration_ms": None, "samples": 0}
        )

    def test_bad_source_rejected(self):
        with self.assertRaises(ValueError):
            self.query([(0, 0), []], START, END, source="POPUP")

    def test_reversed_range_rejected(self):
        with self.assertRaises(ValueError):
            self.query([(0, 0), []], END, START)


class ViewingOverTimeTest(unittest.TestCase):
    def query(self, results, *args, **kwargs):
        with patch("analytics.db.pool.pool", FakePool(results)):
            return run(viewing_time_over_time(*args, **kwargs))

    def test_overall_is_weighted_not_mean_of_means(self):
        rows = [(START, "FEED", 2, 200), (START, "DETAIL", 3, 900)]
        buckets = self.query([rows], START, END, "1h")
        self.assertEqual(buckets[0]["average_duration_ms"], 220.0)
        self.assertEqual(buckets[0]["samples"], 5)
        self.assertIsNone(buckets[1]["average_duration_ms"])
        self.assertEqual(buckets[1]["samples"], 0)

    def test_empty_rows_return_null_buckets(self):
        buckets = self.query([[]], START, END, "1h")
        self.assertEqual(len(buckets), 2)
        self.assertTrue(all(b["average_duration_ms"] is None for b in buckets))

    def test_bad_interval_rejected(self):
        with self.assertRaises(ValueError):
            self.query([[]], START, END, "bogus")

    def test_bucket_guard_rejected(self):
        far = datetime(2027, 1, 1, tzinfo=UTC)
        with self.assertRaises(ValueError):
            self.query([[]], START, far, "1m")


class ViewingEndpointTest(unittest.TestCase):
    def test_without_interval_skips_bucket_queries(self):
        from fastapi.testclient import TestClient
        import analytics.api.viewing_time as api_module
        import analytics.main as main

        summary = {
            "overall": {"average_duration_ms": 4500.0, "samples": 100},
            "by_source": {"FEED": {"average_duration_ms": 5000.0, "samples": 60}},
        }
        with (
            patch.object(
                api_module, "viewing_time_summary", new=AsyncMock(return_value=summary)
            ),
            patch.object(
                api_module, "viewing_time_over_time", new=AsyncMock()
            ) as over_time,
        ):
            resp = TestClient(main.app).get(
                "/engagement/viewing-time",
                params={
                    "start_time": "2026-01-01T00:00:00Z",
                    "end_time": "2026-01-02T00:00:00Z",
                },
            )
        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.json()["overall"], summary["overall"])
        self.assertIsNone(resp.json()["buckets"])
        over_time.assert_not_awaited()

    def test_with_interval_and_source(self):
        from fastapi.testclient import TestClient
        import analytics.api.viewing_time as api_module
        import analytics.main as main

        summary = {
            "overall": {"average_duration_ms": 5000.0, "samples": 60},
            "by_source": {},
        }
        buckets = [
            {
                "period_start": START,
                "average_duration_ms": 5000.0,
                "samples": 60,
                "by_source": {},
            }
        ]
        with (
            patch.object(
                api_module, "viewing_time_summary", new=AsyncMock(return_value=summary)
            ),
            patch.object(
                api_module, "viewing_time_over_time", new=AsyncMock(return_value=buckets)
            ) as over_time,
        ):
            resp = TestClient(main.app).get(
                "/engagement/viewing-time",
                params={
                    "start_time": "2026-01-01T00:00:00Z",
                    "end_time": "2026-01-02T00:00:00Z",
                    "interval": "1h",
                    "source": "FEED",
                },
            )
        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.json()["buckets"][0]["samples"], 60)
        over_time.assert_awaited_once()
        self.assertEqual(over_time.await_args.args[3], "FEED")

    def test_invalid_maps_to_422(self):
        from fastapi.testclient import TestClient
        import analytics.api.viewing_time as api_module
        import analytics.main as main

        with patch.object(
            api_module,
            "viewing_time_summary",
            new=AsyncMock(
                side_effect=ValueError(
                    "Invalid source 'POPUP': expected 'FEED' or 'DETAIL'."
                )
            ),
        ):
            resp = TestClient(main.app).get(
                "/engagement/viewing-time",
                params={
                    "start_time": "2026-01-01T00:00:00Z",
                    "end_time": "2026-01-02T00:00:00Z",
                    "source": "POPUP",
                },
            )
        self.assertEqual(resp.status_code, 422)


if __name__ == "__main__":
    unittest.main()
