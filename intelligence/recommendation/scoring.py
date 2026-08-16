from dataclasses import dataclass
from datetime import datetime, timezone


@dataclass
class PostFeatures:
    """Features of a post used to compute its recommendation score."""
    post_id: str
    like_count: int
    dislike_count: int
    view_count: int
    created_at: datetime
    from_followed: bool = False  # Author is followed by the requesting user


def score_post(features: PostFeatures, now: datetime | None = None) -> float:
    """
    Computes a recommendation score for a post.

    The score rewards engagement (likes and views, penalising dislikes) while
    decaying it over time so that newer posts are preferred. Posts from users
    that the requesting user follows get a small boost.
    """
    now = now or datetime.now(timezone.utc)

    engagement = 2 * features.like_count + features.view_count - 2 * features.dislike_count
    engagement = max(engagement, 0)

    age_hours = max((now - features.created_at).total_seconds() / 3600, 0)
    recency_boost = 1 / (1 + age_hours / 48)  # Half-life of about two days

    follow_boost = 1.5 if features.from_followed else 1.0

    return engagement * recency_boost * follow_boost