from datetime import datetime

from analytics.db.queries._common import resolve_buckets, validate_range

# Dwell sources written by the backend (EventLogService); rows without a
# source key report as UNKNOWN rather than being dropped.
KNOWN_SOURCES = ("FEED", "DETAIL")
UNKNOWN_SOURCE = "UNKNOWN"
ALL_SOURCES = (*KNOWN_SOURCES, UNKNOWN_SOURCE)

# Backend validates durationMs into this range; clamp again here so
# corrupt rows cannot skew the average.
MIN_DURATION_MS = 1
MAX_DURATION_MS = 1800000

_DWELL_VALUE = "(metadata->>'duration_ms')::bigint"


def _validate_source(source: str | None) -> None:
    if source is not None and source not in KNOWN_SOURCES:
        raise ValueError(f"Invalid source {source!r}: expected 'FEED' or 'DETAIL'.")


def _stats(total_ms: int, samples: int) -> dict:
    return {
        "average_duration_ms": (total_ms / samples) if samples else None,
        "samples": samples,
    }


def _zeroed_split() -> dict[str, dict]:
    return {source: _stats(0, 0) for source in ALL_SOURCES}


def _dwell_where(source: str | None) -> tuple[str, list]:
    """Base dwell predicate plus query params (range first, source last)."""
    clause = f"""type = 'VIEW_POST'
        AND metadata ? 'duration_ms'
        AND (metadata->>'duration_ms') ~ '^[0-9]+$'
        AND {_DWELL_VALUE} BETWEEN {MIN_DURATION_MS} AND {MAX_DURATION_MS}
        AND created_at >= %s::timestamptz
        AND created_at < %s::timestamptz"""
    params: list = []
    if source is not None:
        clause += " AND metadata->>'source' = %s"
        params.append(source)
    return clause, params


async def viewing_time_summary(
    start_time: datetime, end_time: datetime, source: str | None = None
) -> dict:
    """Overall average viewing time plus per-source split for ``[start, end)``."""
    from analytics.db.pool import pool

    validate_range(start_time, end_time)
    _validate_source(source)

    where, extra = _dwell_where(source)
    async with pool.connection() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                f"""
                SELECT COUNT(*), COALESCE(SUM({_DWELL_VALUE}), 0)
                FROM event_logs
                WHERE {where};
                """,
                (start_time, end_time, *extra),
            )
            samples, total_ms = await cur.fetchone()

            await cur.execute(
                f"""
                SELECT COALESCE(metadata->>'source', '{UNKNOWN_SOURCE}'),
                    COUNT(*), SUM({_DWELL_VALUE})
                FROM event_logs
                WHERE {where}
                GROUP BY 1
                ORDER BY 1;
                """,
                (start_time, end_time, *extra),
            )
            rows = await cur.fetchall()

    by_source = _zeroed_split()
    for src, count, total in rows:
        if src in by_source:
            by_source[src] = _stats(total, count)
    return {"overall": _stats(total_ms, samples), "by_source": by_source}


async def viewing_time_over_time(
    start_time: datetime,
    end_time: datetime,
    interval: str,
    source: str | None = None,
) -> list[dict]:
    """Per-bucket viewing time in ``[start, end)``, buckets aligned to start.

    Overall bucket averages are weighted from per-source sums, never
    averaged from averages.
    """
    from analytics.db.pool import pool

    _, pg_interval, bucket_starts = resolve_buckets(start_time, end_time, interval)
    _validate_source(source)

    where, extra = _dwell_where(source)
    async with pool.connection() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                f"""
                SELECT
                    date_bin(%s::interval, created_at, %s::timestamptz) AS period,
                    COALESCE(metadata->>'source', '{UNKNOWN_SOURCE}') AS src,
                    COUNT(*), SUM({_DWELL_VALUE})
                FROM event_logs
                WHERE {where}
                GROUP BY period, src
                ORDER BY period, src;
                """,
                (pg_interval, start_time, start_time, end_time, *extra),
            )
            rows = await cur.fetchall()

    grid: dict[datetime, dict[str, list]] = {
        bucket: {s: [0, 0] for s in ALL_SOURCES} for bucket in bucket_starts
    }
    for period, src, count, total in rows:
        if period in grid and src in grid[period]:
            grid[period][src] = [total, count]

    buckets = []
    for bucket in bucket_starts:
        split = {s: _stats(*grid[bucket][s]) for s in ALL_SOURCES}
        total_ms = sum(grid[bucket][s][0] for s in ALL_SOURCES)
        samples = sum(grid[bucket][s][1] for s in ALL_SOURCES)
        buckets.append(
            {
                "period_start": bucket,
                "average_duration_ms": (total_ms / samples) if samples else None,
                "samples": samples,
                "by_source": split,
            }
        )
    return buckets
