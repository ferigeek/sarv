ALTER TABLE posts
    ADD COLUMN view_count bigint NOT NULL DEFAULT 0;

ALTER TABLE posts
    ADD COLUMN like_count bigint NOT NULL DEFAULT 0;

ALTER TABLE posts
    ADD COLUMN dislike_count bigint NOT NULL DEFAULT 0;