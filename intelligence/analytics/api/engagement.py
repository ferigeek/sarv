from datetime import datetime

from fastapi import APIRouter, Query
from pydantic import BaseModel

from analytics.api._common import or_422
from analytics.db.queries.engagement import (
    engagement_over_time,
    engagement_totals,
)

router = APIRouter()


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


@router.get("/users/engagement", response_model=EngagementResponse)
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
    totals = await or_422(engagement_totals(start_time, end_time))
    buckets = (
        await or_422(engagement_over_time(start_time, end_time, interval))
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
