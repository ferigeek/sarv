ALTER TABLE follows
ADD CONSTRAINT chk_no_self_follow
CHECK (follower_id <> followed_id);