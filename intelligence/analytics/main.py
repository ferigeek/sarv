from contextlib import asynccontextmanager
from fastapi import FastAPI
from analytics.db.pool import pool


@asynccontextmanager
async def lifespan(app: FastAPI):
    await pool.open()
    yield
    await pool.close()


app = FastAPI(lifespan=lifespan)
