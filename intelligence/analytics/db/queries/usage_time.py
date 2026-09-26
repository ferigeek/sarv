import re
from datetime import datetime, timedelta

_INTERVAL_RE = re.compile(r"^(\d+)([mhdw])$")

_PG_UNITS = {
    "m": "minutes",
    "h": "hours",
    "d": "days",
    "w": "weeks",
}

_PY_UNITS = {
    "m": "minutes",
    "h": "hours",
    "d": "days",
    "w": "weeks",
}

# Upper bound on buckets per request so a tiny interval over a
# huge range cannot blow up the response or the database.
MAX_BUCKETS = 1000


def parse_interval(raw: str) -> tuple[timedelta, str]:
    """Parse a duration string like ``15m``, ``1h``, ``1d``, ``1w``.

    Returns the step as a ``timedelta`` (for gap-filling in Python)
    plus a whitelisted Postgres interval literal for the SQL query.
    Never interpolate raw user input into SQL.
    """
    match = _INTERVAL_RE.match(raw.strip())
    if not match:
        raise ValueError(
            f"Invalid interval {raw!r}: expected e.g. '15m', '1h', '1d', '1w' "
            "(lowercase unit)."
        )
    amount, unit = int(match.group(1)), match.group(2)
    if amount < 1:
        raise ValueError(f"Invalid interval {raw!r}: amount must be >= 1.")
    step = timedelta(**{_PY_UNITS[unit]: amount})
    return step, f"{amount} {_PG_UNITS[unit]}"


def build_bucket_starts(
    start_time: datetime, end_time: datetime, step: timedelta
) -> list[datetime]:
    """Bucket boundaries in ``[start_time, end_time)`` stepping by ``step``."""
    buckets = []
    current = start_time
    while current < end_time:
        buckets.append(current)
        current += step
    return buckets


def gap_fill(
    bucket_starts: list[datetime], rows: list[tuple[datetime, int]]
) -> list[dict]:
    """Merge sparse DB rows onto the full bucket grid, filling gaps with 0."""
    counts = {period: count for period, count in rows}
    return [
        {"period_start": bucket, "active_users": counts.get(bucket, 0)}
        for bucket in bucket_starts
    ]


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

    if start_time >= end_time:
        raise ValueError("start_time must be before end_time.")

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
