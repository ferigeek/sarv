import asyncio
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock

import candidate as candidate_module
from candidate import CandidateGenerator

NOW = datetime(2026, 9, 21, 12, 0, tzinfo=timezone.utc)


def fake_cursor(rows):
    cur = MagicMock()
    cur.execute = AsyncMock()
    cur.fetchall = AsyncMock(return_value=rows)
    cur.__aenter__ = AsyncMock(return_value=cur)
    cur.__aexit__ = AsyncMock(return_value=False)
    return cur


def run_with_rows(trending, following, follower, affinity=None, engagement=None):
    gen = CandidateGenerator("1")
    conn = MagicMock()
    conn.cursor.side_effect = [
        fake_cursor(trending),
        fake_cursor(following),
        fake_cursor(follower),
        fake_cursor(affinity if affinity is not None else []),
        fake_cursor(engagement if engagement is not None else []),
    ]

    from contextlib import asynccontextmanager

    @asynccontextmanager
    async def fake_get_connection():
        yield conn

    original = candidate_module.get_connection
    candidate_module.get_connection = fake_get_connection
    try:
        return asyncio.run(gen.generate_candidates())
    finally:
        candidate_module.get_connection = original


def test_trending_following_overlap_keeps_flagged_copy():
    cands = run_with_rows(
        trending=[(1, 10, 0, 100, NOW, "11", 2)],
        following=[(1, 10, 0, 100, NOW, "11", 2), (2, 5, 0, 10, NOW, "12", 0)],
        follower=[(3, 1, 0, 1, NOW, "13", 0)],
    )
    assert [c.post_id for c in cands] == ["1", "2", "3"]
    assert cands[0].from_followed is True
    assert cands[0].author_id == "11"
    assert cands[0].comment_count == 2


def test_follower_posts_get_no_boost_flag():
    cands = run_with_rows(
        trending=[],
        following=[],
        follower=[(9, 4, 0, 20, NOW, "19", 1)],
    )
    assert len(cands) == 1
    assert cands[0].post_id == "9"
    assert cands[0].from_followed is False


def test_dedup_preserves_first_seen_order():
    cands = run_with_rows(
        trending=[(1, 1, 0, 1, NOW, "11", 0), (2, 1, 0, 1, NOW, "12", 0)],
        following=[(2, 1, 0, 1, NOW, "12", 0), (3, 1, 0, 1, NOW, "13", 0)],
        follower=[],
    )
    assert [c.post_id for c in cands] == ["1", "2", "3"]
    assert cands[1].from_followed is True


def test_affinity_applied_to_candidates():
    cands = run_with_rows(
        trending=[(1, 10, 0, 100, NOW, "11", 0)],
        following=[],
        follower=[],
        affinity=[("11", 7.0)],
    )
    assert cands[0].author_affinity == 7.0


def test_missing_affinity_defaults_to_zero():
    cands = run_with_rows(
        trending=[(1, 10, 0, 100, NOW, "11", 0)],
        following=[],
        follower=[],
        affinity=[("99", 7.0)],
    )
    assert cands[0].author_affinity == 0.0


def test_dedup_winner_keeps_flag_and_affinity():
    cands = run_with_rows(
        trending=[(1, 10, 0, 100, NOW, "11", 0)],
        following=[(1, 10, 0, 100, NOW, "11", 0)],
        follower=[],
        affinity=[("11", 4.0)],
    )
    assert len(cands) == 1
    assert cands[0].from_followed is True
    assert cands[0].author_affinity == 4.0
