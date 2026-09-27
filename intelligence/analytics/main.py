from contextlib import asynccontextmanager
from pathlib import Path
from fastapi import FastAPI, Request
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from analytics.db.pool import pool
from analytics.api import activity, breakdown, engagement, peak_hours, viewing_time, rankings

BASE = Path(__file__).resolve().parent
templates = Jinja2Templates(directory=BASE / "templates")

@asynccontextmanager
async def lifespan(app: FastAPI):
    await pool.open()
    yield
    await pool.close()


app = FastAPI(lifespan=lifespan)

app.mount("/static", StaticFiles(directory=BASE / "static"), name="static")

app.include_router(activity.router)
app.include_router(breakdown.router)
app.include_router(engagement.router)
app.include_router(peak_hours.router)
app.include_router(viewing_time.router)
app.include_router(rankings.router)


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.get("/dashboard", response_class=HTMLResponse)
async def dashboard(request: Request):
    return templates.TemplateResponse(request, "dashboard.html")
