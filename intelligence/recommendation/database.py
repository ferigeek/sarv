import logging
from contextlib import asynccontextmanager
from typing import AsyncIterator

from psycopg import AsyncConnection
from psycopg_pool import AsyncConnectionPool
from pydantic_settings import BaseSettings

log = logging.getLogger(__name__)


class DatabaseSettings(BaseSettings):
    name: str
    username: str
    password: str
    host: str = "localhost"
    port: int = 5432
    pool_min: int = 2
    pool_max: int = 10
    pool_timeout: float = 5.0

    class Config:
        env_prefix = "DB_"
        env_file = ".env"

    def conninfo(self) -> str:
        return (
            f"dbname={self.name} user={self.username} "
            f"password={self.password} host={self.host} port={self.port}"
        )


_pool: AsyncConnectionPool | None = None


async def open_pool(settings: DatabaseSettings | None = None) -> AsyncConnectionPool:
    """Opens the shared async pool (idempotent). Called from app lifespan."""
    global _pool
    if _pool is not None:
        return _pool
    settings = settings or DatabaseSettings()
    _pool = AsyncConnectionPool(
        settings.conninfo(),
        min_size=settings.pool_min,
        max_size=settings.pool_max,
        timeout=settings.pool_timeout,
        open=False,
    )
    await _pool.open()
    log.info(
        "Database pool opened (min=%d max=%d host=%s)",
        settings.pool_min,
        settings.pool_max,
        settings.host,
    )
    return _pool


async def close_pool() -> None:
    """Closes the shared pool. Called from app lifespan shutdown."""
    global _pool
    if _pool is not None:
        await _pool.close()
        _pool = None


@asynccontextmanager
async def get_connection() -> AsyncIterator[AsyncConnection]:
    if _pool is None:
        raise RuntimeError("Database pool is not open")
    async with _pool.connection() as conn:
        yield conn
