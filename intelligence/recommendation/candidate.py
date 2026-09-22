from datetime import datetime, timedelta, timezone
from time import perf_counter
from typing import List
from database import get_connection
from metrics import observe_candidates, observe_db_query
from scoring import PostFeatures


class CandidateGenerator:
    # Past interaction counts for longer than the candidate window so
    # older relationships still personalize the feed. Plain views only;
    # dwell-time weighting is deferred. Weights mirror intent strength.
    AFFINITY_WINDOW_DAYS = 30

    def __init__(self, user_id: str):
        self.user_id = user_id
        self.search_span_days = 7  # How many days back to look for trending posts

    async def generate_candidates(self) -> List[PostFeatures]:
        """
        Returns candidate posts for the user as a list of PostFeatures.
        Trending posts come first, then posts from followings and followers.
        Only authors the user follows get the follow boost; duplicates keep
        the copy with the stronger (followed, affinity) signal.
        Each post also carries its author's affinity for scoring.
        """
        async with get_connection() as conn:
            trending = await self._get_trending_posts(conn)
            following_posts = await self._get_following_posts(conn)
            follower_posts = await self._get_follower_posts(conn)
            affinity = await self._get_author_affinity(conn)

        all_posts = trending + following_posts + follower_posts
        for post in all_posts:
            post.author_affinity = affinity.get(post.author_id, 0.0)

        # Combine and deduplicate by post id, preserving first-seen order
        # but preferring the copy with the stronger personal signal.
        by_id = {}
        for post in all_posts:
            existing = by_id.get(post.post_id)
            if existing is None:
                by_id[post.post_id] = post
            elif (post.from_followed, post.author_affinity) > (
                existing.from_followed,
                existing.author_affinity,
            ):
                by_id[post.post_id] = post

        candidates = list(by_id.values())
        observe_candidates("trending", len(trending))
        observe_candidates("following", len(following_posts))
        observe_candidates("follower", len(follower_posts))
        observe_candidates("deduped", len(candidates))
        return candidates

    async def _get_author_affinity(self, conn) -> dict:
        """
        Returns {author_id: affinity} for authors the user interacted with
        in the affinity window. One batched query (no N+1), riding the
        event_logs user and post indexes.
        """
        cutoff = datetime.now(timezone.utc) - timedelta(days=self.AFFINITY_WINDOW_DAYS)

        query = """
            SELECT p.user_id AS author_id,
                   SUM(CASE e.type
                         WHEN 'VIEW_POST' THEN 1
                         WHEN 'LIKE_POST' THEN 3
                         WHEN 'CREATE_COMMENT' THEN 4
                         WHEN 'REPOST_POST' THEN 5
                         WHEN 'QUOTE_POST' THEN 5
                         WHEN 'DISLIKE_POST' THEN -2
                         ELSE 0 END) AS affinity
            FROM event_logs e
            JOIN posts p ON p.id = e.post_id
            WHERE e.user_id = %s
              AND e.post_id IS NOT NULL
              AND e.type IN ('VIEW_POST', 'LIKE_POST', 'DISLIKE_POST',
                             'CREATE_COMMENT', 'REPOST_POST', 'QUOTE_POST')
              AND e.created_at >= %s
            GROUP BY p.user_id
        """

        start = perf_counter()
        async with conn.cursor() as cur:
            await cur.execute(query, (self.user_id, cutoff))
            rows = await cur.fetchall()
        observe_db_query("affinity", perf_counter() - start)

        return {str(author_id): float(score) for author_id, score in rows}

    async def _fetch(self, conn, query: str, params: tuple, *, source: str, from_followed: bool = False) -> List[PostFeatures]:
        """Runs one candidate query and maps rows to PostFeatures."""
        start = perf_counter()
        async with conn.cursor() as cur:
            await cur.execute(query, params)
            rows = await cur.fetchall()
        observe_db_query(source, perf_counter() - start)

        return [
            PostFeatures(
                str(row[0]), row[1], row[2], row[3], row[4],
                from_followed=from_followed, author_id=str(row[5]), comment_count=row[6],
            )
            for row in rows
        ]

    async def _get_trending_posts(self, conn) -> List[PostFeatures]:
        """
        Returns trending posts, i.e. posts with high engagement in the recent
        time window, ordered by engagement.
        """
        cutoff = datetime.now(timezone.utc) - timedelta(days=self.search_span_days)

        query = """
            SELECT id, like_count, dislike_count, view_count, created_at,
                   user_id, comment_count
            FROM posts
            WHERE deleted_at IS NULL
              AND created_at >= %s
              AND type = 'NORMAL'
            ORDER BY (like_count + view_count) DESC, created_at DESC
            LIMIT 100
        """

        return await self._fetch(conn, query, (cutoff,), source="trending")

    async def _get_following_posts(self, conn) -> List[PostFeatures]:
        """
        Returns recent posts from users that the current user follows.
        """
        cutoff = datetime.now(timezone.utc) - timedelta(days=self.search_span_days)

        query = """
            SELECT p.id, p.like_count, p.dislike_count, p.view_count, p.created_at,
                   p.user_id, p.comment_count
            FROM posts p
            JOIN follows f ON p.user_id = f.followed_id
            WHERE f.follower_id = %s
              AND p.deleted_at IS NULL
              AND p.created_at >= %s
              AND p.type = 'NORMAL'
            ORDER BY p.created_at DESC
            LIMIT 50
        """

        return await self._fetch(conn, query, (self.user_id, cutoff), source="following", from_followed=True)

    async def _get_follower_posts(self, conn) -> List[PostFeatures]:
        """
        Returns recent posts from users that follow the current user.
        These get no follow boost: the boost is reserved for authors
        the requesting user follows.
        """
        cutoff = datetime.now(timezone.utc) - timedelta(days=self.search_span_days)

        query = """
            SELECT p.id, p.like_count, p.dislike_count, p.view_count, p.created_at,
                   p.user_id, p.comment_count
            FROM posts p
            JOIN follows f ON p.user_id = f.follower_id
            WHERE f.followed_id = %s
              AND p.deleted_at IS NULL
              AND p.created_at >= %s
              AND p.type = 'NORMAL'
            ORDER BY p.created_at DESC
            LIMIT 50
        """

        return await self._fetch(conn, query, (self.user_id, cutoff), source="follower")
