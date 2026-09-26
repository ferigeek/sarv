from datetime import datetime

from analytics.db.queries._common import validate_range

DEFAULT_LIMIT = 10
MAX_LIMIT = 100


def validate_limit(limit: int) -> None:
    if not 1 <= limit <= MAX_LIMIT:
        raise ValueError(f"Invalid limit {limit!r}: expected 1..{MAX_LIMIT}.")


async def most_active_users(
    start_time: datetime, end_time: datetime, limit: int = DEFAULT_LIMIT
) -> list[dict]:
    """Users ranked by event count in ``[start_time, end_time)``.

    Every logged event counts as activity (impression and dwell rows
    alike). The join drops NULL actors, i.e. events of deleted users.
    """
    from analytics.db.pool import pool

    validate_range(start_time, end_time)
    validate_limit(limit)

    async with pool.connection() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                """
                SELECT u.id, u.username, u.display_name, COUNT(*) AS event_count
                FROM event_logs e
                JOIN users u ON u.id = e.user_id
                WHERE e.created_at >= %s::timestamptz
                AND e.created_at < %s::timestamptz
                GROUP BY u.id, u.username, u.display_name
                ORDER BY event_count DESC, u.id ASC
                LIMIT %s;
                """,
                (start_time, end_time, limit),
            )
            rows = await cur.fetchall()
    return [
        {
            "user_id": user_id,
            "username": username,
            "display_name": display_name,
            "event_count": event_count,
        }
        for user_id, username, display_name, event_count in rows
    ]
