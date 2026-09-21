import json
import logging
from typing import Any

import redis.asyncio as redis
from pydantic_settings import BaseSettings

log = logging.getLogger(__name__)


class CacheSettings(BaseSettings):
    redis_url: str = "redis://localhost:6379"
    feed_cache_ttl_seconds: int = 45

    class Config:
        env_file = ".env"


_client: redis.Redis | None = None
_ttl = 45


def cache_key(user_id: str, page: int, size: int) -> str:
    return f"feed:v0:user:{user_id}:page:{page}:size:{size}"


async def open_cache(settings: CacheSettings | None = None) -> None:
    """Creates the shared Redis client. Never raises: on failure caching is bypassed."""
    global _client, _ttl
    settings = settings or CacheSettings()
    _ttl = settings.feed_cache_ttl_seconds
    try:
        _client = redis.from_url(
            settings.redis_url,
            socket_connect_timeout=2,
            socket_timeout=2,
            decode_responses=True,
        )
        await _client.ping()
        log.info("Redis cache enabled (ttl=%ds)", _ttl)
    except Exception:
        log.warning("Redis unavailable at startup, feed caching bypassed", exc_info=True)
        _client = None


async def close_cache() -> None:
    global _client
    if _client is not None:
        try:
            await _client.aclose()
        except Exception:
            log.warning("Error closing Redis client", exc_info=True)
        _client = None


async def get_cached_page(key: str) -> dict[str, Any] | None:
    if _client is None:
        return None
    try:
        raw = await _client.get(key)
    except Exception:
        log.warning("Redis GET failed, bypassing cache", exc_info=True)
        return None
    if raw is None:
        return None
    try:
        return json.loads(raw)
    except (TypeError, ValueError):
        log.warning("Redis cached value is not valid JSON, ignoring")
        return None


async def set_cached_page(key: str, payload: dict[str, Any]) -> None:
    if _client is None:
        return
    try:
        await _client.setex(key, _ttl, json.dumps(payload))
    except Exception:
        log.warning("Redis SETEX failed, serving uncached response", exc_info=True)
