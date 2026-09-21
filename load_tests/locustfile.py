"""Locust entrypoint for Sarv load tests.

Read-heavy realistic mix (~90% reads / ~10% writes) against the Spring
backend. Each virtual user logs in once with a pre-seeded credential
(see seed.py) and then loops over feed, post, search, profile, and
occasional write tasks.

Run from this directory::

    uv run python seed.py --users 50
    uv run locust -f locustfile.py -H http://localhost:8080
"""

from __future__ import annotations

import itertools
import random
import threading
import time

from locust import HttpUser, between, task

from load_tests import config, data

_credentials = data.load_user_credentials(config.USERS_FILE)
_credential_cycle = itertools.cycle(_credentials) if _credentials else None
_credential_lock = threading.Lock()

_post_ids: list[int] = []
_user_ids: list[int] = []
_pool_lock = threading.Lock()


def _next_credential() -> tuple[str, str] | None:
    if _credential_cycle is None:
        return None
    with _credential_lock:
        return next(_credential_cycle)


def _remember_ids(post_ids: list[int], user_ids: list[int]) -> None:
    with _pool_lock:
        for pid in post_ids:
            if pid not in _post_ids:
                _post_ids.append(pid)
        for uid in user_ids:
            if uid not in _user_ids:
                _user_ids.append(uid)
        del _post_ids[:-2000]
        del _user_ids[:-2000]


def _pick(rng: random.Random, pool: list[int]) -> int | None:
    with _pool_lock:
        if not pool:
            return None
        return rng.choice(pool)


