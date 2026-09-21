from datetime import datetime, timedelta, timezone
from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

import main
from scoring import MODEL_VERSION, PostFeatures

NOW = datetime(2026, 9, 21, 12, 0, tzinfo=timezone.utc)

POSTS = [
    PostFeatures("1", 50, 0, 500, NOW - timedelta(hours=1), True),
    PostFeatures("2", 5, 0, 50, NOW - timedelta(hours=2), False),
    PostFeatures("3", 1, 0, 5, NOW - timedelta(hours=3), False),
]


def make_client(posts):
    with patch.object(main, "CandidateGenerator") as gen:
        gen.return_value.generate_candidates = AsyncMock(return_value=posts)
        client = TestClient(main.app)
        yield client


def test_feed_returns_ranked_page_with_total():
    for client in make_client(list(POSTS)):
        resp = client.get("/feed", params={"user_id": "42", "page": 0, "size": 2})
        assert resp.status_code == 200
        body = resp.json()
        assert body["user_id"] == "42"
        assert body["page"] == 0
        assert body["size"] == 2
        assert body["total"] == 3
        assert [p["post_id"] for p in body["posts"]] == ["1", "2"]
        scores = [p["score"] for p in body["posts"]]
        assert scores == sorted(scores, reverse=True)


def test_feed_out_of_range_page_returns_empty():
    for client in make_client(list(POSTS)):
        body = client.get("/feed", params={"user_id": "42", "page": 5, "size": 10}).json()
        assert body["posts"] == []
        assert body["total"] == 3


def test_feed_rejects_invalid_pagination():
    for client in make_client(list(POSTS)):
        assert client.get("/feed", params={"user_id": "42", "page": -1}).status_code == 422
        assert client.get("/feed", params={"user_id": "42", "size": 0}).status_code == 422
        assert client.get("/feed", params={"user_id": "42", "size": 101}).status_code == 422


def test_health_reports_model_version():
    client = TestClient(main.app)
    body = client.get("/health").json()
    assert body["status"] == "ok"
    assert body["model"] == MODEL_VERSION
