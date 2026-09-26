from datetime import datetime
from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel
from analytics.db.pool import pool
from analytics.db.queries.usage_time import (
    active_user_count as hourly_active_users,
    usage_activity_over_time,
)
from analytics.db.queries.event_breakdown import (
    event_breakdown_over_time,
    event_totals,
)
from analytics.db.queries.engagement import (
    engagement_over_time,
    engagement_totals,
)

@asynccontextmanager
async def lifespan(app: FastAPI):
    await pool.open()
    yield
    await pool.close()


app = FastAPI(lifespan=lifespan)


class ActivityBucket(BaseModel):
    period_start: datetime
    active_users: int


class ActivityResponse(BaseModel):
    start_time: datetime
    end_time: datetime
    interval: str
    buckets: list[ActivityBucket]


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


class EngagementTotals(BaseModel):
    total_users: int
    active_users: int
    new_users: int
    returning_users: int


class EngagementBucket(EngagementTotals):
    period_start: datetime


class EngagementResponse(BaseModel):
    start_time: datetime
    end_time: datetime
    interval: str | None = None
    totals: EngagementTotals
    buckets: list[EngagementBucket] | None = None


@app.get("/users/active", response_model=list[ActivityBucket])
async def get_active_user_count(start_time: datetime, end_time: datetime):
    return await hourly_active_users(start_time, end_time)


@app.get("/usage/activity", response_model=ActivityResponse)
async def get_usage_activity(
    start_time: datetime,
    end_time: datetime,
    interval: str = Query(..., description="Bucket size, e.g. '15m', '1h', '1d', '1w'"),
):
    try:
        buckets = await usage_activity_over_time(start_time, end_time, interval)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc))
    return {
        "start_time": start_time,
        "end_time": end_time,
        "interval": interval,
        "buckets": buckets,
    }


@app.get("/events/breakdown", response_model=BreakdownResponse)
async def get_event_breakdown(
    start_time: datetime,
    end_time: datetime,
    interval: str | None = Query(
        None, description="Optional bucket size, e.g. '15m', '1h', '1d', '1w'"
    ),
):
    try:
        totals = await event_totals(start_time, end_time)
        buckets = (
            await event_breakdown_over_time(start_time, end_time, interval)
            if interval is not None
            else None
        )
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc))
    return {
        "start_time": start_time,
        "end_time": end_time,
        "interval": interval,
        "totals": totals,
        "buckets": buckets,
    }


@app.get("/users/engagement", response_model=EngagementResponse)
async def get_user_engagement(
    start_time: datetime,
    end_time: datetime,
    interval: str | None = Query(
        None, description="Optional bucket size, e.g. '15m', '1h', '1d', '1w'"
    ),
):
    """Total, active, new and returning users.

    New users signed up in a bucket count even if inactive there, so
    per bucket new + returning does not necessarily equal active.
    """
    try:
        totals = await engagement_totals(start_time, end_time)
        buckets = (
            await engagement_over_time(start_time, end_time, interval)
            if interval is not None
            else None
        )
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc))
    return {
        "start_time": start_time,
        "end_time": end_time,
        "interval": interval,
        "totals": totals,
        "buckets": buckets,
    }
