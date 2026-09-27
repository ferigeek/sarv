# Analytics Service

Offline Python (FastAPI) service aggregating `event_logs`/`users` into admin reports (activity, event breakdown, engagement, peak hours, viewing time, most-active leaderboard).

Ships a plain-HTML/JS dashboard (`ui/`, Vite + Bootstrap + Chart.js, no framework) served by FastAPI itself: the Jinja shell loads first, then each panel fetches its API. Sign-in is required everywhere except `/health` — single admin account from `ANALYTICS_USER`/`ANALYTICS_PASSWORD`, no register flow.

## Run

```bash
uv sync
export DB_URL=postgresql://user:pass@localhost:5432/sarv
PYTHONPATH=.. uv run uvicorn analytics.main:app --port 8001
```

Dashboard: `http://localhost:8001/dashboard` (redirects to `/login`; dev default `admin`/`admin` with a startup warning). Swagger UI: `http://localhost:8001/docs` (also behind login). Package-style imports require `intelligence/` on the path (`PYTHONPATH=..`).

Or via compose from the repo root (builds the UI into the image):

```bash
docker compose up --build analytics
```

`ANALYTICS_USER`, `ANALYTICS_PASSWORD` and `ANALYTICS_SECRET_KEY` come from `.env` (see `.env.example`); all three must be overridden in production.

## Test

```bash
PYTHONPATH=.. uv run python -m unittest discover -s tests
```

Stdlib `unittest`, pool fully faked (`tests/fakes.py`) — no database needed. CI (`.github/workflows/analytics.yml`) runs the same command on changes under `intelligence/analytics/`.

## Layout

- `main.py` — app setup, admin-auth middleware, `/health`, `/dashboard`
- `auth.py` — stdlib signed-cookie sessions, credential check, login throttle
- `api/` — one router module per domain (routes + response models), plus `auth.py` (`/login`, `/logout`)
- `templates/` — `dashboard.html` (prod twin of the Vite shell), `login.html`
- `static/` — Vite build output (`dist/`, gitignored)
- `ui/` — dashboard source (`npm run build -- --watch` during development)
- `db/pool.py` — shared async pool; `db/queries/` — one module per domain + `_common.py` (intervals, bucketing, validation)
- `tests/` — one test module per domain, plus `test_auth.py`

Full endpoint reference, metric definitions and status: `docs/docs/en/8-Analytics.md` (Persian: `docs/docs/fa/8-Analytics.md`).
