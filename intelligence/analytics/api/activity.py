from datetime import datetime

from fastapi import APIRouter, Query
from pydantic import BaseModel

from analytics.api._common import or_422
from analytics.db.queries.usage_time import (
    active_user_count as hourly_active_users,
    usage_activity_over_time,
)

router = APIRouter()


class ActivityBucket(BaseModel):
    period_start: datetime
    active_users: int


class ActivityResponse(BaseModel):
    start_time: datetime
    end_time: datetime
    interval: str
    buckets: list[ActivityBucket]


@router.get("/users/active", response_model=list[ActivityBucket])
async def get_active_user_count(start_time: datetime, end_time: datetime):
    return await hourly_active_users(start_time, end_time)


@router.get("/usage/activity", response_model=ActivityResponse)
async def get_usage_activity(
    start_time: datetime,
    end_time: datetime,
    interval: str = Query(..., description="Bucket size, e.g. '15m', '1h', '1d', '1w'"),
):
    buckets = await or_422(usage_activity_over_time(start_time, end_time, interval))
    return {
        "start_time": start_time,
        "end_time": end_time,
        "interval": interval,
        "buckets": buckets,
    }
