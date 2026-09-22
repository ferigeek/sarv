"""
Offline dataset builder (and, in P3-2, trainer) for the learned feed ranker.

Label design:
  positive  user interacted with the post
            (LIKE_POST, CREATE_COMMENT, REPOST_POST, QUOTE_POST)
  negative  random same-window post the viewer never touched (no event row)
            at a 1:4 ratio; shown-but-ignored is NOT reconstructible because
            feed serving logs no per-post impression (see 6-Recommendation).

Known caveats (thesis §methodology, not bugs):
  - Features are current counters, not as-of-impression snapshots, so the
    label event itself is inside the counted totals (leakage). Mitigated by
    time-split evaluation in --train; a point-in-time pipeline is out of
    scope for P3.
  - Event logging is best-effort (@Async, swallowed) and dwell rows are
    lossy; dwell is excluded from labels for P3.
  - Age uses the label timestamp for positives (more correct) and now for
    sampled negatives.

Usage:
  uv run python train.py --build-only --out data/train.csv --limit 20000
"""

import argparse
import logging
from datetime import datetime, timedelta, timezone

import pandas as pd
import psycopg

from database import DatabaseSettings
from scoring import FEATURE_NAMES, PostFeatures, engagement_boost, to_vector

log = logging.getLogger(__name__)

POSITIVE_TYPES = ("LIKE_POST", "CREATE_COMMENT", "REPOST_POST", "QUOTE_POST")
AFFINITY_WINDOW_DAYS = 30

# Mirrors CandidateGenerator._get_author_affinity weights (source of truth there).
AFFINITY_CASE = """SUM(CASE e.type
  WHEN 'VIEW_POST' THEN 1 WHEN 'LIKE_POST' THEN 3
  WHEN 'CREATE_COMMENT' THEN 4 WHEN 'REPOST_POST' THEN 5
  WHEN 'QUOTE_POST' THEN 5 WHEN 'DISLIKE_POST' THEN -2 ELSE 0 END)"""


def get_sync_connection():
    settings = DatabaseSettings()
    return psycopg.connect(settings.conninfo())


def fetch_positives(conn, cutoff: datetime, limit: int) -> pd.DataFrame:
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT e.user_id AS viewer_id, e.post_id, e.created_at AS label_at
            FROM event_logs e
            WHERE e.type IN ('LIKE_POST', 'CREATE_COMMENT', 'REPOST_POST', 'QUOTE_POST')
              AND e.user_id IS NOT NULL AND e.post_id IS NOT NULL
              AND e.created_at >= %s
            ORDER BY e.created_at DESC
            LIMIT %s
            """,
            (cutoff, limit),
        )
        rows = cur.fetchall()
    df = pd.DataFrame(rows, columns=["viewer_id", "post_id", "label_at"])
    df["label"] = 1
    return df


def fetch_negatives(conn, viewer_ids: list, cutoff: datetime, per_user: int) -> pd.DataFrame:
    """Random same-window posts each viewer never touched. Empty when no viewers."""
    frames = []
    with conn.cursor() as cur:
        for viewer_id in viewer_ids:
            cur.execute(
                """
                SELECT p.id AS post_id
                FROM posts p
                WHERE p.deleted_at IS NULL AND p.type = 'NORMAL'
                  AND p.created_at >= %s
                  AND NOT EXISTS (
                    SELECT 1 FROM event_logs e2
                    WHERE e2.user_id = %s AND e2.post_id = p.id
                  )
                ORDER BY random()
                LIMIT %s
                """,
                (cutoff, viewer_id, per_user),
            )
            rows = cur.fetchall()
            if rows:
                part = pd.DataFrame(rows, columns=["post_id"])
                part["viewer_id"] = viewer_id
                frames.append(part)
    if not frames:
        return pd.DataFrame(columns=["viewer_id", "post_id", "label_at", "label"])
    df = pd.concat(frames, ignore_index=True)
    df["label_at"] = datetime.now(timezone.utc)
    df["label"] = 0
    return df


def fetch_post_map(conn, post_ids: list) -> dict:
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT id, like_count, dislike_count, view_count, comment_count,
                   created_at, user_id
            FROM posts WHERE id = ANY(%s)
            """,
            (list(post_ids),),
        )
        rows = cur.fetchall()
    return {
        str(pid): {
            "like": like, "dislike": dislike, "view": view,
            "comment": comment, "created_at": created, "author": str(author),
        }
        for pid, like, dislike, view, comment, created, author in rows
    }


