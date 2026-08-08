from datetime import datetime, timedelta, timezone
from typing import List
from database import get_connection


class CandidateGenerator:
    def __init__(self, user_id: str):
        self.user_id = user_id
        self.search_span_days = 7  # How many days back to look for trending posts

    def generate_candidates(self) -> List[str]:
        """
        Returns a list of candidate post IDs for the user,
        based on trending posts and recent posts from followings/followers.
        """
        with get_connection() as conn:
            trending = self._get_trending_posts(conn)
            following_posts = self._get_following_posts(conn)
            follower_posts = self._get_follower_posts(conn)

        # Combine and deduplicate, preserving order (trending first, then followings, then followers)
        seen = set()
        candidates = []
        for post_id in trending + following_posts + follower_posts:
            if post_id not in seen:
                seen.add(post_id)
                candidates.append(post_id)

        return candidates

    def _get_trending_posts(self, conn) -> List[str]:
        """
        Returns a list of trending post IDs.
        Trending = posts with high engagement (likes + views) in the recent time window.
        """
        cutoff = datetime.now(timezone.utc) - timedelta(days=self.search_span_days)

        query = """
            SELECT id
            FROM posts
            WHERE deleted_at IS NULL
              AND created_at >= %s
              AND type = 'NORMAL'
            ORDER BY (like_count + view_count) DESC, created_at DESC
            LIMIT 100
        """

        with conn.cursor() as cur:
            cur.execute(query, (cutoff,))
            rows = cur.fetchall()

        return [str(row[0]) for row in rows]

    def _get_following_posts(self, conn) -> List[str]:
        """
        Returns recent posts from users that the current user follows.
        """
        cutoff = datetime.now(timezone.utc) - timedelta(days=self.search_span_days)

        query = """
            SELECT p.id
            FROM posts p
            JOIN follows f ON p.user_id = f.followed_id
            WHERE f.follower_id = %s
              AND p.deleted_at IS NULL
              AND p.created_at >= %s
              AND p.type = 'NORMAL'
            ORDER BY p.created_at DESC
            LIMIT 50
        """

        with conn.cursor() as cur:
            cur.execute(query, (self.user_id, cutoff))
            rows = cur.fetchall()

        return [str(row[0]) for row in rows]

    def _get_follower_posts(self, conn) -> List[str]:
        """
        Returns recent posts from users that follow the current user.
        """
        cutoff = datetime.now(timezone.utc) - timedelta(days=self.search_span_days)

        query = """
            SELECT p.id
            FROM posts p
            JOIN follows f ON p.user_id = f.follower_id
            WHERE f.followed_id = %s
              AND p.deleted_at IS NULL
              AND p.created_at >= %s
              AND p.type = 'NORMAL'
            ORDER BY p.created_at DESC
            LIMIT 50
        """

        with conn.cursor() as cur:
            cur.execute(query, (self.user_id, cutoff))
            rows = cur.fetchall()

        return [str(row[0]) for row in rows]