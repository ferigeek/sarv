from datetime import datetime

from fastapi import APIRouter, Query
from pydantic import BaseModel

from analytics.api._common import or_422
from analytics.db.queries.viewing_time import (
    viewing_time_over_time,
    viewing_time_summary,
)

router = APIRouter()


class ViewingStats(BaseModel):
    average_duration_ms: float | None
    samples: int


class ViewingBucket(BaseModel):
    period_start: datetime
    average_duration_ms: float | None
    samples: int
    by_source: dict[str, ViewingStats]


class ViewingTimeResponse(BaseModel):
    start_time: datetime
    end_time: datetime
    interval: str | None = None
    overall: ViewingStats
    by_source: dict[str, ViewingStats]
    buckets: list[ViewingBucket] | None = None


@router.get("/engagement/viewing-time", response_model=ViewingTimeResponse)
async def get_viewing_time(
    start_time: datetime,
    end_time: datetime,
    interval: str | None = Query(
        None, description="Optional bucket size, e.g. '15m', '1h', '1d', '1w'"
    ),
    source: str | None = Query(
        None, description="Optional dwell source filter: FEED or DETAIL"
    ),
):
    summary = await or_422(viewing_time_summary(start_time, end_time, source))
    buckets = (
        await or_422(viewing_time_over_time(start_time, end_time, interval, source))
        if interval is not None
        else None
    )
    return {
        "start_time": start_time,
        "end_time": end_time,
        "interval": interval,
        **summary,
        "buckets": buckets,
    }
