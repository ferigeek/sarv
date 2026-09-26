from datetime import datetime

from analytics.db.queries.usage_time import (
    MAX_BUCKETS,
    build_bucket_starts,
    parse_interval,
)

# Mirrors the event_type enum: V1 initial values plus REGISTER and
# QUOTE_POST from V7. Keep in sync when the backend adds new types.
KNOWN_EVENT_TYPES = [
    "VIEW_POST",
    "LIKE_POST",
    "DISLIKE_POST",
    "CREATE_COMMENT",
    "REPOST_POST",
    "FOLLOW_USER",
    "UNFOLLOW_USER",
    "VIEW_PROFILE",
    "CREATE_POST",
    "REQUEST_FEED",
    "LOGIN",
    "REGISTER",
    "QUOTE_POST",
]


def zeroed_totals() -> dict[str, int]:
    return {event_type: 0 for event_type in KNOWN_EVENT_TYPES}


def _check_range(start_time: datetime, end_time: datetime) -> None:
    if start_time >= end_time:
        raise ValueError("start_time must be before end_time.")


async def event_totals(start_time: datetime, end_time: datetime) -> list[dict]:
    """Total event count per type in ``[start_time, end_time)``.

    Types with no events are included with count 0.
    """
    from analytics.db.pool import pool

    _check_range(start_time, end_time)

    async with pool.connection() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                """
                SELECT type, COUNT(*)
                FROM event_logs
                WHERE created_at >= %s::timestamptz
                AND created_at < %s::timestamptz
                GROUP BY type
                ORDER BY type;
                """,
                (start_time, end_time),
            )
            rows = await cur.fetchall()

    counts = zeroed_totals()
    for event_type, count in rows:
        if event_type in counts:
            counts[event_type] = count
    return [{"event_type": t, "count": counts[t]} for t in KNOWN_EVENT_TYPES]


async def event_breakdown_over_time(
    start_time: datetime, end_time: datetime, interval: str
) -> list[dict]:
    """Per-bucket per-type event counts in ``[start, end)``.

    Buckets are aligned to ``start_time``; every bucket carries all
    known types, zero-filled.
    """
    from analytics.db.pool import pool

    _check_range(start_time, end_time)

    step, pg_interval = parse_interval(interval)

    bucket_starts = build_bucket_starts(start_time, end_time, step)
    if len(bucket_starts) > MAX_BUCKETS:
        raise ValueError(
            f"Too many buckets ({len(bucket_starts)}): "
            f"choose a larger interval or a shorter range "
            f"(max {MAX_BUCKETS})."
        )

    async with pool.connection() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                """
                SELECT
                    date_bin(%s::interval, created_at, %s::timestamptz) AS period,
                    type,
                    COUNT(*)
                FROM event_logs
                WHERE created_at >= %s::timestamptz
                AND created_at < %s::timestamptz
                GROUP BY period, type
                ORDER BY period, type;
                """,
                (pg_interval, start_time, start_time, end_time),
            )
            rows = await cur.fetchall()

    grid: dict[datetime, dict[str, int]] = {
        bucket: zeroed_totals() for bucket in bucket_starts
    }
    for period, event_type, count in rows:
        if period in grid and event_type in grid[period]:
            grid[period][event_type] = count
    return [
        {"period_start": bucket, "counts": grid[bucket]} for bucket in bucket_starts
    ]
