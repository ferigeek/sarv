from datetime import datetime

from fastapi import APIRouter
from pydantic import BaseModel

from analytics.api._common import or_422
from analytics.db.queries.peak_hours import peak_activity_hours

router = APIRouter()


class PeakHourBucket(BaseModel):
    hour: int
    event_count: int
    active_users: int


class PeakHoursResponse(BaseModel):
    start_time: datetime
    end_time: datetime
    timezone: str
    peak_hour: PeakHourBucket
    buckets: list[PeakHourBucket]


@router.get("/usage/peak-hours", response_model=PeakHoursResponse)
async def get_peak_hours(start_time: datetime, end_time: datetime):
    result = await or_422(peak_activity_hours(start_time, end_time))
    return {
        "start_time": start_time,
        "end_time": end_time,
        **result,
    }
