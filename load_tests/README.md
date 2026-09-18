# Sarv load tests (Locust)

Read-heavy realistic traffic against the Spring backend (`core_backend`,
default `http://localhost:8080`). Each virtual user logs in once with a
pre-seeded account, then loops over feed reads, post views, searches,
profiles, and occasional writes (posts, reactions, follows).

## Layout

```text
load_tests/
  locustfile.py      # SarvUser: weighted tasks, Web UI entrypoint
  seed.py            # idempotent user + post seeder -> data/users.csv
  src/load_tests/
    config.py        # env overrides (base URL, files, timeouts)
    data.py          # content/search pools, CSV + Spring Page helpers
  data/              # generated users.csv (gitignored)
```

## Prerequisites

Backend (and Postgres) running:

```bash
docker compose up --build -d postgres core_backend recommendation
```

Install deps (once):

```bash
uv sync
```

## Seed, then run (Web UI)

```bash
uv run python seed.py --users 50
uv run locust -f locustfile.py -H http://localhost:8080
```

Open `http://localhost:8089`, set users / spawn rate / host, and start.
Suggested starting points:

| Profile  | Users | Spawn/s | Time  |
|----------|-------|---------|-------|
| Smoke    | 10    | 2       | 2 min |
| Baseline | 100   | 5       | 10 min|
| Soak     | 50    | 2       | 30 min|

`seed.py` is idempotent: existing `lt-NNNN` users fall back to login.
To skip post seeding: `uv run python seed.py --users 50 --no-seed-posts`.

## Configuration (env vars)

| Var                   | Default                  | Meaning                          |
|-----------------------|--------------------------|----------------------------------|
| `SARV_BASE_URL`       | `http://localhost:8080`  | Backend base URL                 |
| `SARV_USERS_FILE`     | `data/users.csv`         | Seeded credentials               |
| `SARV_SEED_PASSWORD`  | `Password123!`           | Password for seeded users        |
| `SARV_USERNAME` / `SARV_PASSWORD` | empty        | Single-user fallback if no CSV   |
| `SARV_PAGE_SIZE`      | `20`                     | Feed page size                   |
| `SARV_REQUEST_TIMEOUT`| `10`                     | Seed HTTP timeout (seconds)      |

Without `data/users.csv` and without `SARV_USERNAME`, each VU
self-registers one ephemeral `lt-auto-*` user so smoke runs still work.

## Traffic mix (`SarvUser`)

~90% reads / ~10% writes:

* 30 chronological feed, 20 recommended feed (fans out to recommendation)
* 15 post detail + comments, 10 search, 10 profile + user posts
* 5 create post/comment/repost, 3 reaction, 2 follow

`404` on random ids and `409` on duplicate follow/reaction are treated
as benign. Non-200 recommendation responses (`500/503`, e.g. timeout)
are recorded as failures so saturation is visible in the UI.

Media upload and direct `recommendation:8000` calls are out of scope.

## Cleanup

Seeded users are prefixed `lt-`. To remove them after a run:

```sql
DELETE FROM users WHERE username LIKE 'lt-%';
```

Never run load tests against a production database.
