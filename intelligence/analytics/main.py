from contextlib import asynccontextmanager
from pathlib import Path
import logging
from fastapi import FastAPI, Request
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from analytics.auth import COOKIE_NAME, verify_token
from analytics.config import settings
from analytics.db.pool import pool
from analytics.api import activity, auth as auth_routes, breakdown, engagement, peak_hours, viewing_time, rankings

log = logging.getLogger("analytics.auth")

BASE = Path(__file__).resolve().parent
templates = Jinja2Templates(directory=BASE / "templates")

OPEN_PATHS = ("/health", "/login", "/logout", "/static")


@asynccontextmanager
async def lifespan(app: FastAPI):
    if settings.analytics_password == "admin" or settings.analytics_secret_key == "dev-only-change-me":
        log.warning(
            "Analytics service uses default admin credentials — "
            "set ANALYTICS_USER, ANALYTICS_PASSWORD and ANALYTICS_SECRET_KEY."
        )
    await pool.open()
    yield
    await pool.close()


app = FastAPI(lifespan=lifespan)


@app.middleware("http")
async def require_admin(request: Request, call_next):
    if request.url.path.startswith(OPEN_PATHS):
        return await call_next(request)
    if verify_token(request.cookies.get(COOKIE_NAME, ""), settings.analytics_secret_key):
        return await call_next(request)
    if "text/html" in request.headers.get("accept", ""):
        return RedirectResponse("/login")
    return JSONResponse({"detail": "Authentication required"}, status_code=401)

app.mount("/static", StaticFiles(directory=BASE / "static"), name="static")

app.include_router(auth_routes.router)
app.include_router(activity.router)
app.include_router(breakdown.router)
app.include_router(engagement.router)
app.include_router(peak_hours.router)
app.include_router(viewing_time.router)
app.include_router(rankings.router)


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.get("/")
async def root():
    return RedirectResponse("/dashboard")


@app.get("/dashboard", response_class=HTMLResponse)
async def dashboard(request: Request):
    return templates.TemplateResponse(request, "dashboard.html")
