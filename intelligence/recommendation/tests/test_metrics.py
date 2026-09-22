from datetime import datetime, timedelta, timezone
from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

import main
from scoring import MODEL_VERSION, PostFeatures

NOW = datetime(2026, 9, 21, 12, 0, tzinfo=timezone.utc)


def test_metrics_expose_model_and_cache_counters():
    posts = [PostFeatures("1", 10, 0, 100, NOW - timedelta(hours=1), True)]
    with (
        patch.object(main, "get_cached_page", AsyncMock(return_value=None)),
        patch.object(main, "set_cached_page", AsyncMock()),
        patch.object(main, "CandidateGenerator") as gen,
    ):
        gen.return_value.generate_candidates = AsyncMock(return_value=posts)
        client = TestClient(main.app)
        assert client.get("/feed", params={"user_id": "7"}).status_code == 200
        body = client.get("/metrics").text
        assert "feed_cache_events_total" in body
        assert "feed_db_query_seconds" in body
        assert "feed_candidates_count" in body
        assert "feed_scoring_seconds" in body
        assert "feed_request_seconds" in body
        assert "feed_scores" in body
        assert MODEL_VERSION in body
        assert 'ranker="heuristic-v1"' in body
