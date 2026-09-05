# Core Backend

This document describes the implementation of the Core Backend service, which is the entry point for all user requests and the executor of the platform's primary business logic.

It covers the technology stack, project structure, API reference, authentication, media storage, event logging, error handling, configuration, and testing. It reflects the **current state of the implementation**; parts that are designed but not yet built are listed at the end under [Implementation Status](#implementation-status).

---

## Overview

The Core Backend is a server-side application written in Java with Spring Boot. It is responsible for:

- User management, profiles, and authentication
- Posts and user interactions (reactions, comments, reposts, quotes, follows)
- Media upload and serving
- Recording user behavior events for analytics and recommendation purposes
- Communication with the Recommendation Service (feed ranking with graceful degradation)

All requests are served through a REST API and secured with JWT-based authentication.

---

## Technology Stack

| Concern | Technology |
|---------|------------|
| Language / Runtime | Java 25 |
| Framework | Spring Boot 4.1 (MVC, Data JPA, Security, Validation, AOP) |
| Database | PostgreSQL, accessed via Spring Data JPA / Hibernate |
| Migrations | Flyway (`spring-boot-starter-flyway`) |
| Authentication | JWT (JJWT 0.13), BCrypt password hashing |
| API Documentation | springdoc-openapi (Swagger UI) |
| Media Storage | Local filesystem (`LocalStorageService`) |
| Utility | Lombok |
| Tests | JUnit 5, MockMvc, H2 (runtime scope) |

---

## Project Structure

The backend follows a layered architecture. The source root is `backend/src/main/java/com/github/ferigeek/sarv/`:

```
controller/   REST endpoints (Auth, User, Follow, Post, Reaction, Media, Feed)
service/      Business logic (Auth, User, Follow, Post, Reaction, Media, Feed,
              CustomUserDetails, LocalStorage, ObjectStorage interface)
repository/   Spring Data JPA repositories
entity/       JPA entities (User, Post, Media, Follow, Reaction, EventLog)
entity/type/  Enums (PostCategory, EventType, Gender, UserStatus)
dto/          request/ and response/ data transfer objects
security/     SecurityConfig, JwtUtil, JwtAuthFilter, OpenApiConfig
aspect/       LogEvent annotation + EventLoggingAspect
exception/    Custom exceptions + GlobalExceptionHandler
client/       RecommendationClient + RecommendationResponse (feed ranking)
config/       RestClientConfig (recommendation HTTP client)
```

Incoming HTTP requests are handled in the controller layer, where authentication, validation, and access control happen. Business logic lives in the service layer, and persistence is handled through the repository layer.

---

## API Reference

All endpoints are prefixed with `/api`. Except where marked **public**, every endpoint requires an `Authorization: Bearer <token>` header and returns `404` / `401` when the JWT is missing or invalid. The primary consumer is the Frontend web client (see [7-Frontend.md](./7-Frontend.md)), which calls these endpoints same-origin via its `/api` proxy.

### Authentication (`/api/auth`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | public | Registers a new user and returns the created profile together with a JWT token |
| POST | `/api/auth/login` | public | Authenticates the user and returns a JWT token string |

Registration request fields: `username` (≥2 chars), `password` (8–50 chars), `confirmPassword` (must match `password`), `email`, `displayName` (≥2 chars), `gender` (`MALE`, `FEMALE`, `RATHER_NOT_TO_SAY`). Duplicate usernames are rejected with `409 Conflict`. A `LOGIN` event is logged on every successful login; registration performs an automatic login and therefore also produces a `LOGIN` event.

### Users & Profiles (`/api/users`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/users/{userId}` | bearer | Returns the profile of a user; logs `VIEW_PROFILE` |
| GET | `/api/users/me` | bearer | Returns the profile of the authenticated user; logs `VIEW_PROFILE` |
| PUT | `/api/users/me` | bearer | Updates the authenticated user's profile |
| GET | `/api/users?query=` | bearer | Searches users by username or display name (case-insensitive, partial match), paginated |
| GET | `/api/users/{userId}/posts` | bearer | Paginated posts of a user, newest first (`size=10, sort=createdAt,DESC`); empty page for unknown users |
| GET | `/api/users/{userId}/reacted-posts?filter=` | bearer | Paginated posts the user reacted to, newest reactions first; `filter` is `ALL` (default), `LIKE`, or `DISLIKE`; client `sort` is ignored; soft-deleted posts excluded |
| GET | `/api/users/{userId}/stats` | bearer | Follower and following counts (`{userId, followerCount, followingCount}`), served separately from the profile so count computation never blocks profile rendering |

Profile update fields: `displayName` (required, ≥2 chars), `bio` (optional), `location` (optional), `gender` (required), `profilePictureId` (optional media id). `PUT` semantics are used: fields sent as `null` or blank are cleared, except `displayName` and `gender` which are mandatory.

### Follows (`/api/users/{userId}/followers`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/users/{userId}/followers` | bearer | Paginated list of the user's followers |
| GET | `/api/users/{userId}/following` | bearer | Paginated list of the users the user follows |
| POST | `/api/users/{userId}/followers` | bearer | Follows the user; `201 Created`; logs `FOLLOW_USER` |
| DELETE | `/api/users/{userId}/followers` | bearer | Unfollows the user; `204 No Content`; logs `UNFOLLOW_USER` |

Self-following is prevented by a database check constraint.

### Posts (`/api/posts`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/posts/{postId}` | bearer | Returns the post and increments its `view_count`; logs `VIEW_POST` |
| GET | `/api/posts/search?query=` | bearer | Searches post text (case-insensitive, partial match), paginated; blank `query` is rejected with `400`; default `size=10, sort=createdAt,DESC` |
| POST | `/api/posts` | bearer | Creates a post; `201 Created` with a `Location` header; logs `CREATE_POST` |
| GET | `/api/posts/{postId}/comments?sortBy=` | bearer | Paginated comments of a post; `sortBy` is `NEWEST` (default, `createdAt DESC`) or `MOST_LIKED` (`likeCount DESC`); client `sort` is ignored |
| PUT | `/api/posts/{postId}` | bearer | Updates the post's content/media; owner only (`403` otherwise) |
| DELETE | `/api/posts/{postId}` | bearer | Soft-deletes the post; owner only |

A post is created from: `postCategory`, `content` (max 280 chars), `mediaId`, `parentId`, `repostOfId`. The category determines which fields are valid:

| Category | Content/Media | `parentId` | `repostOfId` |
|----------|---------------|------------|--------------|
| NORMAL | at least one required | forbidden | forbidden |
| COMMENT | at least one required | required | optional (quote-style comment) |
| QUOTE | at least one required | forbidden | required |
| REPOST | both forbidden | forbidden | required |

Invalid combinations are rejected with `400 Bad Request` (`PostNotValidException`). Creating a `COMMENT` atomically increments the parent post's `comment_count`. Post update uses `PUT` semantics so that a field can be explicitly cleared with `null`. Deleting a post sets `deleted_at` and clears the author reference (soft delete).

### Reactions (`/api/posts/{postId}/reactions`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/posts/{postId}/reactions` | bearer | Adds or changes the caller's reaction (`reactionType`: `1` = like, `-1` = dislike); logs `LIKE_POST` |
| GET | `/api/posts/{postId}/reactions` | bearer | Returns like/dislike counts and the caller's current reaction (`0` = none) |
| DELETE | `/api/posts/{postId}/reactions` | bearer | Removes the caller's reaction; `204 No Content` |

A user can have at most one reaction per post (unique constraint on `post_id + user_id`). Adding a reaction of the opposite type switches it, and the post's `like_count` / `dislike_count` counters are adjusted accordingly. The `LIKE_POST` event is recorded for both likes and dislikes in the current implementation; removing a reaction is not logged.

### Feed (`/api/feed`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/feed/chronological` | bearer | Chronological feed — `deletedAt IS NULL` ordered `createdAt DESC`. Default `page=0,size=20,sort=createdAt,DESC` (`@PageableDefault`); client `sort` honored |
| GET | `/api/feed/recommended` | bearer | Personalized ranked feed — forwards `page/size` to `GET http://recommendation:8000/feed?user_id=&page=&size=` ( `RecommendationClient`, `RestClientConfig` timeout 1500 ms), `score desc` order, **ignores any `sort` param** (sanitized to unsorted `PageRequest`), graceful fallback to chronological on empty/timeout/5xx |

Both endpoints require `Authorization: Bearer <token>` and return `Page<PostResponse>` with identical shape:

```json
{
  "content": [
    {
      "id": 1,
      "userId": 10,
      "postCategory": "NORMAL",
      "content": "hello world",
      "createdAt": "2026-09-02T10:00:00+00:00",
      "updatedAt": "2026-09-02T10:00:00+00:00",
      "mediaId": 5,
      "repostOfId": null,
      "parentId": null,
      "viewCount": 5,
      "likeCount": 2,
      "dislikeCount": 1,
      "commentCount": 0
    }
  ],
  "page": { "size": 20, "number": 0, "totalElements": 100, "totalPages": 5 }
}
```

**Request:**
`GET /api/feed/chronological?page=0&size=20&sort=createdAt,desc` and `GET /api/feed/recommended?page=1&size=10` (any `sort` on recommended is ignored; ranking is always server-side). Empty feed returns `content: []` with `totalElements: 0`.

**Business rules:**
- Soft-delete filter — both `findChronologicalFeed` and `findAllByIdsFiltered` filter `deletedAt IS NULL`.
- View counting — every post served from either feed endpoint gets one view via an atomic bulk increment (`incrementViewCounts`), and the returned `PostResponse` already reflects it; deleted/missing hydrated posts are excluded from the increment.
- Rank-order preservation — recommended hydrates via `findAllByIdsFiltered` and reorders in-memory to preserve `score desc` order; invalid `post_id` strings are skipped, deleted/missing posts are dropped but `total` still reflects recommendation `total` (may cause pagination holes on last page).
- Pagination — chronological `total` = DB count; recommended `total` = recommendation `total` before filtering (fallback to `content.size()` if legacy).

**Errors:**
`403 Forbidden` when unauthenticated, `404 Not Found` `User not found with username: <ghost>` on recommended only (username lookup), `405 Method Not Allowed` for `POST/PUT/DELETE` on same path, `400 Bad Request` for `?page=abc` (`MethodArgumentTypeMismatchException`), `500 Internal Server Error` only if both recommendation and chronological fail. Recommendation timeout/5xx/empty body/parse failure never returns 5xx — it logs `WARN` and returns chronological page transparently.

**Dependencies:**
`recommendation.base-url` (`RECOMMENDATION_URL` env, default `http://recommendation:8000` via `RestClientConfig`) and `recommendation.timeout-ms` (`RECOMMENDATION_TIMEOUT_MS`, default `1500`, `500` in tests) with `SimpleClientHttpRequestFactory` connect/read timeout and `GET /health` polling (docker-compose `interval 10s`).

Both endpoints log `REQUEST_FEED` with `metadata {feed_type: chronological|recommended, page,size,total_elements,returned,requested_page,requested_size}` for analytics; see [Event Logging](#event-logging).

### Media (`/api/media`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/media` | bearer | Uploads a file (`multipart/form-data`, field `file`, max 50 MB); returns `{id, url}` |
| GET | `/api/media/{mediaId}` | bearer | Streams the stored file with its MIME type |
| GET | `/api/media/{mediaId}/metadata` | bearer | Returns metadata: `id`, `size`, `name`, `mimeType`, `createdAt` |

Uploads are content-addressed by SHA-256, so identical content is stored only once. Re-uploading bytes that already exist returns the existing `{id, url}` with `201 Created` instead of failing — no duplicate `Media` row is created (a concurrent-race fallback resolves to the winner's row).

---

## Authentication & Authorization

1. `POST /api/auth/register` creates the user (password hashed with BCrypt) and immediately returns a JWT. `POST /api/auth/login` verifies credentials through Spring Security's `AuthenticationManager` and returns a fresh token.
2. The JWT is signed with HS256 and contains `sub` (username), `iat`, and `exp`. The signing secret comes from the `JWT_SECRET` environment variable and must be at least 32 bytes; `JWT_EXPIRATION` controls the token lifetime in milliseconds.
3. Every request passes through `JwtAuthFilter`, which extracts the `Authorization: Bearer <token>` header, validates the token, loads the user, and sets the security context. Sessions are stateless and CSRF is disabled.
4. The following paths are public: `/api/auth/login`, `/api/auth/register`, `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`. Everything else requires authentication.
5. `CustomUserDetailsService` maps user status to account state: only `ACTIVE` users are enabled, and `SUSPENDED` users have their account locked.

The OpenAPI specification with a global `bearerAuth` security scheme is available at `/swagger-ui.html`.

---

## Media Storage

Media binaries are stored on the **local filesystem** — object storage is not used in the current implementation.

- `ObjectStorageService` is a small interface (`uploadObject`, `download`, `delete`) that decouples the media flow from the concrete backend.
- `LocalStorageService` is the current implementation. Files are written to the directory configured by `STORAGE_DIR` (default `uploads`). The file name is the SHA-256 hash of the content, which provides content-based deduplication and stable object keys.
- Download and delete operations verify that the resolved path stays inside the storage directory to prevent path-traversal attacks.
- The database only stores metadata: `size`, `name`, `mime_type`, `sha_256` (unique), `created_at`, `owner_id`. Media is referenced from posts (`media_id`) and user profiles (`profile_picture`).

---

## Event Logging

User behavior is recorded through an AOP-based mechanism:

- Controller methods annotated with `@LogEvent(EventType.XXX)` produce a row in `event_logs` after successful execution (`@AfterReturning`).
- The `EventLoggingAspect` stores the acting user, event type, timestamp, and — depending on the event type — the affected post or target user.
- The `event_logs` schema also includes `session_id` (groups actions of one usage session; unrelated to JWT) and `metadata` (JSONB, for event-specific information). For `REQUEST_FEED` the aspect now populates `metadata` with `{feed_type: chronological|recommended, page, size, total_elements, returned, requested_page, requested_size}`.

Event types: `VIEW_POST`, `LIKE_POST`, `DISLIKE_POST`, `CREATE_COMMENT`, `REPOST_POST`, `FOLLOW_USER`, `UNFOLLOW_USER`, `VIEW_PROFILE`, `CREATE_POST`, `REQUEST_FEED`, `LOGIN`. `REQUEST_FEED` is produced by both feed endpoints (`GET /api/feed/chronological` and `GET /api/feed/recommended`).

---

## Error Handling

All errors are converted to RFC 9457 `ProblemDetail` responses by `GlobalExceptionHandler`:

| Situation | HTTP Status |
|-----------|-------------|
| Entity not found (user, post, media) | `404 Not Found` |
| Validation failures, malformed JSON, bad requests | `400 Bad Request` |
| Unauthorized modification (not the owner) | `403 Forbidden` |
| Duplicate username | `409 Conflict` |
| Bad credentials / authentication failure | `401 Unauthorized` |
| Storage failures and unexpected exceptions | `500 Internal Server Error` |

Each `ProblemDetail` includes `status`, `title`, `detail`, and `instance` (the request URI).

---

## Pagination

List endpoints return Spring Data `Page` objects with `page`, `size`, `totalElements`, and `totalPages`. Default page size is 20; sorting defaults: user search by `username`, user posts and post search by `createdAt DESC`, post comments by server-driven `sortBy` (`NEWEST`/`MOST_LIKED`), followers by `follower.username`, following by `followed.username`, chronological feed by `createdAt DESC`; reacted posts and recommended feed are **unsorted** (ordering by reaction date newest-first and `score desc` server-side respectively, any client `sort` is ignored). Clients can override with standard `page`, `size`, `sort` query parameters except where the ordering is server-driven; recommended forwards `page/size` to the recommendation service (`size` 1–100) and uses its `total` for page metadata.

---

## Database & Migrations

- Schema is managed exclusively by Flyway migrations in `backend/src/main/resources/db/migration/` (`V1` initial schema through `V6` comment count).
- Hibernate is configured with `ddl-auto=validate`, so entity mappings are checked against the migrated schema at startup.
- The full schema is described in [4-Database.md](./4-Database.md).

---

## Configuration & Environment

Required environment variables (see `.env.example` at the repository root):

| Variable | Description |
|----------|-------------|
| `DB_URL` | JDBC URL of the PostgreSQL database |
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password |
| `JWT_SECRET` | JWT signing secret (min 32 bytes) |
| `JWT_EXPIRATION` | Token lifetime in milliseconds |
| `STORAGE_DIR` | Media storage directory (default `uploads`) |
| `RECOMMENDATION_URL` | Base URL of recommendation service (default `http://recommendation:8000`; `http://localhost:8000` locally) |
| `RECOMMENDATION_TIMEOUT_MS` | HTTP timeout for recommendation calls in ms (default `1500`; `500` in tests) |

File uploads are limited to 50 MB per request (`spring.servlet.multipart`).

---

## Testing

The test suite covers:

- **Controller tests** with MockMvc for all controllers including `FeedController` (chronological and recommended: pagination, auth, 404, 405, identical `Page<PostResponse>` shape, sort-ignored) and `PostController` (user posts, post comments with `sortBy`, reacted posts with `filter`, post search, validation, auth, pagination metadata).
- **Service unit tests** for Auth, User, Follow, Post, Reaction, Media, and `Feed` (chronological: rank-independent mapping, view-count recording; recommended: rank-order hydration, empty/exception fallback to chronological, invalid `post_id` skip, deleted filtering, visible-only view increments, `UserNotFound` propagation, total metadata), plus `CustomUserDetailsService`.
- **Repository tests** for post listings (user posts, comments with ordering, reacted posts with reaction-type filtering, content search), view/comment counter increments, and pagination.
- H2 is used as the test database (runtime scope); Flyway migrations are disabled and `recommendation.base-url=http://localhost:8000` is stubbed in `src/test/resources/application.properties`.

Run the tests from the `backend/` directory:

```
./mvnw test
```

---

## Running the Service

The service is containerized. From the repository root:

```
docker compose up --build
```

This starts PostgreSQL, the Core Backend (port `8080`), the Recommendation Service (port `8000`), and the Frontend web client (port `3000`; see [7-Frontend.md](./7-Frontend.md)). A named volume (`uploads`) persists media files across container restarts. Alternatively, run locally with `./mvnw spring-boot:run` after exporting the environment variables above.

---

## Implementation Status

The following components remain **designed but not yet implemented** in the Core Backend:

- **Feed generation:** ✅ Implemented — `GET /api/feed/chronological` (`deletedAt IS NULL ORDER BY createdAt DESC`) and `GET /api/feed/recommended` (`RecommendationClient` → `GET /feed?user_id=&page=&size=` → hydration via `findAllByIdsFiltered` preserving rank order, graceful fallback to chronological on empty/timeout, identical `Page<PostResponse>` shape) with `REQUEST_FEED` logging and `PageableDefault(size=20)`.
- **Recommendation integration:** ✅ Implemented — `RestClientConfig` (`recommendation.base-url` / `RECOMMENDATION_URL`, 1500 ms timeout), `RecommendationClient`/`RecommendationResponse`/`RankedPost`, `docker-compose.yaml` healthcheck on `GET /health`; see [Recommendation Service](./6-Recommendation.md).
- **Monitoring:** Spring Boot Actuator is included as a dependency, but no Prometheus/Grafana stack or metric export is wired up.
- **Redis:** present in `docker-compose.yaml` but unused by the application so far.