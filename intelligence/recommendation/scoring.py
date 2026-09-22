from dataclasses import dataclass
from datetime import datetime, timezone
import math

MODEL_VERSION = "heuristic-v1"

AFFINITY_CAP = 10.0  # Affinity above this earns no extra boost
AFFINITY_RATE = 0.1  # Each affinity point adds 10% (max 2x at cap)

COMMENT_WEIGHT = 3  # Comments signal stronger intent than likes


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

    engagement = (
        2 * features.like_count
        + features.view_count
        + COMMENT_WEIGHT * features.comment_count
        - 2 * features.dislike_count
    )
    engagement = max(engagement, 0)

    age_hours = max((now - features.created_at).total_seconds() / 3600, 0)
    recency_boost = 1 / (1 + age_hours / 48)  # Half-life of about two days

    follow_boost = 1.5 if features.from_followed else 1.0

    affinity = min(max(features.author_affinity, 0), AFFINITY_CAP)
    affinity_boost = 1 + affinity * AFFINITY_RATE  # 1.0 (cold) to 2.0 (capped)

    return engagement * recency_boost * follow_boost * affinity_boost * features.user_boost


def engagement_boost(views: int, likes: int, comments: int) -> float:
    """
    Maps a user's like/comment rate to a 0.9-1.1 multiplier centered near
    average activity. New users with no views stay neutral at 1.0.
    """
    if views <= 0:
        return 1.0
    rate = min((likes + comments) / views, 1.0)
    return 0.9 + 0.2 * rate


FEATURE_NAMES = [
    "log_like",
    "log_dislike",
    "log_view",
    "log_comment",
    "age_hours",
    "from_followed",
    "affinity_capped",
    "user_boost",
]


def to_vector(features: PostFeatures, now: datetime | None = None) -> list[float]:
    """
    Shared model feature vector. Single definition used by serving (ranking)
    and training (dataset building) so the two cannot diverge. Counts are
    log-scaled; age and affinity enter raw (affinity capped as in scoring).
    """
    now = now or datetime.now(timezone.utc)
    age_hours = max((now - features.created_at).total_seconds() / 3600, 0)
    return [
        math.log1p(max(features.like_count, 0)),
        math.log1p(max(features.dislike_count, 0)),
        math.log1p(max(features.view_count, 0)),
        math.log1p(max(features.comment_count, 0)),
        age_hours,
        1.0 if features.from_followed else 0.0,
        min(max(features.author_affinity, 0.0), AFFINITY_CAP),
        features.user_boost,
    ]