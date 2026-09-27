# Analytics Service

Offline Python (FastAPI) service aggregating `event_logs`/`users` into admin reports (activity, event breakdown, engagement, peak hours, viewing time, most-active leaderboard).

## Run

```bash
uv sync
export DB_URL=postgresql://user:pass@localhost:5432/sarv
PYTHONPATH=.. uv run uvicorn analytics.main:app --port 8001
```

Swagger UI: `http://localhost:8001/docs`. Package-style imports require `intelligence/` on the path (`PYTHONPATH=..`).

## Test

```bash
PYTHONPATH=.. uv run python -m unittest discover -s tests
```

Stdlib `unittest`, pool fully faked (`tests/fakes.py`) — no database needed. CI (`.github/workflows/analytics.yml`) runs the same command on changes under `intelligence/analytics/`.

## Layout

- `main.py` — app setup only (`include_router` per domain)
- `api/` — one router module per domain (routes + response models)
- `db/pool.py` — shared async pool; `db/queries/` — one module per domain + `_common.py` (intervals, bucketing, validation)
- `tests/` — one test module per domain

Full endpoint reference, metric definitions and status: `docs/docs/en/8-Analytics.md` (Persian: `docs/docs/fa/8-Analytics.md`).
