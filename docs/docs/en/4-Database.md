# Database Design

## Overview

This project uses PostgreSQL as the main database for a social feed platform.  
The database is designed to support users, posts, interactions, media, and event logging for analytics and machine learning.

---
## Entity Relationship Diagram (ERD)

![ERD](../database/erd.png)

---
## Core Entities

### Users
Stores user account and profile information.

Key fields:
- id (primary key)
- username (unique)
- email (unique)
- password_hash
- status (active, suspended, banned, deleted)
- gender (enum)
- profile_picture (media reference)

### Posts
Represents user-generated content.

Supports multiple content types:
- NORMAL posts
- COMMENTS (via parent_id)
- REPOSTS (via repost_of_id)
- QUOTES (quote posts with additional referenced content)

Each post belongs to a user.

### Reactions
Stores user reactions to posts.

- reaction_type:
  - 1 = like
  - -1 = dislike
- Each user can react once per post (unique constraint on post_id + user_id)

### Follows
Represents follower-following relationships between users.

- follower_id → user who follows
- followed_id → user being followed
- prevents duplicate follows

### Media
Stores metadata for uploaded files.

Includes:
- file size
- MIME type
- SHA-256 hash (for deduplication)
- owner reference

### Event Logs
Stores user activity events for analytics and machine learning.

Used for:
- feed ranking
- behavior analysis
- recommendation systems

Examples of events:
- LOGIN
- VIEW_POST
- LIKE_POST
- CREATE_POST
- FOLLOW_USER
- REQUEST_FEED

User ID is nullable to allow anonymization.

---
## Enums

### Gender
- male
- female
- rather_not_to_say

### User Status
- active
- suspended
- banned
- deleted

### Post Category
- NORMAL
- COMMENT
- REPOST
- QUOTE

### Event Type
- VIEW_POST
- LIKE_POST
- DISLIKE_POST
- CREATE_COMMENT
- REPOST_POST
- FOLLOW_USER
- UNFOLLOW_USER
- VIEW_PROFILE
- CREATE_POST
- REQUEST_FEED
- LOGIN

---
## Relationships

- A user can create many posts
- A user can follow many users
- A user can react to many posts
- A post can have many reactions
- A post can have comments (self-referencing)
- Posts can also quote other posts (QUOTE type)
- Media can be owned by a user
- Events are linked to users, posts, or target users (nullable)

---
## Deletion Strategy

- Users: cascade delete posts, follows, reactions
- Posts: deleted if user is deleted
- Media: ownership is set to NULL if user is deleted
- Event logs: user reference is set to NULL (for analytics preservation)

---
## Indexing Strategy (planned)

Indexes should be added for performance on:

- posts.user_id
- reactions.post_id
- reactions.user_id
- follows.follower_id
- follows.followed_id
- event_logs.user_id

---
## Notes

- Event logs are designed for machine learning and recommendation systems
- Soft deletion is supported via deleted_at fields in posts and media
- The schema is optimized for read-heavy workloads (feed generation)

