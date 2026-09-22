from prometheus_client import Counter, Histogram, Info

from scoring import MODEL_VERSION

CACHE_EVENTS = Counter(
    "feed_cache_events_total",
    "Feed cache outcomes (hit, miss, or bypass on error).",
    ["outcome"],
)
DB_QUERY_SECONDS = Histogram(
    "feed_db_query_seconds",
    "Candidate query latency by source.",
    ["query"],
)
CANDIDATES_COUNT = Histogram(
    "feed_candidates_count",
    "Candidates per request by source (trending, following, follower, deduped).",
    ["source"],
    buckets=(0, 10, 25, 50, 100, 200, 300),
)
SCORING_SECONDS = Histogram(
    "feed_scoring_seconds",
    "Time to score and sort candidates.",
    ["ranker"],
)
REQUEST_SECONDS = Histogram(
    "feed_request_seconds",
    "Total feed request time including cache lookup.",
    ["outcome", "ranker"],
)
RESULT_TOTAL = Histogram(
    "feed_result_total",
    "Ranked total per feed request.",
    buckets=(0, 10, 25, 50, 100, 200, 300),
)
SCORES = Histogram(
    "feed_scores",
    "Distribution of returned post scores.",
)
MODEL_INFO = Info("feed_model", "Active ranking model.")
MODEL_INFO.info({"version": MODEL_VERSION})


def observe_cache(outcome: str) -> None:
    CACHE_EVENTS.labels(outcome=outcome).inc()


def observe_db_query(query: str, seconds: float) -> None:
    DB_QUERY_SECONDS.labels(query=query).observe(seconds)


def observe_candidates(source: str, count: int) -> None:
    CANDIDATES_COUNT.labels(source=source).observe(count)


def observe_scoring(ranker: str, seconds: float) -> None:
    SCORING_SECONDS.labels(ranker=ranker).observe(seconds)


def observe_request(outcome: str, ranker: str, seconds: float) -> None:
    REQUEST_SECONDS.labels(outcome=outcome, ranker=ranker).observe(seconds)


def observe_result(total: int, scores: list[float]) -> None:
    RESULT_TOTAL.observe(total)
    for score in scores:
        SCORES.observe(score)
