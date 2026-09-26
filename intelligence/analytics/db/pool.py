from psycopg_pool import AsyncConnectionPool
from analytics.config import settings


pool = AsyncConnectionPool(
    conninfo=settings.db_url,
    min_size=2,
    max_size=10,
    open=False
)