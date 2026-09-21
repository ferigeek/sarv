-- Recommendation feed indexes (candidate.py query paths).
--
-- 1. idx_posts_trending backs _get_trending_posts:
--      posts WHERE deleted_at IS NULL AND type = 'NORMAL'
--        AND created_at >= cutoff
--      ORDER BY (like_count + view_count) DESC, created_at DESC
--      LIMIT 100
--    The partial predicate matches the query filter exactly; the expression
--    btree avoids a full scan + sort over the 7-day window.
--
-- 2. idx_posts_user_type_created_at backs _get_following_posts and
--    _get_follower_posts:
--      posts JOIN follows ... WHERE p.user_id = ?
--        AND p.deleted_at IS NULL AND p.type = 'NORMAL'
--        AND p.created_at >= cutoff
--      ORDER BY p.created_at DESC LIMIT 50
--    Complements idx_posts_user_created_at (V5) with the type/deleted
--    predicate the feed queries always carry.

CREATE INDEX idx_posts_trending
    ON posts (((like_count + view_count)) DESC, created_at DESC)
    WHERE deleted_at IS NULL AND type = 'NORMAL';

CREATE INDEX idx_posts_user_type_created_at
    ON posts (user_id, created_at DESC)
    WHERE deleted_at IS NULL AND type = 'NORMAL';
