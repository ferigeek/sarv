from datetime import datetime

from analytics.db.queries._common import resolve_buckets, validate_range


async def engagement_totals(start_time: datetime, end_time: datetime) -> dict:
    """Total, active, new and returning users for ``[start_time, end_time)``.

    New users come from ``users.created_at`` (event logging is
    best-effort, so ``REGISTER`` events can undercount signups).
    Only ``status='ACTIVE'`` users count toward total/new/returning;
    active users are distinct event actors regardless of status.
    """
    from analytics.db.pool import pool

    validate_range(start_time, end_time)

    async with pool.connection() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                """
                SELECT
                    (SELECT COUNT(*)
                     FROM users
                     WHERE created_at < %s::timestamptz
                     AND status = 'ACTIVE') AS total_users,
                    (SELECT COUNT(DISTINCT user_id)
                     FROM event_logs
                     WHERE created_at >= %s::timestamptz
                     AND created_at < %s::timestamptz
                     AND user_id IS NOT NULL) AS active_users,
                    (SELECT COUNT(*)
                     FROM users
                     WHERE created_at >= %s::timestamptz
                     AND created_at < %s::timestamptz
                     AND status = 'ACTIVE') AS new_users,
                    (SELECT COUNT(DISTINCT e.user_id)
                     FROM event_logs e
                     JOIN users u ON u.id = e.user_id
                     WHERE e.created_at >= %s::timestamptz
                     AND e.created_at < %s::timestamptz
                     AND u.created_at < %s::timestamptz
                     AND u.status = 'ACTIVE') AS returning_users;
                """,
                (
                    end_time,
                    start_time,
                    end_time,
                    start_time,
                    end_time,
                    start_time,
                    end_time,
                    start_time,
                ),
            )
            row = await cur.fetchone()
    total_users, active_users, new_users, returning_users = row
    return {
        "total_users": total_users,
        "active_users": active_users,
        "new_users": new_users,
        "returning_users": returning_users,
    }


async def engagement_over_time(
    start_time: datetime, end_time: datetime, interval: str
) -> list[dict]:
    """Per-bucket engagement in ``[start, end)``, buckets aligned to start.

    Per bucket: ``new`` counts signups in that bucket (even if inactive),
    ``returning`` counts active actors created before that bucket's start,
    ``total`` is the cumulative ACTIVE snapshot as of the bucket end.
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
            active_rows = await cur.fetchall()

            await cur.execute(
                """
                SELECT
                    date_bin(%s::interval, created_at, %s::timestamptz) AS period,
                    COUNT(*) AS new_users
                FROM users
                WHERE created_at >= %s::timestamptz
                AND created_at < %s::timestamptz
                AND status = 'ACTIVE'
                GROUP BY period
                ORDER BY period;
                """,
                (pg_interval, start_time, start_time, end_time),
            )
            new_rows = await cur.fetchall()

            await cur.execute(
                """
                SELECT
                    date_bin(%s::interval, e.created_at, %s::timestamptz) AS period,
                    COUNT(DISTINCT e.user_id) AS returning_users
                FROM event_logs e
                JOIN users u ON u.id = e.user_id
                WHERE e.created_at >= %s::timestamptz
                AND e.created_at < %s::timestamptz
                AND u.created_at < date_bin(%s::interval, e.created_at, %s::timestamptz)
                AND u.status = 'ACTIVE'
                GROUP BY period
                ORDER BY period;
                """,
                (
                    pg_interval,
                    start_time,
                    start_time,
                    end_time,
                    pg_interval,
                    start_time,
                ),
            )
            returning_rows = await cur.fetchall()

            await cur.execute(
                """
                SELECT COUNT(*)
                FROM users
                WHERE created_at < %s::timestamptz
                AND status = 'ACTIVE';
                """,
                (start_time,),
            )
            base_total = (await cur.fetchone())[0]

    active = {period: count for period, count in active_rows}
    new = {period: count for period, count in new_rows}
    returning = {period: count for period, count in returning_rows}

    buckets = []
    running_total = base_total
    for bucket in bucket_starts:
        running_total += new.get(bucket, 0)
        buckets.append(
            {
                "period_start": bucket,
                "total_users": running_total,
                "active_users": active.get(bucket, 0),
                "new_users": new.get(bucket, 0),
                "returning_users": returning.get(bucket, 0),
            }
        )
    return buckets
