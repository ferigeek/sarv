"""Shared configuration for Sarv load tests.

All values are overridable via environment variables so the same
locustfile works locally and in CI without code changes.
"""

from __future__ import annotations

import os
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
SRC_ROOT = PROJECT_ROOT / "src"
DATA_DIR = PROJECT_ROOT / "data"

BASE_URL = os.getenv("SARV_BASE_URL", "http://localhost:8080").rstrip("/")
USERS_FILE = Path(os.getenv("SARV_USERS_FILE", str(DATA_DIR / "users.csv")))
SEED_PASSWORD = os.getenv("SARV_SEED_PASSWORD", "Password123!")
SINGLE_USERNAME = os.getenv("SARV_USERNAME", "")
SINGLE_PASSWORD = os.getenv("SARV_PASSWORD", "")

DEFAULT_PAGE_SIZE = int(os.getenv("SARV_PAGE_SIZE", "20"))
REQUEST_TIMEOUT = int(os.getenv("SARV_REQUEST_TIMEOUT", "10"))
