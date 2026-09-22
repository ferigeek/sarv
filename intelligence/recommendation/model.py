import logging

import joblib
import numpy as np
from pydantic_settings import BaseSettings

from scoring import MODEL_VERSION as HEURISTIC_VERSION
from scoring import FEATURE_NAMES, PostFeatures, score_post, to_vector

log = logging.getLogger(__name__)

LR_VERSION = "lr-v1"


class ModelSettings(BaseSettings):
    model_path: str = "models/model.pkl"

    class Config:
        env_file = ".env"


def load_model(path: str | None = None):
    """Loads the learned ranker. Returns None (never raises) when the
    artifact is missing or corrupt, so serving falls back to heuristic."""
    target = path or ModelSettings().model_path
    try:
        pipe = joblib.load(target)
        pipe.predict_proba(np.zeros((1, len(FEATURE_NAMES))))
        log.info("Learned ranker loaded from %s", target)
        return pipe
    except Exception:
        log.warning("Learned model unavailable at %s, using heuristic", target, exc_info=True)
        return None


def rank_posts(posts: list, pipe, now=None) -> list:
    """Ranks by P(positive) desc in one vectorized call. Raises to the
    caller on failure so the request can fall back to heuristic."""
    vectors = np.array([to_vector(post, now=now) for post in posts])
    proba = pipe.predict_proba(vectors)[:, 1]
    ranked = sorted(
        zip(posts, (float(score) for score in proba)),
        key=lambda item: item[1],
        reverse=True,
    )
    return ranked


def rank_heuristic(posts: list, now=None) -> list:
    scored = [(post, score_post(post, now=now)) for post in posts]
    scored.sort(key=lambda item: item[1], reverse=True)
    return scored
