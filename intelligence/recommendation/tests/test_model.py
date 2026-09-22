from datetime import datetime, timedelta, timezone
from unittest.mock import AsyncMock, patch

import numpy as np
from fastapi.testclient import TestClient

import main
from model import HEURISTIC_VERSION, LR_VERSION, load_model, rank_heuristic, rank_posts
from scoring import PostFeatures

NOW = datetime(2026, 9, 21, 12, 0, tzinfo=timezone.utc)

POSTS = [
    PostFeatures("1", 50, 0, 500, NOW - timedelta(hours=1), True),
    PostFeatures("2", 5, 0, 50, NOW - timedelta(hours=2), False),
]


class StubPipe:
    def __init__(self, proba):
        self._proba = np.array(proba)

    def predict_proba(self, X):
        assert X.shape == (len(POSTS), 8)
        return np.column_stack([1 - self._proba, self._proba])


def feed_with_pipe(pipe):
    old_pipe, old_version = main._pipe, main._active_version
    main._pipe = pipe
    main._active_version = LR_VERSION if pipe is not None else HEURISTIC_VERSION
    try:
        with (
            patch.object(main, "get_cached_page", AsyncMock(return_value=None)),
            patch.object(main, "set_cached_page", AsyncMock()),
            patch.object(main, "CandidateGenerator") as gen,
        ):
            gen.return_value.generate_candidates = AsyncMock(return_value=list(POSTS))
            yield TestClient(main.app)
    finally:
        main._pipe, main._active_version = old_pipe, old_version


def test_rank_posts_orders_by_probability():
    ranked = rank_posts(list(POSTS), StubPipe([0.2, 0.9]))
    assert [p.post_id for p, _ in ranked] == ["2", "1"]
    assert ranked[0][1] == 0.9


def test_learned_model_serves_probabilities():
    for client in feed_with_pipe(StubPipe([0.2, 0.9])):
        body = client.get("/feed", params={"user_id": "42"}).json()
        assert [p["post_id"] for p in body["posts"]] == ["2", "1"]
        assert body["posts"][0]["score"] == 0.9


def test_model_error_falls_back_to_heuristic():
    class BrokenPipe:
        def predict_proba(self, X):
            raise RuntimeError("boom")

    for client in feed_with_pipe(BrokenPipe()):
        body = client.get("/feed", params={"user_id": "42"}).json()
        assert body["posts"][0]["post_id"] == "1"  # heuristic order restored
        assert body["total"] == 2


def test_load_model_missing_returns_none():
    assert load_model("/nonexistent/model.pkl") is None


def test_load_model_corrupt_returns_none(tmp_path):
    bad = tmp_path / "model.pkl"
    bad.write_bytes(b"not a pickle")
    assert load_model(str(bad)) is None


def test_rank_heuristic_matches_score_order():
    ranked = rank_heuristic(list(POSTS))
    assert [p.post_id for p, _ in ranked] == ["1", "2"]
