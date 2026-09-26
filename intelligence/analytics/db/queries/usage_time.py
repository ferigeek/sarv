from datetime import datetime

from analytics.db.queries._common import gap_fill, resolve_buckets


async def active_user_count(start_time: datetime, end_time: datetime) -> list[dict]:
    from analytics.db.pool import pool

    async with pool.connection() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                """
                SELECT
                    date_trunc('hour', created_at) AS period,
                    COUNT(DISTINCT user_id) AS active_users
                FROM event_logs
                WHERE created_at >= %s
                AND created_at < %s
                AND user_id IS NOT NULL
                GROUP BY period
                ORDER BY period;
                """,
                (start_time, end_time),
            )
            rows = await cur.fetchall()
    return [{"period_start": period, "active_users": count} for period, count in rows]


async def usage_activity_over_time(
    start_time: datetime, end_time: datetime, interval: str
) -> list[dict]:
    """Distinct active users per ``interval`` bucket in ``[start, end)``.

    Buckets are aligned to ``start_time``; empty buckets are zero-filled.
    """
    from analytics.db.pool import pool

    _, pg_interval, bucket_starts = resolve_buckets(start_time, end_time, interval)

    async with pool.connection() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                """
                SELECT
                    date_bin(%s::interval, created_at, %s::timestamptz) AS period,
                    COUNT(DISTINCT user_id) AS active_users
                FROM event_logs
                WHERE created_at >= %s::timestamptz
                AND created_at < %s::timestamptz
                AND user_id IS NOT NULL
                GROUP BY period
                ORDER BY period;
                """,
                (pg_interval, start_time, start_time, end_time),
            )
            rows = await cur.fetchall()
    return gap_fill(bucket_starts, rows)
