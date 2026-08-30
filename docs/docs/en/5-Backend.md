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
- Communication with the Recommendation Service (planned, see [Implementation Status](#implementation-status))

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
controller/   REST endpoints (Auth, User, Follow, Post, Reaction, Media)
service/      Business logic (Auth, User, Follow, Post, Reaction, Media,
              CustomUserDetails, LocalStorage, ObjectStorage interface)
repository/   Spring Data JPA repositories
entity/       JPA entities (User, Post, Media, Follow, Reaction, EventLog)
entity/type/  Enums (PostCategory, EventType, Gender, UserStatus)
dto/          request/ and response/ data transfer objects
security/     SecurityConfig, JwtUtil, JwtAuthFilter, OpenApiConfig
aspect/       LogEvent annotation + EventLoggingAspect
exception/    Custom exceptions + GlobalExceptionHandler
```

Incoming HTTP requests are handled in the controller layer, where authentication, validation, and access control happen. Business logic lives in the service layer, and persistence is handled through the repository layer.

---

## API Reference

All endpoints are prefixed with `/api`. Except where marked **public**, every endpoint requires an `Authorization: Bearer <token>` header and returns `404` / `401` when the JWT is missing or invalid.

### Authentication (`/api/auth`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | public | Registers a new user and returns the created profile together with a JWT token |
| POST | `/api/auth/login` | public | Authenticates the user and returns a JWT token string |

Registration request fields: `username` (≥2 chars), `password` (8–50 chars), `email`, `displayName` (≥2 chars), `gender` (`MALE`, `FEMALE`, `RATHER_NOT_TO_SAY`). Duplicate usernames are rejected with `409 Conflict`. A `LOGIN` event is logged on every successful login; registration performs an automatic login and therefore also produces a `LOGIN` event.

### Users & Profiles (`/api/users`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/users/{userId}` | bearer | Returns the profile of a user; logs `VIEW_PROFILE` |
| GET | `/api/users/me` | bearer | Returns the profile of the authenticated user; logs `VIEW_PROFILE` |
| PUT | `/api/users/me` | bearer | Updates the authenticated user's profile |
| GET | `/api/users?query=` | bearer | Searches users by username or display name (case-insensitive, partial match), paginated |

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
| POST | `/api/posts` | bearer | Creates a post; `201 Created` with a `Location` header; logs `CREATE_POST` |
| PUT | `/api/posts/{postId}` | bearer | Updates the post's content/media; owner only (`403` otherwise) |
| DELETE | `/api/posts/{postId}` | bearer | Soft-deletes the post; owner only |

A post is created from: `postCategory`, `content` (max 280 chars), `mediaId`, `parentId`, `repostOfId`. The category determines which fields are valid:

| Category | Content/Media | `parentId` | `repostOfId` |
|----------|---------------|------------|--------------|
| NORMAL | at least one required | forbidden | forbidden |
| COMMENT | at least one required | required | optional (quote-style comment) |
| QUOTE | at least one required | forbidden | required |
| REPOST | both forbidden | forbidden | required |

Invalid combinations are rejected with `400 Bad Request` (`PostNotValidException`). Post update uses `PUT` semantics so that a field can be explicitly cleared with `null`. Deleting a post sets `deleted_at` and clears the author reference (soft delete).

### Reactions (`/api/posts/{postId}/reactions`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/posts/{postId}/reactions` | bearer | Adds or changes the caller's reaction (`reactionType`: `1` = like, `-1` = dislike); logs `LIKE_POST` |
| GET | `/api/posts/{postId}/reactions` | bearer | Returns like/dislike counts and the caller's current reaction (`0` = none) |
| DELETE | `/api/posts/{postId}/reactions` | bearer | Removes the caller's reaction; `204 No Content` |

A user can have at most one reaction per post (unique constraint on `post_id + user_id`). Adding a reaction of the opposite type switches it, and the post's `like_count` / `dislike_count` counters are adjusted accordingly. The `LIKE_POST` event is recorded for both likes and dislikes in the current implementation; removing a reaction is not logged.

### Media (`/api/media`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/media` | bearer | Uploads a file (`multipart/form-data`, field `file`, max 50 MB); returns `{id, url}` |
| GET | `/api/media/{mediaId}` | bearer | Streams the stored file with its MIME type |
| GET | `/api/media/{mediaId}/metadata` | bearer | Returns metadata: `id`, `size`, `name`, `mimeType`, `createdAt` |

Uploads are content-addressed by SHA-256, so identical content is stored only once.

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
- The `event_logs` schema also includes `session_id` (groups actions of one usage session; unrelated to JWT) and `metadata` (JSONB, for event-specific information). These two fields are part of the schema but are not yet populated by the aspect.

Event types: `VIEW_POST`, `LIKE_POST`, `DISLIKE_POST`, `CREATE_COMMENT`, `REPOST_POST`, `FOLLOW_USER`, `UNFOLLOW_USER`, `VIEW_PROFILE`, `CREATE_POST`, `REQUEST_FEED`, `LOGIN`. `REQUEST_FEED` is defined but not yet produced by any endpoint.

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

List endpoints return Spring Data `Page` objects with `page`, `size`, `totalElements`, and `totalPages`. Default page size is 20; sorting defaults: user search by `username`, followers by `follower.username`, following by `followed.username`. Clients can override with standard `page`, `size`, `sort` query parameters.

---

## Database & Migrations

- Schema is managed exclusively by Flyway migrations in `backend/src/main/resources/db/migration/` (`V1` initial schema through `V5` indexes).
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

File uploads are limited to 50 MB per request (`spring.servlet.multipart`).

---

## Testing

The test suite covers:

- **Controller tests** with MockMvc for all six controllers (happy paths, validation, authorization, not-found cases).
- **Service unit tests** for Auth, User, Follow, Post, Reaction, Media, and `CustomUserDetailsService`.
- H2 is used as the test database (runtime scope); Flyway migrations run against it.

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

This starts PostgreSQL, the Core Backend (port `8080`), and the Recommendation Service (port `8000`). A named volume (`uploads`) persists media files across container restarts. Alternatively, run locally with `./mvnw spring-boot:run` after exporting the environment variables above.

---

## Implementation Status

The following components are **designed but not yet implemented** in the Core Backend:

- **Feed generation:** there is no feed endpoint yet (neither chronological nor smart). The `REQUEST_FEED` event type and the feed design exist, but no `FeedController`/`FeedService` is present.
- **Recommendation integration:** the backend does not yet call the Recommendation Service. The service exists and is running, but the HTTP integration from the backend is not built.
- **Monitoring:** Spring Boot Actuator is included as a dependency, but no Prometheus/Grafana stack or metric export is wired up.
- **Redis:** present in `docker-compose.yaml` but unused by the application so far.