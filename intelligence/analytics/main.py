from contextlib import asynccontextmanager
from fastapi import FastAPI
from analytics.db.pool import pool
from analytics.api import activity, breakdown, engagement, peak_hours, viewing_time

@asynccontextmanager
async def lifespan(app: FastAPI):
    await pool.open()
    yield
    await pool.close()


app = FastAPI(lifespan=lifespan)

app.include_router(activity.router)
app.include_router(breakdown.router)
app.include_router(engagement.router)
app.include_router(peak_hours.router)
app.include_router(viewing_time.router)