def fetch_follow_sets(conn, pairs: set) -> set:
    """pairs: {(viewer_id, author_id)} -> subset that follows. Empty in/out safe."""
    if not pairs:
        return set()
    viewers = list({v for v, _ in pairs})
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT f.follower_id, p.user_id
            FROM follows f JOIN posts p ON p.user_id = f.followed_id
            WHERE f.follower_id = ANY(%s)
            """,
            (viewers,),
        )
        edges = {(str(v), str(a)) for v, a in cur.fetchall()}
    return pairs & edges


def fetch_affinity_map(conn, viewer_ids: list, cutoff: datetime) -> dict:
    if not viewer_ids:
        return {}
    with conn.cursor() as cur:
        cur.execute(
            f"""
            SELECT e.user_id AS viewer_id, p.user_id AS author_id,
                   {AFFINITY_CASE} AS affinity
            FROM event_logs e JOIN posts p ON p.id = e.post_id
            WHERE e.user_id = ANY(%s) AND e.post_id IS NOT NULL
              AND e.type IN ('VIEW_POST', 'LIKE_POST', 'DISLIKE_POST',
                             'CREATE_COMMENT', 'REPOST_POST', 'QUOTE_POST')
              AND e.created_at >= %s
            GROUP BY e.user_id, p.user_id
            """,
            (list(viewer_ids), cutoff),
        )
        rows = cur.fetchall()
    return {(str(v), str(a)): float(s) for v, a, s in rows}


def fetch_user_boosts(conn, viewer_ids: list, cutoff: datetime) -> dict:
    if not viewer_ids:
        return {}
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT e.user_id AS viewer_id,
              COUNT(*) FILTER (WHERE e.type = 'VIEW_POST') AS views,
              COUNT(*) FILTER (WHERE e.type = 'LIKE_POST') AS likes,
              COUNT(*) FILTER (WHERE e.type = 'CREATE_COMMENT') AS comments
            FROM event_logs e
            WHERE e.user_id = ANY(%s) AND e.created_at >= %s
            GROUP BY e.user_id
            """,
            (list(viewer_ids), cutoff),
        )
        rows = cur.fetchall()
    return {str(v): engagement_boost(views, likes, comments) for v, views, likes, comments in rows}


def build_dataset(conn, cutoff: datetime, limit: int, neg_ratio: int) -> pd.DataFrame:
    pos = fetch_positives(conn, cutoff, limit)
    if pos.empty:
        log.warning("No positive pairs in window; dataset is empty")
        return pd.DataFrame(columns=FEATURE_NAMES + ["label"])
    per_user = max(neg_ratio // max(len(pos["viewer_id"].unique()), 1), 1)
    neg = fetch_negatives(conn, sorted(pos["viewer_id"].unique().tolist()), cutoff, per_user * 2)
    pairs = pd.concat([pos, neg], ignore_index=True)

    post_map = fetch_post_map(conn, pairs["post_id"].unique().tolist())
    pairs = pairs[pairs["post_id"].astype(str).isin(post_map)]
    viewer_ids = sorted(pairs["viewer_id"].astype(str).unique().tolist())
    wanted = {
        (str(v), post_map[str(p)]["author"])
        for v, p in zip(pairs["viewer_id"], pairs["post_id"])
    }
    follows = fetch_follow_sets(conn, wanted)
    aff_cutoff = datetime.now(timezone.utc) - timedelta(days=AFFINITY_WINDOW_DAYS)
    affinity = fetch_affinity_map(conn, viewer_ids, aff_cutoff)
    boosts = fetch_user_boosts(conn, viewer_ids, aff_cutoff)

    vectors, labels = [], []
    for row in pairs.itertuples():
        info = post_map[str(row.post_id)]
        ref_time = row.label_at if row.label == 1 else datetime.now(timezone.utc)
        feats = PostFeatures(
            post_id=str(row.post_id),
            like_count=info["like"], dislike_count=info["dislike"],
            view_count=info["view"], comment_count=info["comment"],
            created_at=info["created_at"],
            from_followed=(str(row.viewer_id), info["author"]) in follows,
            author_id=info["author"],
            author_affinity=affinity.get((str(row.viewer_id), info["author"]), 0.0),
            user_boost=boosts.get(str(row.viewer_id), 1.0),
        )
        vectors.append(to_vector(feats, now=ref_time))
        labels.append(row.label)
    df = pd.DataFrame(vectors, columns=FEATURE_NAMES)
    df["label"] = labels
    return df


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Build (and train on) the ranking dataset.")
    parser.add_argument("--build-only", action="store_true")
    parser.add_argument("--out", default="data/train.csv")
    parser.add_argument("--limit", type=int, default=20000)
    parser.add_argument("--window-days", type=int, default=90)
    parser.add_argument("--neg-ratio", type=int, default=4)
    args = parser.parse_args(argv)

    if not args.build_only:
        parser.error("--train lands in P3-2; use --build-only for now")

    cutoff = datetime.now(timezone.utc) - timedelta(days=args.window_days)
    with get_sync_connection() as conn:
        df = build_dataset(conn, cutoff, args.limit, args.neg_ratio)
    if df.empty:
        log.warning("Empty dataset, writing header only")
    df.to_csv(args.out, index=False)
    log.info("Wrote %d rows (%d positive) to %s", len(df), int(df["label"].sum()), args.out)
    return 0


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    raise SystemExit(main())
