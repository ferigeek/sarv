from contextlib import asynccontextmanager
from fastapi import FastAPI, Query
from candidate import CandidateGenerator
from database import close_pool, open_pool
from prometheus_fastapi_instrumentator import Instrumentator
from scoring import MODEL_VERSION, score_post


@asynccontextmanager
async def lifespan(app: FastAPI):
    await open_pool()
    yield
    await close_pool()


app = FastAPI(lifespan=lifespan)

# Excluded so scrape/healthcheck traffic doesn't drown out real request metrics.
Instrumentator(excluded_handlers=["/metrics", "/health"]).instrument(app).expose(app)


@app.get("/health")
async def health():
    return {"status": "ok", "model": MODEL_VERSION}


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
    """
    candidates = await CandidateGenerator(user_id).generate_candidates()
    scored = [(post, score_post(post)) for post in candidates]
    scored.sort(key=lambda item: item[1], reverse=True)

    total = len(scored)
    start = page * size
    end = start + size
    paged = scored[start:end] if start < total else []

    return {
        "user_id": user_id,
        "posts": [{"post_id": post.post_id, "score": score} for post, score in paged],
        "page": page,
        "size": size,
        "total": total,
    }
