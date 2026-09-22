# Recommendation Service

FastAPI service for personalized feed ranking (`heuristic-v1`: engagement + recency + follow/affinity/user boosts; see `scoring.py`).

- `GET /feed?user_id=&page=&size=` → ranked `post_id` + `score` with `total` (see `main.py`; read-through Redis cache `feed:v0:*`, bypass on failure)
- `GET /health` → health probe with `status` + `model`
- `GET /metrics` → Prometheus (default histograms + `feed_cache/db/candidates/scoring/request/result/scores` series + `feed_model_info`)

Modules: `candidate.py` (async `_fetch` + dedup), `scoring.py` (formula + `MODEL_VERSION`), `database.py` (`AsyncConnectionPool` lifespan), `cache.py` (Redis, optional), `metrics.py`.

Env: `DB_*` (incl. `DB_POOL_MIN/MAX/TIMEOUT`), `REDIS_URL` (default `redis://localhost:6379`), `FEED_CACHE_TTL_SECONDS` (default `45`).

Candidate generation (`candidate.py`) and scoring (`scoring.py`) details are documented in `docs/docs/en/6-Recommendation.md`.

Quickstart:
```bash
uv sync
uvicorn main:app --reload --port 8000
```

Docker: `docker compose up --build recommendation` (healthcheck on `GET /health`).
