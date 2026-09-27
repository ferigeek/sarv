"""Admin authentication core: env credentials, HMAC-signed session tokens.

Pure stdlib so the dashboard needs no extra dependencies. Tokens are
``payload.signature`` strings (base64url, no padding) kept in an HttpOnly
cookie; there is intentionally no register flow — the single admin account
is configured through ``ANALYTICS_USER`` / ``ANALYTICS_PASSWORD``.
"""

import base64
import hashlib
import hmac
import json
import secrets
import time

COOKIE_NAME = "sarv_admin"

MAX_FAILS = 5
LOCKOUT_SECONDS = 60


def _b64encode(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")


def _b64decode(data: str) -> bytes:
    return base64.urlsafe_b64decode(data + "=" * (-len(data) % 4))


def create_token(username: str, secret: str, ttl_seconds: int, now: float | None = None) -> str:
    issued = int(now if now is not None else time.time())
    payload = _b64encode(
        json.dumps({"sub": username, "exp": issued + ttl_seconds}, separators=(",", ":")).encode()
    )
    sig = _b64encode(hmac.new(secret.encode(), payload.encode(), hashlib.sha256).digest())
    return f"{payload}.{sig}"


def verify_token(token: str, secret: str, now: float | None = None) -> str | None:
    """Return the username for a valid, unexpired token, else ``None``."""
    try:
        payload, sig = token.split(".", 1)
        expected = _b64encode(hmac.new(secret.encode(), payload.encode(), hashlib.sha256).digest())
        if not hmac.compare_digest(sig, expected):
            return None
        claims = json.loads(_b64decode(payload))
        if not isinstance(claims.get("sub"), str):
            return None
        if int(claims.get("exp", 0)) <= int(now if now is not None else time.time()):
            return None
        return claims["sub"]
    except (ValueError, KeyError, TypeError, json.JSONDecodeError):
        return None


def check_credentials(username: str, password: str, expected_user: str, expected_password: str) -> bool:
    return secrets.compare_digest(username, expected_user) and secrets.compare_digest(
        password, expected_password
    )


class LoginThrottle:
    """In-memory per-IP failure counter with a short lockout."""

    def __init__(self, max_fails: int = MAX_FAILS, lockout_seconds: int = LOCKOUT_SECONDS):
        self.max_fails = max_fails
        self.lockout_seconds = lockout_seconds
        self._fails: dict[str, list[float]] = {}

    def is_locked(self, ip: str, now: float | None = None) -> bool:
        moment = now if now is not None else time.time()
        recent = [t for t in self._fails.get(ip, []) if moment - t < self.lockout_seconds]
        self._fails[ip] = recent
        return len(recent) >= self.max_fails

    def register_fail(self, ip: str, now: float | None = None) -> None:
        moment = now if now is not None else time.time()
        self._fails.setdefault(ip, []).append(moment)

    def reset(self, ip: str) -> None:
        self._fails.pop(ip, None)


throttle = LoginThrottle()
