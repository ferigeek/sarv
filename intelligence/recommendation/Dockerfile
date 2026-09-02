# ---------- Build stage ----------
FROM python:3.13-slim AS build

WORKDIR /app

COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy

COPY pyproject.toml uv.lock ./

RUN uv sync --frozen --no-install-project

COPY main.py candidate.py scoring.py database.py ./

RUN uv sync --frozen


# ---------- Runtime stage ----------
FROM python:3.13-slim

WORKDIR /app

RUN useradd --create-home --shell /usr/sbin/nologin appuser

COPY --from=build --chown=appuser:appuser /app/.venv /app/.venv

COPY --from=build --chown=appuser:appuser /app/main.py /app/main.py
COPY --from=build --chown=appuser:appuser /app/candidate.py /app/candidate.py
COPY --from=build --chown=appuser:appuser /app/scoring.py /app/scoring.py
COPY --from=build --chown=appuser:appuser /app/database.py /app/database.py

ENV PATH="/app/.venv/bin:$PATH"

USER appuser

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
