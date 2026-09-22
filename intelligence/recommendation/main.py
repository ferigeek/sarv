from contextlib import asynccontextmanager
from logging import getLogger
from time import perf_counter
from fastapi import FastAPI, Query
from cache import cache_key, close_cache, get_cached_page, open_cache, set_cached_page
from candidate import CandidateGenerator
from database import close_pool, open_pool
from metrics import MODEL_INFO, observe_cache, observe_request, observe_result, observe_scoring
from model import HEURISTIC_VERSION, LR_VERSION, load_model, rank_heuristic, rank_posts
from prometheus_fastapi_instrumentator import Instrumentator

log = getLogger(__name__)

_pipe = None
_active_version = HEURISTIC_VERSION


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _pipe, _active_version
    try:
        await open_pool()
    except Exception:
        # Stay up without a pool (local runs, early boot): /feed 500s with a
        # clear error until DB_* is configured, while /health and /metrics
        # keep serving. The backend falls back to chronological meanwhile.
        log.exception("Database pool failed to open, starting degraded")
    await open_cache()
    _pipe = load_model()
    _active_version = LR_VERSION if _pipe is not None else HEURISTIC_VERSION
    MODEL_INFO.info({"version": _active_version})
    yield
    await close_cache()
    await close_pool()


app = FastAPI(lifespan=lifespan)

# Excluded so scrape/healthcheck traffic doesn't drown out real request metrics.
Instrumentator(excluded_handlers=["/metrics", "/health"]).instrument(app).expose(app)


@app.get("/health")
async def health():
    return {"status": "ok", "model": _active_version}


@app.get("/feed")
async def get_feed(
    user_id: str,
    page: int = Query(0, ge=0, description="Zero-based page index"),
    size: int = Query(20, ge=1, le=100, description="Page size"),
):
    """
    Returns a ranked list of recommended post IDs with their scores
    for the given user. Supports pagination via page/size forwarded from
    the backend; sorting is always by server-side ranking (score desc).
    Read-through Redis cache first; any cache failure bypasses to the DB.
    Ranking uses the learned model when loaded, else the heuristic.
    """
    started = perf_counter()
    key = cache_key(user_id, page, size)
    try:
        cached = await get_cached_page(key)
    except Exception:
        cached = None
    if cached is not None:
        observe_cache("hit")
        observe_result(cached["total"], [p["score"] for p in cached["posts"]])
        observe_request("hit", _active_version, perf_counter() - started)
        return {
            "user_id": user_id,
            "posts": cached["posts"],
            "page": page,
            "size": size,
            "total": cached["total"],
        }

    observe_cache("miss")
    candidates = await CandidateGenerator(user_id).generate_candidates()
    scoring_started = perf_counter()
    ranker_used = HEURISTIC_VERSION
    if _pipe is not None:
        try:
            scored = rank_posts(candidates, _pipe)
            ranker_used = LR_VERSION
        except Exception:
            log.warning("Learned ranking failed, falling back to heuristic", exc_info=True)
            scored = rank_heuristic(candidates)
    else:
        scored = rank_heuristic(candidates)
    observe_scoring(ranker_used, perf_counter() - scoring_started)

    total = len(scored)
    start = page * size
    end = start + size
    paged = scored[start:end] if start < total else []
    posts = [{"post_id": post.post_id, "score": score} for post, score in paged]
    observe_result(total, [score for _, score in paged])

    try:
        await set_cached_page(key, {"posts": posts, "total": total})
    except Exception:
        pass
    observe_request("miss", ranker_used, perf_counter() - started)

    return {
        "user_id": user_id,
        "posts": posts,
        "page": page,
        "size": size,
        "total": total,
    }
