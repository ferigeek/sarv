"""Seed pre-test users (and a small post pool) for Locust runs.

Idempotent: existing usernames fall back to login instead of failing.

Usage::

    uv run python seed.py --users 50
    uv run python seed.py --users 200 --prefix lt- --base-url http://localhost:8080
"""

from __future__ import annotations

import argparse
import csv
import json
import random
import sys
import urllib.error
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "src"))

from load_tests import config, data  # noqa: E402


def _request(
    method: str, url: str, payload: dict | None = None, token: str | None = None
) -> tuple[int, dict | list]:
    body = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=body, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=config.REQUEST_TIMEOUT) as resp:
            raw = resp.read().decode() or "{}"
            return resp.status, json.loads(raw)
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode() or "{}")
        except ValueError:
            return e.code, {}
    except OSError as e:
        raise SystemExit(f"Cannot reach {url}: {e}")


def register_or_login(base_url: str, username: str, password: str, gender: str) -> str | None:
    status, body = _request(
        "POST",
        f"{base_url}/api/auth/register",
        {
            "username": username,
            "password": password,
            "confirmPassword": password,
            "email": f"{username}@test.local",
            "displayName": f"Load Test {username}",
            "gender": gender,
        },
    )
    if status == 200 and isinstance(body, dict) and body.get("token"):
        print(f"registered {username}")
        return str(body["token"])
    status, body = _request(
        "POST", f"{base_url}/api/auth/login", {"username": username, "password": password}
    )
    if status == 200 and isinstance(body, dict) and body.get("token"):
        print(f"reused {username}")
        return str(body["token"])
    print(f"FAILED {username}: register/login -> {status} {body}")
    return None


def seed_posts(base_url: str, tokens: list[str], rng: random.Random, per_user: int = 3) -> int:
    created = 0
    for token in tokens[: min(len(tokens), 10)]:
        for _ in range(per_user):
            status, _ = _request(
                "POST",
                f"{base_url}/api/posts",
                {
                    "postCategory": "NORMAL",
                    "content": data.random_content(rng),
                    "mediaId": None,
                    "parentId": None,
                    "repostOfId": None,
                },
                token=token,
            )
            if status in (200, 201):
                created += 1
    return created


def main() -> None:
    parser = argparse.ArgumentParser(description="Seed Sarv load-test users.")
    parser.add_argument("--users", type=int, default=50)
    parser.add_argument("--prefix", default="lt-")
    parser.add_argument("--password", default=config.SEED_PASSWORD)
    parser.add_argument("--base-url", default=config.BASE_URL)
    parser.add_argument("--output", default=str(config.USERS_FILE))
    parser.add_argument("--seed-posts", dest="seed_posts", action="store_true", default=True)
    parser.add_argument("--no-seed-posts", dest="seed_posts", action="store_false")
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")
    rng = random.Random(42)
    rows: list[tuple[str, str]] = []
    tokens: list[str] = []

    for i in range(args.users):
        username = f"{args.prefix}{i:04d}"
        token = register_or_login(base_url, username, args.password, data.GENDERS[i % len(data.GENDERS)])
        if token:
            rows.append((username, args.password))
            tokens.append(token)

    if args.seed_posts and tokens:
        created = seed_posts(base_url, tokens, rng)
        print(f"seeded {created} posts")

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["username", "password"])
        writer.writerows(rows)
    print(f"wrote {len(rows)}/{args.users} credentials to {output}")


if __name__ == "__main__":
    main()
