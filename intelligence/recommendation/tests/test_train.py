from datetime import datetime, timedelta, timezone

import numpy as np
import pandas as pd

import train
from scoring import FEATURE_NAMES
from train import RAW_COLUMNS, build_dataset, heuristic_scores, load_model, save_model, train_model

NOW = datetime(2026, 9, 21, 12, 0, tzinfo=timezone.utc)
CREATED = NOW - timedelta(hours=10)


class FakeCursor:
    def __init__(self, script):
        self.script = script
        self.calls = 0

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def execute(self, query, params=None):
        pass

    def fetchall(self):
        rows = self.script[self.calls]
        self.calls += 1
        return rows


class FakeConn:
    def __init__(self, script):
        self._cur = FakeCursor(script)

    def cursor(self):
        return self._cur


def make_script():
    return [
        [(7, 42, NOW)],  # positives
        [(43,)],  # negatives for viewer 7
        [  # post map: id, like, dislike, view, comment, created, author
            (42, 10, 1, 100, 2, CREATED, 11),
            (43, 1, 0, 5, 0, CREATED, 12),
        ],
        [(7, 11)],  # follows: viewer 7 follows author 11
        [(7, 11, 6.0)],  # affinity
        [(7, 20, 8, 2)],  # boosts: views, likes, comments
    ]


def test_build_dataset_assembles_vectors_and_labels():
    df = build_dataset(FakeConn(make_script()), NOW - timedelta(days=90), 1000, 4)
    assert list(df.columns) == FEATURE_NAMES + RAW_COLUMNS + ["label"]
    assert sorted(df["label"].tolist()) == [0, 1]
    pos = df[df["label"] == 1].iloc[0]
    neg = df[df["label"] == 0].iloc[0]
    assert pos["from_followed"] == 1.0
    assert neg["from_followed"] == 0.0
    assert pos["affinity_capped"] == 6.0
    assert neg["affinity_capped"] == 0.0
    assert pos["age_hours"] == 10.0
    assert pos["log_comment"] > 0


def test_build_dataset_empty_without_positives():
    df = build_dataset(FakeConn([[]]), NOW - timedelta(days=90), 1000, 4)
    assert df.empty


def test_fetch_helpers_empty_safe():
    conn = FakeConn([[], [], []])
    assert train.fetch_negatives(conn, [], NOW, 4).empty
    assert train.fetch_follow_sets(conn, set()) == set()
    assert train.fetch_affinity_map(conn, [], NOW) == {}
    assert train.fetch_user_boosts(conn, [], NOW) == {}


def make_separable_frame(n: int = 60, seed: int = 7) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    pos = pd.DataFrame({
        "log_like": rng.normal(4.0, 0.5, n),
        "log_dislike": rng.normal(0.0, 0.2, n),
        "log_view": rng.normal(5.0, 0.5, n),
        "log_comment": rng.normal(1.5, 0.5, n),
        "age_hours": rng.normal(5.0, 2.0, n),
        "from_followed": np.ones(n),
        "affinity_capped": rng.normal(6.0, 1.0, n),
        "user_boost": np.ones(n),
        "label": np.ones(n, dtype=int),
    })
    neg = pd.DataFrame({
        "log_like": rng.normal(0.5, 0.5, n),
        "log_dislike": rng.normal(0.5, 0.5, n),
        "log_view": rng.normal(1.0, 0.5, n),
        "log_comment": np.zeros(n),
        "age_hours": rng.normal(100.0, 20.0, n),
        "from_followed": np.zeros(n),
        "affinity_capped": np.zeros(n),
        "user_boost": np.ones(n),
        "label": np.zeros(n, dtype=int),
    })
    return pd.concat([pos, neg], ignore_index=True)


def test_train_model_reports_metrics_above_baseline():
    df = make_separable_frame()
    pipe, metrics = train_model(df, seed=42)
    for key in ("accuracy", "log_loss", "roc_auc", "precision_at_10",
                "baseline_prevalence", "n_train", "n_test"):
        assert key in metrics
    assert metrics["accuracy"] > metrics["baseline_prevalence"]
    assert metrics["roc_auc"] > 0.9
    assert pipe.predict_proba(df[FEATURE_NAMES].to_numpy()[:2]).shape == (2, 2)


def test_save_load_model_roundtrip(tmp_path):
    df = make_separable_frame()
    pipe, metrics = train_model(df, seed=42)
    out = str(tmp_path / "model.pkl")
    save_model(pipe, metrics, out, seed=42)
    restored = load_model(out)
    X = df[FEATURE_NAMES].to_numpy(dtype=float)
    assert (restored.predict(X) == pipe.predict(X)).all()


def test_heuristic_baseline_matches_score_post():
    from scoring import PostFeatures, score_post

    df = make_separable_frame(20)
    df["raw_like"] = 5
    df["raw_dislike"] = 0
    df["raw_view"] = 50
    df["raw_comment"] = 1
    df["raw_age_hours"] = 12.0
    df["raw_followed"] = 1
    df["raw_affinity"] = 5.0
    df["raw_user_boost"] = 1.0
    df["label_at"] = NOW.isoformat()
    scores = heuristic_scores(df)
    expected = score_post(PostFeatures(
        post_id="x", like_count=5, dislike_count=0, view_count=50,
        created_at=NOW - timedelta(hours=12), from_followed=True,
        author_affinity=5.0, comment_count=1,
    ), now=NOW)
    assert scores[0] == expected


def test_train_reports_heuristic_baseline():
    df = make_separable_frame()
    n = len(df)
    df["raw_like"] = df["log_like"]
    df["raw_dislike"] = 0
    df["raw_view"] = df["log_view"]
    df["raw_comment"] = df["log_comment"]
    df["raw_age_hours"] = df["age_hours"]
    df["raw_followed"] = df["from_followed"]
    df["raw_affinity"] = df["affinity_capped"]
    df["raw_user_boost"] = 1.0
    df["label_at"] = NOW.isoformat()
    _, metrics = train_model(df, seed=42)
    assert metrics["heuristic_roc_auc"] is not None
    assert metrics["heuristic_roc_auc"] > 0.9
    assert metrics["heuristic_precision_at_10"] is not None


def test_train_skips_baseline_without_raw_columns():
    _, metrics = train_model(make_separable_frame(), seed=42)
    assert metrics["heuristic_roc_auc"] is None
    assert metrics["heuristic_precision_at_10"] is None
