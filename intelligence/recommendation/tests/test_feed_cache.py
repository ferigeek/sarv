from datetime import datetime, timedelta, timezone
from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

import main
from scoring import PostFeatures

NOW = datetime(2026, 9, 21, 12, 0, tzinfo=timezone.utc)

POSTS = [
    PostFeatures("1", 50, 0, 500, NOW - timedelta(hours=1), True),
    PostFeatures("2", 5, 0, 50, NOW - timedelta(hours=2), False),
]


def test_cache_hit_skips_db():
    cached = {"posts": [{"post_id": "9", "score": 1.5}], "total": 1}
    with (
        patch.object(main, "get_cached_page", AsyncMock(return_value=cached)),
        patch.object(main, "set_cached_page", AsyncMock()) as setter,
        patch.object(main, "CandidateGenerator") as gen,
    ):
        body = TestClient(main.app).get("/feed", params={"user_id": "42"}).json()
        assert body["posts"] == cached["posts"]
        assert body["total"] == 1
        gen.assert_not_called()
        setter.assert_not_called()


def test_cache_miss_stores_result():
    with (
        patch.object(main, "get_cached_page", AsyncMock(return_value=None)),
        patch.object(main, "set_cached_page", AsyncMock()) as setter,
        patch.object(main, "CandidateGenerator") as gen,
    ):
        gen.return_value.generate_candidates = AsyncMock(return_value=list(POSTS))
        body = TestClient(main.app).get("/feed", params={"user_id": "42"}).json()
        assert body["total"] == 2
        assert [p["post_id"] for p in body["posts"]] == ["1", "2"]
        assert setter.await_count == 1
        key, payload = setter.await_args.args
        assert key == "feed:v0:user:42:page:0:size:20"
        assert payload["total"] == 2


def test_cache_error_falls_back_to_db():
    with (
        patch.object(main, "get_cached_page", AsyncMock(side_effect=RuntimeError("down"))),
        patch.object(main, "set_cached_page", AsyncMock()),
        patch.object(main, "CandidateGenerator") as gen,
    ):
        gen.return_value.generate_candidates = AsyncMock(return_value=list(POSTS))
        resp = TestClient(main.app).get("/feed", params={"user_id": "42"})
        assert resp.status_code == 200
        assert resp.json()["total"] == 2
