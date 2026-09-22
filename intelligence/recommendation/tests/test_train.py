from datetime import datetime, timedelta, timezone

import pandas as pd

import train
from scoring import FEATURE_NAMES
from train import build_dataset

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
    assert list(df.columns) == FEATURE_NAMES + ["label"]
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
