# Recommendation Service

FastAPI service for personalized feed ranking.

- `GET /feed?user_id=&page=&size=` → ranked `post_id` + `score` with `total` (see `main.py`)
- `GET /health` → health probe

Candidate generation (`candidate.py`) and scoring (`scoring.py`) details are documented in `docs/docs/en/6-Recommendation.md`.

Quickstart:
```bash
uv sync
uvicorn main:app --reload --port 8000
```

Docker: `docker compose up --build recommendation` (healthcheck on `GET /health`).
