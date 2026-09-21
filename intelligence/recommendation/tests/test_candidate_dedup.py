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


def run_with_rows(trending, following, follower):
    gen = CandidateGenerator("1")
    conn = MagicMock()
    conn.cursor.side_effect = [
        fake_cursor(trending),
        fake_cursor(following),
        fake_cursor(follower),
    ]

    async def fake_connection():
        yield conn

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
        trending=[(1, 10, 0, 100, NOW)],
        following=[(1, 10, 0, 100, NOW), (2, 5, 0, 10, NOW)],
        follower=[(3, 1, 0, 1, NOW)],
    )
    assert [c.post_id for c in cands] == ["1", "2", "3"]
    assert cands[0].from_followed is True


def test_follower_posts_get_no_boost_flag():
    cands = run_with_rows(
        trending=[],
        following=[],
        follower=[(9, 4, 0, 20, NOW)],
    )
    assert len(cands) == 1
    assert cands[0].post_id == "9"
    assert cands[0].from_followed is False


def test_dedup_preserves_first_seen_order():
    cands = run_with_rows(
        trending=[(1, 1, 0, 1, NOW), (2, 1, 0, 1, NOW)],
        following=[(2, 1, 0, 1, NOW), (3, 1, 0, 1, NOW)],
        follower=[],
    )
    assert [c.post_id for c in cands] == ["1", "2", "3"]
    assert cands[1].from_followed is True
