# System Architecture

This document describes the overall structure and architecture of the system. It first presents the high-level architecture and major system components, then examines the internal structure of each subsystem, their interactions, and the flow of data throughout the platform.

The architecture of this project is based on separation of responsibilities. The core business logic, data management, and API layer are implemented using Java and Spring Boot, while data analysis, content ranking, and intelligent processing are implemented in Python. This separation allows each subsystem to evolve independently while preventing computationally intensive analytical workloads from directly affecting the performance of the core application.

---
## High-Level System Architecture

The system consists of several major components, each responsible for a specific set of tasks.

The Core Backend acts as the entry point for all user requests. User management, content management, interactions, authentication, authorization, and feed generation are all handled through this component.

Alongside the Core Backend, an Intelligence Layer is responsible for intelligent data processing. This layer consists of two independent subsystems. The Recommendation System is responsible for content ranking and personalization, while the Analytics Subsystem processes user behavior data and generates statistical insights.

All structured application data is stored in PostgreSQL. Media files such as images and videos are stored on the local filesystem of the Core Backend (a dedicated directory that is exposed as a volume in the containerized deployment), while only their metadata and references are maintained in the database.

A Monitoring Subsystem is also included to collect operational metrics and provide visibility into the runtime behavior of the platform. This subsystem is planned but not yet deployed.

```mermaid
graph TD
    Client(Client / User)
    Frontend(Frontend / Vue SPA)
    LocalFileStorage[(Local File Storage / uploads directory)]
    Monitoring(Monitoring System / planned)

    subgraph System Boundary
        Backend(Core Backend / Spring Boot)
        Recommender(Recommendation Service / Python)
        Analytics(Analytics Subsystem / Python - planned)
        Postgres[(PostgreSQL Database)]
    end

    Client -- Browser --> Frontend
    Frontend -- HTTP REST --> Backend
    Backend -- SQL --> Postgres
    Backend -- Binary Read/Write --> LocalFileStorage
    Recommender -- SQL --> Postgres

    Backend -- HTTP REST --> Recommender
    Backend -. Async Event Flow - planned .-> Analytics
    Backend -. Metrics - planned .-> Monitoring
```

Solid arrows represent implemented connections; dashed arrows represent designed-but-not-yet-implemented connections.

---
## Communication Between Subsystems

All user requests first enter the Core Backend. The backend is responsible for request validation, authentication, business logic execution, data management, and coordination between other subsystems.

Whenever content ranking is required, the backend sends the necessary information to the Recommendation System and receives ranked results. This communication is performed synchronously because the ranking results are directly required to generate the final response returned to the user.

> **Note:** the integration between the Core Backend and the Recommendation Service is now implemented (`GET /api/feed/recommended` → `GET http://recommendation:8000/feed?user_id=&page=&size=` via `RestClient` with graceful fallback). The sequence diagram below depicts the smart-feed flow.

Communication with the Analytics Subsystem is performed asynchronously. User behavior events are recorded during system operation, while analytical processing is executed later in separate workflows. This prevents analytical workloads from increasing request latency for end users.

The Monitoring Subsystem operates outside the primary request-processing path and continuously collects operational metrics from the various services.

![Sequence Diagram](../assets/sequence_diagram.png)

---
## Core Backend

The Core Backend is the central component of the system and is responsible for executing the primary business logic.

All user requests pass through this subsystem, and all major platform operations are coordinated from here.

The backend is implemented using Spring Boot and follows a layered architecture. Incoming HTTP requests are processed in the presentation layer, where authentication, authorization, validation, and access control are performed. Business logic is executed within the service layer, while data persistence and retrieval are handled through the data access layer.

This separation of concerns improves maintainability and allows individual layers to evolve independently with minimal impact on other parts of the system.

The Core Backend is responsible for user management, profiles, posts, comments, user interactions, follow relationships, feed generation, and communication with external subsystems. Its implementation is documented in detail in [5-Backend.md](./5-Backend.md).

When a user creates a new post, the backend first validates the incoming request. If media files are attached, the files are written to the local filesystem (content-addressed by their SHA-256 hash) and only media metadata is stored in PostgreSQL.
When generating a feed, the backend either serves a chronological feed (`GET /api/feed/chronological` with `PageableDefault(size=20, sort=createdAt,DESC)`) or a personalized feed (`GET /api/feed/recommended` with `@AuthenticationPrincipal` forwarding `page/size` to the Recommendation Service and hydrating via `findAllByIdsFiltered` preserving rank order, falling back to chronological on empty/timeout).

---

## Frontend (Web Client)

The Frontend is the web client of the platform — a Vue 3 + TypeScript single-page application (Vite, Pinia, Axios) served by nginx and deployed as the `frontend` service in `docker-compose.yaml` (`3000:80`, proxying same-origin `/api/` to the Core Backend). Unlike the backend and recommendation services, it was written using AI agents against `frontend/Design.md` and the backend API contracts.

Users never call the Core Backend directly in normal use: the browser loads the SPA, which performs login/registration, stores the JWT in `localStorage`, attaches it as `Authorization: Bearer <token>`, and renders feed, posts, profiles, search, and media upload flows. Its implementation is documented in detail in [7-Frontend.md](./7-Frontend.md).

---

## Recommendation System

