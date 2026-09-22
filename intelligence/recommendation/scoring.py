from dataclasses import dataclass
from datetime import datetime, timezone

MODEL_VERSION = "heuristic-v1"

AFFINITY_CAP = 10.0  # Affinity above this earns no extra boost
AFFINITY_RATE = 0.1  # Each affinity point adds 10% (max 2x at cap)


@dataclass
class PostFeatures:
    """Features of a post used to compute its recommendation score."""
    post_id: str
    like_count: int
    dislike_count: int
    view_count: int
    created_at: datetime
    from_followed: bool = False  # Author is followed by the requesting user
    author_id: str = ""  # Post author (users.id); for affinity lookup
    comment_count: int = 0
    author_affinity: float = 0.0  # Weighted past interaction with the author
    user_boost: float = 1.0  # Requesting user's engagement level multiplier


def score_post(features: PostFeatures, now: datetime | None = None) -> float:
    """
    Computes a recommendation score for a post.

    The score rewards engagement (likes, comments, and views, penalising
    dislikes) while decaying it over time so that newer posts are preferred.
    Posts from followed authors get a boost, as do posts from authors the
    requesting user has interacted with before (affinity).
    """
    now = now or datetime.now(timezone.utc)

    engagement = 2 * features.like_count + features.view_count - 2 * features.dislike_count
    engagement = max(engagement, 0)

    age_hours = max((now - features.created_at).total_seconds() / 3600, 0)
    recency_boost = 1 / (1 + age_hours / 48)  # Half-life of about two days

    follow_boost = 1.5 if features.from_followed else 1.0

    affinity = min(max(features.author_affinity, 0), AFFINITY_CAP)
    affinity_boost = 1 + affinity * AFFINITY_RATE  # 1.0 (cold) to 2.0 (capped)

    return engagement * recency_boost * follow_boost * affinity_boost * features.user_boost