class SarvUser(HttpUser):
    wait_time = between(1, 3)

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.rng = random.Random()
        self.username: str | None = None
        self.user_id: int | None = None

    def on_start(self):
        credential = _next_credential()
        if credential is not None:
            self._login(*credential)
            return
        if config.SINGLE_USERNAME and config.SINGLE_PASSWORD:
            self._login(config.SINGLE_USERNAME, config.SINGLE_PASSWORD)
            return
        self._register_ephemeral()

    def _auth_headers(self, token: str) -> None:
        self.client.headers.update({"Authorization": f"Bearer {token}"})

    def _login(self, username: str, password: str) -> None:
        with self.client.post(
            "/api/auth/login",
            json={"username": username, "password": password},
            name="/api/auth/login",
            catch_response=True,
        ) as resp:
            if resp.status_code != 200:
                resp.failure(f"login failed: {resp.status_code}")
                return
            try:
                token = resp.json().get("token", "")
            except ValueError:
                resp.failure("login returned non-JSON body")
                return
            if not token:
                resp.failure("login returned empty token")
                return
            resp.success()
            self._auth_headers(token)
            self.username = username
        self._fetch_self()
        self._warm_pools()

    def _register_ephemeral(self) -> None:
        suffix = f"{int(time.time() * 1000) % 1_000_000}{self.rng.randint(100, 999)}"
        username = f"lt-auto-{suffix}"
        password = config.SEED_PASSWORD
        with self.client.post(
            "/api/auth/register",
            json={
                "username": username,
                "password": password,
                "confirmPassword": password,
                "email": f"{username}@test.local",
                "displayName": f"Load {suffix}",
                "gender": self.rng.choice(data.GENDERS),
            },
            name="/api/auth/register",
            catch_response=True,
        ) as resp:
            if resp.status_code != 200:
                resp.failure(f"ephemeral register failed: {resp.status_code}")
                return
            try:
                token = resp.json().get("token", "")
                self.user_id = resp.json().get("id")
            except ValueError:
                resp.failure("register returned non-JSON body")
                return
            if not token:
                resp.failure("register returned empty token")
                return
            resp.success()
            self._auth_headers(token)
            self.username = username
        self._warm_pools()

    def _fetch_self(self) -> None:
        with self.client.get(
            "/api/users/me", name="/api/users/me", catch_response=True
        ) as resp:
            if resp.status_code != 200:
                resp.failure(f"me failed: {resp.status_code}")
                return
            try:
                self.user_id = resp.json().get("id")
            except ValueError:
                resp.failure("me returned non-JSON body")

    def _warm_pools(self) -> None:
        with self.client.get(
            "/api/feed/chronological",
            params={"page": 0, "size": config.DEFAULT_PAGE_SIZE},
            name="/api/feed/chronological",
            catch_response=True,
        ) as resp:
            if resp.status_code != 200:
                resp.failure(f"warm feed failed: {resp.status_code}")
                return
            try:
                body = resp.json()
            except ValueError:
                resp.failure("warm feed returned non-JSON body")
                return
            resp.success()
            _remember_ids(data.extract_page_ids(body), data.extract_page_user_ids(body))

    @task(30)
    def chronological_feed(self):
        page = self.rng.randint(0, 3)
        with self.client.get(
            "/api/feed/chronological",
            params={"page": page, "size": config.DEFAULT_PAGE_SIZE},
            name="/api/feed/chronological",
            catch_response=True,
        ) as resp:
            if resp.status_code != 200:
                resp.failure(f"chronological failed: {resp.status_code}")
                return
            try:
                body = resp.json()
            except ValueError:
                resp.failure("chronological returned non-JSON body")
                return
            resp.success()
            _remember_ids(data.extract_page_ids(body), data.extract_page_user_ids(body))

    @task(20)
    def recommended_feed(self):
        page = self.rng.randint(0, 2)
        with self.client.get(
            "/api/feed/recommended",
            params={"page": page, "size": config.DEFAULT_PAGE_SIZE},
            name="/api/feed/recommended",
            catch_response=True,
        ) as resp:
            if resp.status_code not in (200, 500, 503):
                resp.failure(f"recommended failed: {resp.status_code}")
                return
            if resp.status_code != 200:
                resp.failure(f"recommendation unavailable: {resp.status_code}")
                return
            try:
                body = resp.json()
            except ValueError:
                resp.failure("recommended returned non-JSON body")
                return
            resp.success()
            _remember_ids(data.extract_page_ids(body), data.extract_page_user_ids(body))

    @task(15)
    def view_post_with_comments(self):
        post_id = _pick(self.rng, _post_ids)
        if post_id is None:
            self.chronological_feed()
            return
        with self.client.get(
            f"/api/posts/{post_id}",
            name="/api/posts/[id]",
            catch_response=True,
        ) as resp:
            if resp.status_code == 404:
                resp.success()
                return
            if resp.status_code != 200:
                resp.failure(f"get post failed: {resp.status_code}")
                return
            resp.success()
        with self.client.get(
            f"/api/posts/{post_id}/comments",
            params={"sortBy": "NEWEST", "page": 0, "size": 10},
            name="/api/posts/[id]/comments",
            catch_response=True,
        ) as resp:
            if resp.status_code == 404:
                resp.success()
                return
            if resp.status_code != 200:
                resp.failure(f"get comments failed: {resp.status_code}")

    @task(10)
    def search(self):
        term = data.random_search_term(self.rng)
        if self.rng.random() < 0.5:
            with self.client.get(
                "/api/posts/search",
                params={"query": term, "page": 0, "size": 10},
                name="/api/posts/search",
                catch_response=True,
            ) as resp:
                if resp.status_code != 200:
                    resp.failure(f"post search failed: {resp.status_code}")
                    return
                try:
                    _remember_ids(data.extract_page_ids(resp.json()), [])
                except ValueError:
                    resp.failure("post search returned non-JSON body")
        else:
            with self.client.get(
                "/api/users",
                params={"query": term, "page": 0, "size": 10},
                name="/api/users?query=",
                catch_response=True,
            ) as resp:
                if resp.status_code != 200:
                    resp.failure(f"user search failed: {resp.status_code}")
                    return
                try:
                    _remember_ids([], data.extract_page_ids(resp.json()))
                except ValueError:
                    resp.failure("user search returned non-JSON body")

    @task(10)
    def view_profile(self):
        target = _pick(self.rng, _user_ids) or self.user_id
        if target is None:
            return
        with self.client.get(
            f"/api/users/{target}",
            name="/api/users/[id]",
            catch_response=True,
        ) as resp:
            if resp.status_code == 404:
                resp.success()
            elif resp.status_code != 200:
                resp.failure(f"get user failed: {resp.status_code}")
        with self.client.get(
            f"/api/users/{target}/posts",
            params={"page": 0, "size": 10},
            name="/api/users/[id]/posts",
            catch_response=True,
        ) as resp:
            if resp.status_code == 404:
                resp.success()
            elif resp.status_code != 200:
                resp.failure(f"get user posts failed: {resp.status_code}")

    @task(5)
    def create_post_or_comment(self):
        roll = self.rng.random()
        if roll < 0.6:
            parent_id = _pick(self.rng, _post_ids)
            payload: dict = {
                "postCategory": "COMMENT" if parent_id else "NORMAL",
                "content": data.random_content(self.rng),
                "mediaId": None,
                "parentId": parent_id,
                "repostOfId": None,
            }
        elif roll < 0.8:
            payload = {
                "postCategory": "NORMAL",
                "content": data.random_content(self.rng),
                "mediaId": None,
                "parentId": None,
                "repostOfId": None,
            }
        else:
            repost_of = _pick(self.rng, _post_ids)
            if repost_of is None:
                return
            payload = {
                "postCategory": "REPOST",
                "content": None,
                "mediaId": None,
                "parentId": None,
                "repostOfId": repost_of,
            }
        with self.client.post(
            "/api/posts", json=payload, name="POST /api/posts", catch_response=True
        ) as resp:
            if resp.status_code == 404:
                resp.failure("create post hit missing parent/repost target")
                return
            if resp.status_code not in (200, 201):
                resp.failure(f"create post failed: {resp.status_code}")
                return
            try:
                new_id = resp.json().get("id")
            except ValueError:
                resp.failure("create post returned non-JSON body")
                return
            resp.success()
            if isinstance(new_id, int):
                _remember_ids([new_id], [])

    @task(3)
    def react_to_post(self):
        post_id = _pick(self.rng, _post_ids)
        if post_id is None:
            return
        reaction = self.rng.choice([1, -1])
        with self.client.post(
            f"/api/posts/{post_id}/reactions",
            json={"reactionType": reaction},
            name="/api/posts/[id]/reactions",
            catch_response=True,
        ) as resp:
            if resp.status_code == 404:
                resp.success()
            elif resp.status_code not in (200, 201):
                resp.failure(f"react failed: {resp.status_code}")

    @task(2)
    def follow_user(self):
        target = _pick(self.rng, _user_ids)
        if target is None or target == self.user_id:
            return
        with self.client.post(
            f"/api/users/{target}/followers",
            name="/api/users/[id]/followers",
            catch_response=True,
        ) as resp:
            if resp.status_code in (200, 201, 409):
                resp.success()
            elif resp.status_code == 404:
                resp.success()
            else:
                resp.failure(f"follow failed: {resp.status_code}")
