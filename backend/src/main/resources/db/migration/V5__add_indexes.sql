-- PostgreSQL does not index foreign key columns automatically, and the
-- existing unique constraints do not cover every lookup pattern used by
-- the repository layer. These indexes back the queries below:
--
-- follows (followed_id):        FollowRepository.findByFollowed
--                               (the unique (follower_id, followed_id)
--                               constraint leads with follower_id, so it
--                               cannot serve a followed_id-only filter)
-- posts (user_id, created_at):  user timelines/feed lookups by author,
--                               newest first; also keeps users.id FK checks cheap
-- posts (parent_id):            comment tree navigation via posts.id FK
-- posts (repost_of_id):         repost navigation via posts.id FK
-- reactions (user_id):          per-user reaction existence/history;
--                               complements the unique (post_id, user_id) constraint
-- event_logs (user_id, created_at):
--                               per-user activity timelines for analytics
-- event_logs (type, created_at):
--                               hot-topic/peak-usage style aggregations
-- event_logs (post_id), event_logs (target_user_id):
--                               entity-scoped analytics queries

CREATE INDEX idx_follows_followed_id ON follows (followed_id);

CREATE INDEX idx_posts_user_created_at ON posts (user_id, created_at);
CREATE INDEX idx_posts_parent_id ON posts (parent_id);
CREATE INDEX idx_posts_repost_of_id ON posts (repost_of_id);

CREATE INDEX idx_reactions_user_id ON reactions (user_id);

CREATE INDEX idx_event_logs_user_created_at ON event_logs (user_id, created_at);
CREATE INDEX idx_event_logs_type_created_at ON event_logs (type, created_at);
CREATE INDEX idx_event_logs_post_id ON event_logs (post_id);
CREATE INDEX idx_event_logs_target_user_id ON event_logs (target_user_id);
