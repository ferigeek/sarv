from datetime import datetime

from fastapi import APIRouter, Query
from pydantic import BaseModel

from analytics.api._common import or_422
from analytics.db.queries.rankings import DEFAULT_LIMIT, MAX_LIMIT, most_active_users

router = APIRouter()


class MostActiveUser(BaseModel):
    user_id: int
    username: str
    display_name: str
    event_count: int


class MostActiveResponse(BaseModel):
    start_time: datetime
    end_time: datetime
    limit: int
    users: list[MostActiveUser]


@router.get("/users/most-active", response_model=MostActiveResponse)
async def get_most_active_users(
    start_time: datetime,
    end_time: datetime,
    limit: int = Query(DEFAULT_LIMIT, ge=1, le=MAX_LIMIT),
):
    users = await or_422(most_active_users(start_time, end_time, limit))
    return {
        "start_time": start_time,
        "end_time": end_time,
        "limit": limit,
        "users": users,
    }
