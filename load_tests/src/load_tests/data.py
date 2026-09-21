"""Shared test-data pools and payload factories."""

from __future__ import annotations

import csv
import random
from pathlib import Path

CONTENT_POOL = [
    "Just shipped a small side project, feeling good about it.",
    "Hot take: chronological feeds beat algorithmic ones for close friends.",
    "Working on my bachelor's project, load testing the feed today.",
    "Does anyone else keep a notebook of ideas for posts that never ship?",
    "Morning run done. Now back to debugging recommendation scores.",
    "Reading about Postgres indexes, they matter more than I expected.",
    "What is the best way to learn Spring Security without the pain?",
    "Small progress every day compounds faster than weekend crunches.",
    "Testing search with short queries like feed, post, and sarv.",
    "Quote of the day: make it work, make it fast, then make it pretty.",
]

SEARCH_TERMS = [
    "sarv",
    "feed",
    "post",
    "test",
    "project",
    "spring",
    "hello",
    "day",
]

GENDERS = ["MALE", "FEMALE", "RATHER_NOT_TO_SAY"]


def random_content(rng: random.Random) -> str:
    base = rng.choice(CONTENT_POOL)
    return f"{base} #{rng.randint(1000, 9999)}"


def random_search_term(rng: random.Random) -> str:
    return rng.choice(SEARCH_TERMS)


def load_user_credentials(path: Path) -> list[tuple[str, str]]:
    if not path.is_file():
        return []
    with path.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        if not reader.fieldnames or "username" not in reader.fieldnames:
            return []
        return [
            (row["username"], row.get("password", ""))
            for row in reader
            if row.get("username") and row.get("password")
        ]


def extract_page_ids(payload: object, key: str = "id") -> list[int]:
    """Pull numeric ids out of a Spring Data Page body or a plain list."""
    items: object = payload
    if isinstance(payload, dict) and isinstance(payload.get("content"), list):
        items = payload["content"]
    if not isinstance(items, list):
        return []
    ids: list[int] = []
    for item in items:
        if isinstance(item, dict) and isinstance(item.get(key), int):
            ids.append(item[key])
    return ids


def extract_page_user_ids(payload: object) -> list[int]:
    ids = extract_page_ids(payload, "id")
    if ids:
        return ids
    if isinstance(payload, dict) and isinstance(payload.get("content"), list):
        out: list[int] = []
        for item in payload["content"]:
            if isinstance(item, dict) and isinstance(item.get("userId"), int):
                out.append(item["userId"])
        return out
    return []
