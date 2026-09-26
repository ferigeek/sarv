from datetime import datetime
from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel
from analytics.db.pool import pool
from analytics.db.queries.usage_time import (
    active_user_count as hourly_active_users,
    usage_activity_over_time,
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
