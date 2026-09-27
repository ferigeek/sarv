from pathlib import Path

from fastapi import APIRouter, Form, Request
from fastapi.responses import RedirectResponse
from fastapi.templating import Jinja2Templates

from analytics.auth import (
    COOKIE_NAME,
    check_credentials,
    create_token,
    throttle,
    verify_token,
)
from analytics.config import settings

router = APIRouter()

templates = Jinja2Templates(directory=Path(__file__).resolve().parent.parent / "templates")


def _login_page(request: Request, error: str | None, status: int = 200):
    return templates.TemplateResponse(request, "login.html", {"error": error}, status_code=status)


def _client_ip(request: Request) -> str:
    forwarded = request.headers.get("x-forwarded-for")
    if forwarded:
        return forwarded.split(",")[0].strip()
    return request.client.host if request.client else "unknown"


@router.get("/login")
async def login_form(request: Request):
    if verify_token(request.cookies.get(COOKIE_NAME, ""), settings.analytics_secret_key):
        return RedirectResponse("/dashboard", status_code=303)
    return _login_page(request, None)


@router.post("/login")
async def login(request: Request, username: str = Form(...), password: str = Form(...)):
    ip = _client_ip(request)
    if throttle.is_locked(ip):
        return _login_page(request, "Too many failed attempts — try again in a minute.", 429)
    if not check_credentials(
        username, password, settings.analytics_user, settings.analytics_password
    ):
        throttle.register_fail(ip)
        return _login_page(request, "Invalid username or password.", 401)
    throttle.reset(ip)
    token = create_token(username, settings.analytics_secret_key, settings.session_ttl_seconds)
    response = RedirectResponse("/dashboard", status_code=303)
    response.set_cookie(
        COOKIE_NAME,
        token,
        max_age=settings.session_ttl_seconds,
        httponly=True,
        samesite="lax",
        path="/",
    )
    return response


@router.post("/logout")
async def logout():
    response = RedirectResponse("/login", status_code=303)
    response.delete_cookie(COOKIE_NAME, path="/")
    return response
