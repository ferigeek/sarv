from fastapi import FastAPI, Query
from candidate import CandidateGenerator
from scoring import score_post

app = FastAPI()


@app.get("/health")
async def health():
    return {"status": "ok"}


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
    candidates = CandidateGenerator(user_id).generate_candidates()
    ranked = sorted(candidates, key=score_post, reverse=True)

    total = len(ranked)
    start = page * size
    end = start + size
    paged = ranked[start:end] if start < total else []

    return {
        "user_id": user_id,
        "posts": [{"post_id": p.post_id, "score": score_post(p)} for p in paged],
        "page": page,
        "size": size,
        "total": total,
    }
