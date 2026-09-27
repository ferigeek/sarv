# ---------- UI build stage ----------
FROM node:24-alpine AS ui

WORKDIR /ui

# Install dependencies first for better layer caching
COPY ui/package.json ui/package-lock.json ./
RUN npm ci

# Copy UI source and build (output lands in /static/dist via vite.config.js)
COPY ui/ ./
RUN npm run build


# ---------- Python dependencies stage ----------
FROM python:3.13-slim AS build

WORKDIR /app/analytics

COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy

COPY pyproject.toml uv.lock ./

RUN uv sync --frozen --no-install-project

COPY __init__.py main.py config.py auth.py ./
COPY api/ ./api/
COPY db/ ./db/
COPY templates/ ./templates/
COPY static/ ./static/

RUN uv sync --frozen


# ---------- Runtime stage ----------
FROM python:3.13-slim

WORKDIR /app

RUN useradd --create-home --shell /usr/sbin/nologin appuser

COPY --from=build --chown=appuser:appuser /app/analytics /app/analytics
COPY --from=ui --chown=appuser:appuser /static/dist /app/analytics/static/dist

# Package root is /app/analytics, so the importable package `analytics`
# resolves with /app on the path.
ENV PYTHONPATH="/app" \
    PATH="/app/analytics/.venv/bin:$PATH"

USER appuser

EXPOSE 8001

CMD ["uvicorn", "analytics.main:app", "--host", "0.0.0.0", "--port", "8001"]
