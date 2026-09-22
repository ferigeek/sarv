from datetime import datetime, timedelta, timezone

from scoring import PostFeatures, score_post

NOW = datetime(2026, 9, 21, 12, 0, tzinfo=timezone.utc)


def make_post(post_id="1", likes=10, dislikes=0, views=100, age_hours=0, followed=False,
              affinity=0.0, user_boost=1.0):
    return PostFeatures(
        post_id=post_id,
        like_count=likes,
        dislike_count=dislikes,
        view_count=views,
        created_at=NOW - timedelta(hours=age_hours),
        from_followed=followed,
        author_affinity=affinity,
        user_boost=user_boost,
    )


def test_zero_engagement_scores_zero():
    assert score_post(make_post(likes=0, views=0), now=NOW) == 0


def test_negative_engagement_clamped_to_zero():
    assert score_post(make_post(likes=0, views=0, dislikes=5), now=NOW) == 0


def test_dislikes_lower_score():
    clean = score_post(make_post(dislikes=0), now=NOW)
    penalized = score_post(make_post(dislikes=3), now=NOW)
    assert penalized < clean


def test_future_post_treated_as_fresh():
    fresh = make_post(age_hours=0)
    future = PostFeatures(
        post_id="1",
        like_count=10,
        dislike_count=0,
        view_count=100,
        created_at=NOW + timedelta(hours=5),
    )
    assert score_post(future, now=NOW) == score_post(fresh, now=NOW)


def test_recency_halves_at_48h():
    fresh = score_post(make_post(age_hours=0), now=NOW)
    old = score_post(make_post(age_hours=48), now=NOW)
    assert old == fresh / 2


def test_follow_boost_is_1_5x():
    plain = score_post(make_post(followed=False), now=NOW)
    boosted = score_post(make_post(followed=True), now=NOW)
    assert boosted == plain * 1.5


def test_higher_engagement_ranks_first():
    low = make_post(post_id="low", likes=1, views=1)
    high = make_post(post_id="high", likes=50, views=200)
    assert score_post(high, now=NOW) > score_post(low, now=NOW)


def test_zero_affinity_is_neutral():
    assert score_post(make_post(affinity=0.0), now=NOW) == score_post(make_post(), now=NOW)


def test_affinity_adds_ten_percent_per_point():
    base = score_post(make_post(affinity=0.0), now=NOW)
    assert score_post(make_post(affinity=5.0), now=NOW) == base * 1.5


def test_affinity_capped_at_ten_points():
    assert score_post(make_post(affinity=10.0), now=NOW) == score_post(make_post(affinity=99.0), now=NOW)


def test_negative_affinity_treated_as_zero():
    assert score_post(make_post(affinity=-4.0), now=NOW) == score_post(make_post(), now=NOW)
