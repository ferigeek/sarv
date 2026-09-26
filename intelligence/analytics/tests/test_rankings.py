import os

os.environ.setdefault("DB_URL", "postgresql://localhost:5432/sarv")

import unittest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, patch

from analytics.db.queries.rankings import most_active_users
from fakes import FakePool, run

UTC = timezone.utc
START = datetime(2026, 1, 1, tzinfo=UTC)
END = datetime(2026, 1, 2, tzinfo=UTC)


class MostActiveQueryTest(unittest.TestCase):
    def query(self, rows, *args, **kwargs):
        with patch("analytics.db.pool.pool", FakePool(rows)):
            return run(most_active_users(*args, **kwargs))

    def test_row_mapping(self):
        users = self.query(
            [(7, "feri", "Feri", 900), (3, "ana", "Ana", 120)], START, END, 10
        )
        self.assertEqual(
            users,
            [
                {
                    "user_id": 7,
                    "username": "feri",
                    "display_name": "Feri",
                    "event_count": 900,
                },
                {
                    "user_id": 3,
                    "username": "ana",
                    "display_name": "Ana",
                    "event_count": 120,
                },
            ],
        )

    def test_limit_is_bound_to_sql(self):
        pool = FakePool([(7, "feri", "Feri", 900)])
        with patch("analytics.db.pool.pool", pool):
            run(most_active_users(START, END, 5))
        self.assertEqual(pool.conns[0].params, (START, END, 5))

    def test_bad_limits_rejected(self):
        for bad in (0, 101):
            with self.subTest(bad=bad):
                with self.assertRaises(ValueError):
                    self.query([], START, END, bad)

    def test_reversed_range_rejected(self):
        with self.assertRaises(ValueError):
            self.query([], END, START, 10)


class MostActiveEndpointTest(unittest.TestCase):
    def test_ok_with_default_limit(self):
        from fastapi.testclient import TestClient
        import analytics.api.rankings as api_module
        import analytics.main as main

        users = [
            {"user_id": 7, "username": "feri", "display_name": "Feri", "event_count": 900}
        ]
        with patch.object(
            api_module, "most_active_users", new=AsyncMock(return_value=users)
        ):
            resp = TestClient(main.app).get(
                "/users/most-active",
                params={
                    "start_time": "2026-01-01T00:00:00Z",
                    "end_time": "2026-01-02T00:00:00Z",
                },
            )
        self.assertEqual(resp.status_code, 200)
        body = resp.json()
        self.assertEqual(body["limit"], 10)
        self.assertEqual(body["users"], users)

    def test_limit_out_of_range_is_422(self):
        from fastapi.testclient import TestClient
        import analytics.main as main

        resp = TestClient(main.app).get(
            "/users/most-active",
            params={
                "start_time": "2026-01-01T00:00:00Z",
                "end_time": "2026-01-02T00:00:00Z",
                "limit": 0,
            },
        )
        self.assertEqual(resp.status_code, 422)

    def test_invalid_maps_to_422(self):
        from fastapi.testclient import TestClient
        import analytics.api.rankings as api_module
        import analytics.main as main

        with patch.object(
            api_module,
            "most_active_users",
            new=AsyncMock(side_effect=ValueError("start_time must be before end_time.")),
        ):
            resp = TestClient(main.app).get(
                "/users/most-active",
                params={
                    "start_time": "2026-01-02T00:00:00Z",
                    "end_time": "2026-01-01T00:00:00Z",
                },
            )
        self.assertEqual(resp.status_code, 422)


if __name__ == "__main__":
    unittest.main()
