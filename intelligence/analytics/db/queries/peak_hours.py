from datetime import datetime

TIMEZONE = "UTC"


def _check_range(start_time: datetime, end_time: datetime) -> None:
    if start_time >= end_time:
        raise ValueError("start_time must be before end_time.")


def zeroed_buckets() -> list[dict]:
    return [
        {"hour": hour, "event_count": 0, "active_users": 0} for hour in range(24)
    ]


async def peak_activity_hours(start_time: datetime, end_time: datetime) -> dict:
    """Event and active-user counts per hour-of-day (UTC) in ``[start, end)``.

    Returns all 24 hours zero-filled plus the peak hour (most events,
    ties broken toward the earlier hour).
    """
    from analytics.db.pool import pool

    _check_range(start_time, end_time)

    async with pool.connection() as conn:
        async with conn.cursor() as cur:
            # AT TIME ZONE 'UTC' pins the bucketing: plain EXTRACT(HOUR ...)
            # would silently follow the session timezone instead.
            await cur.execute(
                """
                SELECT
                    EXTRACT(HOUR FROM created_at AT TIME ZONE 'UTC')::int AS hour,
                    COUNT(*) AS event_count,
                    COUNT(DISTINCT user_id) AS active_users
                FROM event_logs
                WHERE created_at >= %s::timestamptz
                AND created_at < %s::timestamptz
                GROUP BY hour
                ORDER BY hour;
                """,
                (start_time, end_time),
            )
            rows = await cur.fetchall()

    buckets = zeroed_buckets()
    for hour, event_count, active_users in rows:
        buckets[hour] = {
            "hour": hour,
            "event_count": event_count,
            "active_users": active_users,
        }
    peak_hour = max(buckets, key=lambda b: (b["event_count"], -b["hour"]))
    return {"timezone": TIMEZONE, "peak_hour": peak_hour, "buckets": buckets}
