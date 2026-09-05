# Frontend (Web Client)

This document describes the implementation of the Frontend service — the web client of the Sarv platform and the primary user-facing entry point to the system.

It covers the technology stack, project structure, routing and layout, authentication and session handling, the API layer, views and components, state management, styling and visual language, error handling, testing, configuration and deployment. It reflects the **current state of the implementation**; parts that are designed but not yet wired to real data are listed at the end under [Implementation Status](#implementation-status).

> **Note on authorship:** unlike the Core Backend and Recommendation Service, which were written by hand, this service was **written using AI agents** (agentic code generation) against the `frontend/Design.md` specification and the backend API contracts in [5-Backend.md](./5-Backend.md). The design document defines *how things look*; this document describes *what was actually built*.

---

## Overview

The Frontend is a single-page application (SPA) written in TypeScript with Vue 3 and Vite. It is responsible for:

- Login and two-step registration (JWT session, password confirmation)
- Post feed with **For You** (recommended) and **Latest** (chronological) tabs
- Posts: detail view with comments thread, creating (normal/comment/quote/repost, content and/or media), like/dislike with animated feedback
- Category identity: terminal-blue banners and embedded originals for comment/repost/quote posts
- Profiles: viewing, follow stats, own-posts list, editing own profile, per-user followers/following lists, reacted-posts history (liked/disliked/all filter)
- Search in a centered modal: combined general, username/display-name, and post-content tabs
- Media upload (with progress) and media/profile-picture rendering, incl. expandable media on embedded originals
- Session handling: token storage, auth guards, expired-session redirect

All data comes from the Core Backend REST API (`/api`, see [5-Backend.md](./5-Backend.md)). The frontend keeps no business state of its own — the backend is the source of truth for users, posts, reactions, follows, and media. The visual language (square geometry, green Matrix/hacker identity, heavy animation) is defined by `frontend/Design.md`.

---

## Technology Stack

| Concern | Technology |
|---------|------------|
| Language | TypeScript (strict, `vue-tsc`) |
| Framework | Vue 3.5 (Composition API, `<script setup>`, SFC) |
| Build / Dev | Vite 8 (`@vitejs/plugin-vue`, `vite-plugin-vue-devtools`) |
| Routing | `vue-router` 5 (history mode, lazy-loaded views, navigation guards) |
| State | `pinia` 4 (auth store only; everything else is local component state) |
| HTTP | `axios` 1.x (shared `apiClient` with interceptors) |
| Animation | `gsap` 3 (modal transitions, upload progress, logo/feedback effects) |
| Utilities | `@vueuse/core`, `@iconify/vue` + local pixel-icon set (`assets/icons/pixelarticons.ts`) |
| Tests | Vitest 4 + `jsdom` + `@vue/test-utils` (unit), Playwright 1.x (e2e, chromium/firefox/webkit) |
| Lint / Format | `oxlint` + `eslint` (+ `eslint-plugin-vue`, `@vue/eslint-config-typescript`), `prettier` |
| Production serving | `nginx:1.27-alpine` (SPA fallback + `/api/` reverse proxy, see [Configuration & Deployment](#configuration-deployment)) |

---

## Project Structure

Source root is `frontend/src/`:

```
api/          HTTP layer — one module per domain (client, auth, users, posts,
              feed, follows, reactions, media)
router/       Route table + auth guards (index.ts)
stores/       Pinia stores (auth.ts — the only shared store)
types/        Backend-mirroring types (api.ts: User/Post/Reaction/Media, Page)
utils/        token.ts (localStorage helpers)
views/        Route-level screens (AppShell, Feed, PostDetail, Login, Register,
              Profile, LikedPosts, Following, Followers)
components/   Reusable UI (LeftSidebar, RightSidebar, PostCard, PostCreateModal,
              RepostConfirm, SearchSection, SearchModal, UserSummary/List,
              NavigationMenu, SarvLogo, HotTopicsPanel, PlatformNewsPanel,
              AmbientNetwork, MobileTopBar, MobileBottomNav, AppIcon)
assets/       main.css (design tokens + base styles), icons/pixelarticons.ts
__tests__/    Unit tests for views/stores/router; components/__tests__/ for components
App.vue       Root (<router-view> + session rehydration)
main.ts       Bootstrap (pinia, router, session-expired hook, icons, css)
```

Top-level config: `vite.config.ts` (alias `@`, dev `/api` proxy), `vitest.config.ts` (jsdom, excludes `e2e/`), `playwright.config.ts` (dev/preview server, 3 browsers), `nginx.conf`, `Dockerfile`, `e2e/` specs.

---

## Routing & Layout

Route table (`router/index.ts:5`):

| Path | Name | Component | Access |
|------|------|-----------|--------|
| `/login` | `login` | `LoginView.vue` | public |
| `/register` | `register` | `RegisterView.vue` | public |
| `/` | `feed` (child `''`) | `AppShell.vue` → `FeedView.vue` | auth |
| `/post/:id` | `post-detail` | `PostDetailView.vue` | auth |
| `/profile/:id?` | `profile` | `ProfileView.vue` | auth (`:id?` omitted = self) |
| `/liked` | `liked` | `LikedPostsView.vue` | auth |
| `/following/:id?` | `following` | `FollowingView.vue` | auth (`:id?` omitted = self) |
| `/followers/:id?` | `followers` | `FollowersView.vue` | auth (`:id?` omitted = self) |
| `/:pathMatch(.*)*` | — | redirect → `login` | — |

Navigation guard (`router/index.ts:36`):

1. On an auth route with a token but no loaded user → `fetchMe()`; on failure `logout()` and redirect to `login`.
2. On an auth route without a token → redirect to `login`, preserving `?redirect=` (except for `/`).
3. On `login` while authenticated → redirect to `feed`.

Layout (`views/AppShell.vue:116`): authenticated shell is a 3-column grid — `LeftSidebar | router-view (center, largest) | RightSidebar` (`grid-template-columns: 300px minmax(0, 1fr) 320px`, narrowing at 1100 px). Center is the scroll container. Below 900 px the right sidebar becomes a slide-in drawer; below 640 px the left sidebar does too (scrim + `Escape` closes), with `MobileTopBar` on top and `MobileBottomNav` at the bottom. New posts bump a provided `feedRefreshKey` so `FeedView` re-fetches (`AppShell.vue:11`).

---

## Authentication & Session

Flow (`stores/auth.ts:9`, `api/auth.ts:17`, `utils/token.ts:1`):

1. `POST /api/auth/login` returns a raw JWT string; `POST /api/auth/register` returns `{..., token}`. The store saves it to `localStorage` under `sarv.jwt` and calls `GET /api/users/me` (`fetchMe`) to populate `user`.
2. `isAuthenticated` is derived from token presence only (`stores/auth.ts:13`).
3. Every request carries `Authorization: Bearer <token>` via the `apiClient` request interceptor (`api/client.ts:25`).
4. A missing/invalid/expired JWT yields `403` with an empty body from Spring Security. The response interceptor (`api/client.ts:33`) treats that as session expiry: clears the token and fires the `onSessionExpired` hook wired in `main.ts:19`, which logs out and pushes to `login`.
5. `App.vue:8` rehydrates the session on reload (`fetchMe` if a token exists without a user).

Login UI (`views/LoginView.vue`): centered box, username + password, `401` → "Invalid username or password", otherwise backend `detail`; success honors `?redirect=` or goes to `feed`.

Registration UI (`views/RegisterView.vue`, two steps per `Design.md §§12`):

- **Step 1 (mandatory):** `username`, `password` (≥ 8 chars, client-checked), `confirmPassword` (must match, client-checked; the backend rejects mismatches with 400), `email`, `displayName`, `gender` → `auth.register()` → advances to step 2.
- **Step 2 (optional):** `bio` (≤ 255), `location` (≤ 30), profile picture (`accept="image/*"` via a styled picker with live thumbnail preview, not the native file control). If anything was provided, the picture is uploaded first (`POST /api/media`) and `PUT /api/users/me` saves the profile; otherwise nothing is sent. **Skip** goes straight to the feed.

---

## API Layer

`api/client.ts:21` creates `axios` with `baseURL: '/api'` (same-origin; proxied to the backend in dev and prod, so no CORS or frontend env vars). Failures are normalized to `ApiError { status, title, detail, instance }` from the backend RFC 9457 `ProblemDetail` (`types/api.ts:90`).

| Module | Functions | Backend endpoints |
|--------|-----------|-------------------|
| `api/auth.ts` | `login`, `register` | `POST /api/auth/login`, `POST /api/auth/register` |
| `api/users.ts` | `getMe`, `getUser`, `updateMe`, `searchUsers(query, pageable)`, `getUserPosts`, `getReactedPosts(filter)`, `getUserStats` | `GET /api/users/me`, `GET /api/users/{id}`, `PUT /api/users/me`, `GET /api/users?query=`, `GET /api/users/{id}/posts`, `GET /api/users/{id}/reacted-posts?filter=`, `GET /api/users/{id}/stats` |
| `api/feed.ts` | `getChronologicalFeed`, `getRecommendedFeed` | `GET /api/feed/chronological`, `GET /api/feed/recommended` |
| `api/posts.ts` | `getPost`, `createPost`, `updatePost`, `deletePost`, `searchPosts`, `getComments`, `repostPost`, `quotePost` | `GET/POST /api/posts`, `PUT/DELETE /api/posts/{id}`, `GET /api/posts/search?query=`, `GET /api/posts/{id}/comments?sortBy=`, repost/quote via `POST /api/posts` |
| `api/reactions.ts` | `addReaction(1\|-1)`, `getReaction`, `removeReaction` | `POST/GET/DELETE /api/posts/{id}/reactions` |
| `api/follows.ts` | `getFollowers`, `getFollowing`, `follow`, `unfollow` | `GET/POST/DELETE /api/users/{id}/followers`, `GET /api/users/{id}/following` |
| `api/media.ts` | `uploadMedia(file, onProgress?)`, `getMediaBlob`, `getMediaMetadata` | `POST /api/media` (multipart `file`), `GET /api/media/{id}`, `GET /api/media/{id}/metadata` |

Types in `types/api.ts:4` mirror the backend field-for-field (`Gender`, `UserStatus`, `PostCategory`, `CommentSort`, `ReactionFilter`, `UserResponse`, `UserSummaryResponse`, `UserStatsResponse`, `PostResponse` incl. `commentCount`, `ReactionResponse`, `MediaResponse`, `Page<T>` with `page { size, number, totalElements, totalPages }`).

### Feed behavior

`FeedView.vue:36` exposes **For You** (default) and **Latest** tabs (`size = 20`, `load more` pagination from `page.totalPages`):

- **Latest** → `GET /api/feed/chronological` directly.
- **For You** → `GET /api/feed/recommended`; on empty first page or on request failure the view **falls back to chronological itself** (`FeedView.vue:50`) — in addition to the backend's own graceful degradation (see [5-Backend.md](./5-Backend.md)). Rapid tab switches are guarded by a sequence counter so stale responses are ignored.

### Post creation (media first)

`PostCreateModal.vue:34` enforces the `Design.md §8` workflow as explicit phases (`idle → uploading → uploaded → publishing`, plus `error`):

1. User picks content and/or a file (preview via object URL).
2. **Upload first:** `⇪ upload media` calls `POST /api/media` with a progress callback driving a pixel-striped GSAP progress bar (`media.ts:8`, `PostCreateModal.vue:80`).
3. **Then submit:** `createPost({ postCategory: 'NORMAL', content, mediaId, parentId: null, repostOfId: null })`. Submit is disabled until content or an uploaded `mediaId` exists.

The composer has three modes (`PostCreateModal.vue:12`, same window, same media-first flow):

- **post** (default): `NORMAL` post as above.
- **comment** (`parentId` set, opened from the post detail): `COMMENT` with `parentId`, no `repostOfId`.
- **quote** (`repostOfId` set, opened from a card's quote button): non-interactive preview of the quoted post on top, then content (required — the backend rejects empty quotes) plus optional media via the `quotePost` helper.

### Post detail & comments

`PostDetailView.vue` renders the post via `PostCard` in `detailed` mode, then the comments thread underneath: `GET /api/posts/{id}/comments?sortBy=` with a **newest** (default) / **most liked** toggle, paginated `load more` (`size = 10`), an empty state inviting the first comment, and a write-a-comment button opening the composer in comment mode (reloads post + comments on publish). Clicking a feed card (or its comment button) navigates here; like/dislike, author link, and media stop propagation.

### Repost

The card's repost button opens a `RepostConfirm.vue` window (quoted snippet, `repost`/`cancel`, `Escape`/backdrop closes) and confirming sends `postCategory: 'REPOST'` with null content/media via `repostPost`. Success marks the button in terminal blue and emits `reposted` so the feed refreshes; failures stay in-window with retry.

### Reactions, follows, profiles, media rendering

- `PostCard.vue:73` loads per-post reaction state (`likeCount/dislikeCount/userReaction`), author profile, avatar blob, and post media blob on mount; like = thumbs-up (green when active), dislike = thumbs-down (red when active), with pixelated smile/sad GSAP feedback after success (per `Design.md §7.3`). Author header navigates to `profile/:userId`; card body and comment button navigate to `post-detail`.
- Category identity: `COMMENT`/`REPOST`/`QUOTE` posts render a terminal-blue (`--sarv-blue`) banner strip on top of the card linking to the parent/referenced post; repost/quote cards embed a one-level preview of the original (author, snippet, counts) with an `original post unavailable` fallback, plus a `show attached media` toggle that loads and shows the original's image/video inline when it has media.
- `ProfileView.vue:42`: `:id?` omitted resolves to self; follow state is derived from the first page of the viewer's own following list (the API has no `isFollowing` field). The header shows follow stats (`GET /api/users/{id}/stats`) linking to that user's followers/following lists, followed by a paginated own-posts list (`GET /api/users/{id}/posts`, reusing `PostCard`). Self profiles get an edit form (`displayName`, `bio`, `location`, `gender`, avatar via a styled picker with live preview → `updateMe`); only these fields are editable.
- `LikedPostsView.vue` is the reacted-posts history (`GET /api/users/{id}/reacted-posts`) with **liked** (default) / **disliked** / **all** filter tabs; the nav item is labeled "recent reactions".
- Avatars and post media are fetched as blobs (`GET /api/media/{id}`) and rendered via `URL.createObjectURL`, revoked on change/unmount.

---

## Views & Components

| Screen | File | Notes |
|--------|------|-------|
| Shell | `views/AppShell.vue` | 3-column layout, mobile drawers, create-post modal host |
| Feed | `views/FeedView.vue` | For You / Latest tabs, retry + empty + load-more states |
| Post detail | `views/PostDetailView.vue` | Post + comments thread with sort toggle, comment composer |
| Login / Register | `views/LoginView.vue`, `views/RegisterView.vue` | Centered auth boxes |
| Profile | `views/ProfileView.vue` | Stats, own posts, view + self-edit, follow/unfollow |
| Reacted / Following / Followers | `views/LikedPostsView.vue`, `views/FollowingView.vue`, `views/FollowersView.vue` | Filterable reacted-posts feed; per-user follow lists |

| Component | Role (Design.md ref) |
|-----------|----------------------|
| `LeftSidebar.vue` + `SearchSection.vue`, `SearchModal.vue`, `UserSummary.vue`, `NavigationMenu.vue` | Search triggers + centered results modal, user summary, create-post action, home/profile/reactions/following/followers nav (§4) |
| `PostCard.vue`, `PostCreateModal.vue`, `RepostConfirm.vue` | Feed posts, banners, previews, counts, actions (§7); same-page creation/comment/quote windows and repost confirmation (§8) |
| `RightSidebar.vue` + `SarvLogo.vue`, `HotTopicsPanel.vue`, `PlatformNewsPanel.vue` | Animated Sarv name, hottest topics, platform news (§9) |
| `UserSummaryList.vue` | Shared avatar/username/displayName rows, identity click-through to profiles (§6) |
| `MobileTopBar.vue`, `MobileBottomNav.vue`, `AmbientNetwork.vue`, `AppIcon.vue` | Responsive chrome, background effect, thumbs/search/user/home/image icons |

The brand block at the top of the left sidebar (logo + tagline) navigates to the feed, as does the highlighted `home` nav item with its icon.

Search (`SearchModal.vue`, opened from the sidebar input/tabs): a window fixed over the center of the screen with the same three tabs — **username** (`GET /api/users?query=`, top 8, click → profile), **post** (`GET /api/posts/search?query=`, mini rows, click → post detail), and **general** combining both with see-all shortcuts. Debounced 300 ms, `Escape`/backdrop/close-button dismiss, avatars for account rows.

Right sidebar data (`HotTopicsPanel.vue:7`, `PlatformNewsPanel.vue:8`): currently **static placeholder lists** (5 themed tags, 3 release entries) — no analytics/topics API exists yet. Clicking them does nothing, per the "unimplemented controls do nothing" rule (`Design.md §13`).

---

## State Management

Only one shared store exists: `useAuthStore` (`stores/auth.ts:9` — `token`, `user`, `isAuthenticated`, `login/register/logout/fetchMe`). Everything else (feed pages, search results, modals, forms, follow state) is local `ref` state inside views/components, passed via props/emits or the `feedRefreshKey` injection. JWT persistence is a thin `localStorage` wrapper (`utils/token.ts:1`, key `sarv.jwt`) — no refresh tokens or expiry tracking client-side.

---

## Styling & Visual Language

`assets/main.css` defines the Sarv design tokens (`--sarv-green`, `--sarv-blue` for post-category identity, `--sarv-panel/bg/border`, `--sarv-glow`, spacing scale) consumed by all scoped component styles. The implementation follows `frontend/Design.md` constraints: sharp square geometry (no pill/rounded cards), green-on-dark terminal aesthetic, pixel-art icons (`assets/icons/pixelarticons.ts`, registered in `main.ts:11`), and GSAP-driven motion (logo construction, modal open/close, like/dislike feedback, upload scan bar). Responsive strategy preserves the center-feed hierarchy: sidebars collapse to drawers instead of squeezing the feed (`AppShell.vue:173`, `Design.md §14`).

---

## Error Handling & UX

- Backend `ProblemDetail` → `ApiError.detail` is shown inline (auth forms, feed retry, composer status); unknown/network failures fall back to generic messages.
- Feed: full-page error with `retry` when empty, inline error + preserved list when paginating (`FeedView.vue:139`).
- Composer: upload and publish failures set `phase: 'error'` with a message and block submit until reset.
- Missing images/media fail silently to icon/empty state (avatar/media loads catch and clear).
- Unimplemented UI (static sidebar rows) renders but performs no action — no mock backend behavior is invented.

---

## Testing

| Suite | Scope | Command |
|-------|-------|---------|
| Unit (Vitest, jsdom) | `src/__tests__/` (auth store, router guards, Login/Register/Profile/Feed/PostDetail/Liked/shell/sidebar/mobile) + `src/components/__tests__/` (PostCard, PostCreateModal, RepostConfirm, AppIcon, UserSummaryList) | `npm run test:unit` |
| E2E (Playwright) | `e2e/vue.spec.ts`, `e2e/mobile.spec.ts`, `e2e/sticky-tabs.spec.ts` (chromium, firefox, webkit; dev server locally, preview on CI) | `npm run test:e2e` |
| Type-check | `vue-tsc --build` (part of `npm run build`) | `npm run type-check` |
| Lint | `oxlint` + `eslint` (Vue + TS rules) | `npm run lint` |

---

## Configuration & Deployment

No frontend environment variables are required — the app talks to same-origin `/api`:

- **Dev:** `vite.config.ts:20` proxies `/api → http://localhost:8080` (backend on host). `npm install` → `npm run dev` (5173).
- **Prod:** multi-stage `Dockerfile` (`node:24-alpine` build → `nginx:1.27-alpine` runtime serving `dist/`). `nginx.conf:20` proxies `/api/ → http://core_backend:8080` with SPA fallback (`try_files … /index.html`), `gzip`, and immutable caching for hashed assets (`index.html` never cached).
- **Compose:** `docker-compose.yaml` builds `frontend` (`3000:80`, `depends_on: core_backend`); backend stays on `8080`, recommendation on `8000`.

```
docker compose up --build
# frontend → http://localhost:3000, backend → http://localhost:8080
```

---

## Implementation Status

**Implemented:** auth (login, 2-step register incl. password confirmation and optional avatar/bio/location), guards + session expiry, For You/Latest feed with client fallback, post create (media-first + progress, normal/comment/quote modes), post detail with comments thread + sorting + composer, repost with confirmation, category banners + embedded originals with media toggle, like/dislike with counts (incl. comment counts) + feedback, profile view/edit with follow stats + own posts, per-user followers/following lists, reacted-posts history with filter, full three-tab search modal, avatar/media blob rendering, styled picture pickers, responsive shell + drawers, unit + e2e suites, Docker/nginx deployment.

**Designed but not yet wired (UI exists, does nothing or shows placeholders):**

- Hot topics and platform news content (static lists in `HotTopicsPanel.vue:7`, `PlatformNewsPanel.vue:8` — analytics/topics API is planned, not built).
- Post edit/delete from the UI (API wrappers exist in `api/posts.ts:27`, no UI controls yet).

See also [5-Backend.md](./5-Backend.md), [3-Architecture.md](./3-Architecture.md), and [6-Recommendation.md](./6-Recommendation.md).
