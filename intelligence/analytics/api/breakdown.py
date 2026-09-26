from datetime import datetime

from fastapi import APIRouter, Query
from pydantic import BaseModel

from analytics.api._common import or_422
from analytics.db.queries.event_breakdown import (
    event_breakdown_over_time,
    event_totals,
)

router = APIRouter()


class EventTypeCount(BaseModel):
    event_type: str
    count: int


class BreakdownBucket(BaseModel):
    period_start: datetime
    counts: dict[str, int]


class BreakdownResponse(BaseModel):
    start_time: datetime
    end_time: datetime
    interval: str | None = None
    totals: list[EventTypeCount]
    buckets: list[BreakdownBucket] | None = None


@router.get("/events/breakdown", response_model=BreakdownResponse)
async def get_event_breakdown(
    start_time: datetime,
    end_time: datetime,
    interval: str | None = Query(
        None, description="Optional bucket size, e.g. '15m', '1h', '1d', '1w'"
    ),
):
    totals = await or_422(event_totals(start_time, end_time))
    buckets = (
        await or_422(event_breakdown_over_time(start_time, end_time, interval))
        if interval is not None
        else None
    )
    return {
        "start_time": start_time,
        "end_time": end_time,
        "interval": interval,
        "totals": totals,
        "buckets": buckets,
    }