The Recommendation System is responsible for content personalization and feed ranking and is documented in detail in [6-Recommendation.md](./6-Recommendation.md).

This subsystem is implemented as an independent Python (FastAPI) service and does not manage users or store application data directly. It reads candidate posts directly from the shared PostgreSQL database:

- **Trending posts:** recent posts (last 7 days) with high engagement, ordered by `like_count + view_count` (limit 100)
- **Posts from followings:** recent posts by users the requesting user follows (limit 50)
- **Posts from followers:** recent posts by users that follow the requesting user (limit 50)

Candidates are deduplicated (preserving `trending → following → follower` order), scored, sorted by descending score, and paginated. `GET /feed?user_id=&page=&size=` (health via `GET /health`) returns `{user_id, posts: [{post_id, score}], page, size, total}` with server-side `score desc` order; see [6-Recommendation.md](./6-Recommendation.md) for scoring formula and API details.

> **Note on the contract:** the service currently returns both `post_id` and `score`. The intended contract is that the recommendation service returns **only post IDs** to the Core Backend, and the backend hydrates the full post data from the database before responding. Currently the backend ignores `score` except for order and hydrates via `findAllByIdsFiltered` preserving rank order.

The integration with the Core Backend is implemented: `GET /api/feed/recommended` resolves the authenticated user, calls `GET http://recommendation:8000/feed?user_id=&page=&size=` via `RestClient` (`RestClientConfig` with `recommendation.base-url` / `RECOMMENDATION_URL` and 1500 ms timeout), extracts ranked IDs (skipping invalid strings), hydrates, and builds `Page<PostResponse>` with `total` from recommendation. On empty response, timeout, or any exception it logs `WARN` and falls back to a simple chronological feed without interrupting availability (graceful degradation, detailed in [5-Backend.md](./5-Backend.md)). `GET /health` is polled by `docker-compose.yaml` healthcheck.

Since this subsystem does not maintain persistent internal state, multiple instances can be deployed in parallel in the future to distribute processing load and improve scalability.

---
## Analytics Subsystem

The Analytics Subsystem is responsible for processing user behavior data and generating statistical insights for administrators. It is **planned but not yet implemented** — no analytics service exists in the repository yet.

Unlike the Recommendation System, which operates directly within user-facing workflows, the Analytics Subsystem functions asynchronously and does not affect request response times.

User activities such as content views, likes, comments, and follow actions are recorded during normal system operation in the `event_logs` table. These events are intended to be periodically processed to generate analytical information and system-wide metrics.

The resulting insights can be used for administrative reporting, user behavior analysis, platform activity monitoring, and evaluation of recommendation algorithms.

Separating analytics from the main application ensures that computationally intensive analytical workloads do not impact the day-to-day experience of platform users while allowing both subsystems to evolve independently.

---
## Monitoring Subsystem

Monitoring is not part of the primary request-processing workflow, but it plays a critical role in operating and maintaining the platform. It is **planned but not yet deployed** — the backend includes Spring Boot Actuator, but no Prometheus/Grafana stack or metric export is wired up yet.

All major services expose operational metrics describing their runtime behavior. These metrics include request latency, resource utilization, error rates, service availability, and other technical indicators.

Prometheus is responsible for collecting and storing these metrics, while Grafana is used for visualization, analysis, and dashboard creation.

This subsystem provides visibility into system behavior, helps identify performance bottlenecks, and supports troubleshooting and operational analysis. In the event of failures or abnormal behavior, the collected metrics provide valuable information for diagnosis and root-cause investigation.

---

## Data Storage Layer

The system uses two different storage mechanisms.

Structured data such as users, posts, comments, interactions, and social relationships are stored in PostgreSQL. These entities contain well-defined relationships and require transactional guarantees and integrity constraints that are best provided by a relational database system.

Media assets such as images and videos are stored on the **local filesystem** of the Core Backend (object storage is not used in the current implementation). Files are addressed by the SHA-256 hash of their content, which deduplicates identical uploads and keeps the storage simple. Only metadata — size, MIME type, hash, and owner — is maintained in the database.

As a result, each category of data is stored in the environment most suitable for its characteristics, improving both system performance and maintainability.

---

## Implementation Status

| Component | Status |
|-----------|--------|
| Core Backend (users, auth, posts, reactions, follows, media) | Implemented |
| Frontend web client (auth, feed tabs, posts, reactions, profiles, search, media) | Implemented (AI-generated; topics/news content and repost/quote/comment still placeholders — see [7-Frontend.md](./7-Frontend.md)) |
| Media storage on local filesystem | Implemented |
| Event logging (`event_logs`) | Implemented — `REQUEST_FEED` with `metadata {feed_type, page, size, total_elements}` |
| Feed generation (chronological / smart feed) | Implemented — `GET /api/feed/chronological` and `GET /api/feed/recommended` with `Page<PostResponse>` and graceful fallback |
| Backend ↔ Recommendation integration | Implemented — `RestClient` (`recommendation.base-url` / `RECOMMENDATION_URL`, 1500 ms timeout), `RecommendationClient` → `GET /feed?user_id=&page=&size=`, hydration via `findAllByIdsFiltered`, healthcheck on `GET /health` |
| Analytics subsystem | Planned — not started |
| Monitoring (Prometheus / Grafana) | Planned — only actuator dependency present |
| Redis (caching / rate limiting) | Planned — container present, unused |