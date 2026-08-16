from fastapi import FastAPI
from candidate import CandidateGenerator
from scoring import score_post

app = FastAPI()


@app.get("/feed")
async def get_feed(user_id: str):
    """
    Returns a ranked list of recommended post IDs with their scores
    for the given user.
    """
    candidates = CandidateGenerator(user_id).generate_candidates()
    ranked = sorted(candidates, key=score_post, reverse=True)

    return {
        "user_id": user_id,
        "posts": [{"post_id": p.post_id, "score": score_post(p)} for p in ranked],
    }