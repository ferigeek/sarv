# سرویس توصیه‌گر (Recommendation Service)

این سند سرویس توصیه‌گر را شرح می‌دهد — سرویس مستقل Python (FastAPI) که مسئول رتبه‌بندی محتوا و تولید فید شخصی‌سازی‌شده است.

شامل هدف، راه‌اندازی سریع، پیکربندی، قرارداد API، تولید کاندید، امتیازدهی، صفحه‌بندی، یکپارچه‌سازی با هسته مرکزی و استقرار است و **وضعیت فعلی پیاده‌سازی** را بازتاب می‌دهد.

---

## نمای کلی

سرویس توصیه‌گر کاربران را مدیریت نمی‌کند و داده برنامه را ذخیره نمی‌کند. کاندیدها را مستقیماً از پایگاه داده مشترک PostgreSQL می‌خواند، رتبه‌بندی کرده و لیست صفحه‌بندی‌شده `post_id` + `score` را به بک‌اند بازمی‌گرداند. بک‌اند از طریق `findAllByIdsFiltered` و با حفظ ترتیب رتبه، موجودیت‌های کامل `Post` را هیدراته و `Page<PostResponse>` می‌سازد (به [5-Backend.md](./5-Backend.md) مراجعه کنید).

ویژگی‌های کلیدی:

- بدون حالت — قابل مقیاس افقی (چند نمونه پشت Load Balancer)
- همزمان — رتبه‌بندی در مسیر بحرانی `GET /api/feed/recommended` است اما با تخریب مهربانانه به زمانی برمی‌گردد
- پایگاه داده مشترک — خواندن جداول `posts` و `follows` (بدون نوشتن)

---

## راه‌اندازی سریع

**محلی (بدون Docker):**

```bash
cd intelligence/recommendation
uv sync
# فایل .env با متغیرهای DB_* (یا export)
uvicorn main:app --reload --port 8000
curl "http://localhost:8000/feed?user_id=1&page=0&size=5"
curl http://localhost:8000/health
```

**Docker:**

```bash
docker compose up --build recommendation
# یا کل استک
docker compose up --build
curl "http://localhost:8000/feed?user_id=1&page=0&size=5"
```

Swagger UI در `http://localhost:8000/docs` (خودکار FastAPI) در دسترس است.

---

## پیکربندی و متغیرهای محیطی

سرویس از `pydantic-settings` (`database.py:7` `BaseSettings` با `env_prefix="DB_"` و `env_file=".env"`) استفاده می‌کند:

| متغیر | توضیح | پیش‌فرض | نمونه |
|----------|-------------|---------|---------|
| `DB_NAME` | نام پایگاه داده PostgreSQL | — | `sarv` |
| `DB_USERNAME` | کاربر DB | — | `postgres` |
| `DB_PASSWORD` | رمز DB | — | `your-password` |
| `DB_HOST` | هاست DB | `localhost` | `postgres` (در `docker-compose.yaml`) |
| `DB_PORT` | پورت DB | `5432` | `5432` |
| `DB_POOL_MIN` | کف استخر اتصال | `2` | `2` |
| `DB_POOL_MAX` | سقف استخر اتصال | `10` | `10` |
| `DB_POOL_TIMEOUT` | تایم‌اوت اتصال (ثانیه) | `5` | `5` |
| `REDIS_URL` | ردیس کش فید | `redis://localhost:6379` | `redis://redis:6379` (در `docker-compose.yaml`) |
| `FEED_CACHE_TTL_SECONDS` | ماندگاری صفحه کش | `45` | `45` |

`docker-compose.yaml` مقادیر `DB_HOST=postgres`، `DB_PORT=5432`، اندازه استخر و `REDIS_URL=redis://redis:6379` را تنظیم می‌کند. استخر در `lifespan` برنامه باز می‌شود؛ ردیس اختیاری است — خرابی آن با `WARN` به DB مستقیم برمی‌گردد.

اتصال بک‌اند جداگانه از طریق `RECOMMENDATION_URL` پیکربندی می‌شود (به [5-Backend.md](./5-Backend.md) مراجعه کنید).

---

## مرجع API

### `GET /health`

بررسی سلامت برای `docker-compose.yaml` (`interval 10s, timeout 3s, retries 3, start_period 10s` — `python -c "urllib.request.urlopen('http://localhost:8000/health')"`).

- **احراز هویت:** ندارد (شبکه داخلی)
- **پاسخ `200`:**
```json
{ "status": "ok", "model": "heuristic-v1" }
```

### `GET /feed`

شناسه پست‌های رتبه‌بندی‌شده همراه با امتیاز را به‌صورت صفحه‌بندی‌شده سمت سرور برمی‌گرداند. توسط `RecommendationClient.java:26` به‌صورت `GET /feed?user_id=&page=&size=` فراخوانی می‌شود.

**پارامترهای کوئری:**

| پارامتر | نوع | الزامی | اعتبارسنجی | توضیح |
|-------|------|----------|------------|-------------|
| `user_id` | string | بله | — | `users.id` به‌صورت رشته (`Long` از `userRepository.findByUsername → getId` در اسپرینگ) |
| `page` | int | خیر | `ge=0` | شماره صفحه مبتنی بر صفر (پیش‌فرض `0`) |
| `size` | int | خیر | `ge=1, le=100` | اندازه صفحه (پیش‌فرض `20`) |

خطای اعتبارسنجی `422 Unprocessable Entity` (خودکار FastAPI) برمی‌گرداند.

**پاسخ `200`:**

```json
{
  "user_id": "42",
  "posts": [
    { "post_id": "123", "score": 12.3 },
    { "post_id": "87", "score": 8.1 }
  ],
  "page": 0,
  "size": 20,
  "total": 87
}
```

- `posts` مرتب `score desc`؛ صفحه‌بندی `paged = ranked[page*size : page*size+size]`؛ صفحه خارج از محدوده `[]` برمی‌گرداند نه خطا.
- `total` = تعداد قبل از صفحه‌بندی (توسط بک‌اند به‌عنوان `Page` `totalElements` در `FeedService.java:65` استفاده می‌شود).
- مرتب‌سازی همیشه `score desc` است — هر `sort` کلاینت در `GET /api/feed/recommended` نادیده گرفته می‌شود (تبدیل به `PageRequest` بدون مرتب‌سازی در `FeedController.java:42`).

**نمونه:**

```bash
curl "http://localhost:8000/feed?user_id=42&page=1&size=10"
```

> **نکته درباره قرارداد:** سرویس فعلاً هم `post_id` و هم `score` را برمی‌گرداند. قرارداد نهایی فقط `post IDs` است؛ بک‌اند فعلاً `score` را جز برای ترتیب نادیده گرفته و از طریق `findAllByIdsFiltered` هیدراته می‌کند. به [3-Architecture.md](./3-Architecture.md) مراجعه کنید.

**خطاها:**
`422` برای `page/size` نامعتبر، `500` برای خطای DB (بدون هندلر سفارشی — پیش‌فرض FastAPI).

---

## تولید کاندید

`CandidateGenerator(user_id).generate_candidates()` (`candidate.py`) سه منبع (`search_span_days=7`) را با یک `_fetch` ناهمگام ترکیب و با اولویت نسخه پرچم‌دار حذف تکراری می‌کند (ترتیب `trending → following → follower`).

همه کوئری‌ها روی `deleted_at IS NULL AND type='NORMAL' AND created_at >= now - 7d` (UTC) فیلتر می‌کنند:

**۱. پست‌های داغ (`_get_trending_posts`, سقف ۱۰۰):**
```sql
SELECT id, like_count, dislike_count, view_count, created_at
FROM posts
WHERE deleted_at IS NULL AND created_at >= %s AND type='NORMAL'
ORDER BY (like_count + view_count) DESC, created_at DESC
LIMIT 100
```
نکته: مرتب بر اساس `like_count + view_count` (بدون `dislike_count`؛ امتیازدهی آن را جریمه می‌کند).

**۲. دنبال‌شونده‌ها (`_get_following_posts`, سقف ۵۰):**
```sql
SELECT p.id, p.like_count, p.dislike_count, p.view_count, p.created_at
FROM posts p JOIN follows f ON p.user_id = f.followed_id
WHERE f.follower_id = %s AND p.deleted_at IS NULL AND p.created_at >= %s AND p.type='NORMAL'
ORDER BY p.created_at DESC LIMIT 50
```
`from_followed=True` روی `PostFeatures`.

**۳. دنبال‌کننده‌ها (`_get_follower_posts`, سقف ۵۰):**
```sql
SELECT p.id, p.like_count, p.dislike_count, p.view_count, p.created_at
FROM posts p JOIN follows f ON p.user_id = f.follower_id
WHERE f.followed_id = %s AND ...
ORDER BY p.created_at DESC LIMIT 50
```
نیز `from_followed=False` (بدون تقویت؛ تقویت فقط برای دنبال‌شونده‌ها).

حداکثر کاندید خام: `100 + 50 + 50 = 200` قبل از حذف تکراری. اتصال‌ها از `AsyncConnectionPool` مشترک در `lifespan` برنامه می‌آیند (`DB_POOL_MIN/MAX/TIMEOUT`). تأخیر و تعداد هر منبع با `feed_db_query_seconds{query}` و `feed_candidates_count{source}` ثبت می‌شود.

پشتیبانی با `V9__add_recommendation_indexes.sql`: `idx_posts_trending` جزئی برای مرتب‌سازی تعامل و `idx_posts_user_type_created_at` جزئی برای تایم‌لاین‌های دنبال‌شونده/دنبال‌کننده.

---

## امتیازدهی

کلاس `PostFeatures` (`scoring.py`):

```python
post_id: str
like_count: int
dislike_count: int
view_count: int
created_at: datetime
from_followed: bool = False
author_id: str = ""
comment_count: int = 0
author_affinity: float = 0.0
user_boost: float = 1.0
```

`score_post(features, now=None)` (`heuristic-v1`):

```python
engagement = 2 * like_count + view_count + 3 * comment_count - 2 * dislike_count
engagement = max(engagement, 0)

age_hours = max((now - created_at).total_seconds() / 3600, 0)
recency_boost = 1 / (1 + age_hours / 48)  # نیمه‌عمر حدود 48 ساعت

follow_boost = 1.5 if from_followed else 1.0
affinity_boost = 1 + min(max(author_affinity, 0), 10) * 0.1  # ۱ تا ۲

return engagement * recency_boost * follow_boost * affinity_boost * user_boost
```

- تعامل با وزن `2×` لایک/دیسلایک، `3×` کامنت، `1×` بازدید، کف ۰.
- پوسیدگی زمانی `1/(1+t/48h)` → ۵۰٪ در ۲ روز، ۳۳٪ در ۴ روز.
- تقویت دنبال‌شدن `1.5×`؛ تقویت تعامل با نویسنده `1.0` (سرد) تا `2.0` (سقف ۱۰ امتیاز).
- `user_boost` در `0.9 تا 1.1` از نرخ لایک/کامنت کاربر، `1.0` برای کاربران جدید.

*نمونه:* ۱۰ لایک، ۱۰۰ بازدید، ۲ کامنت، ۱ دیسلایک، ۱۲ ساعت عمر، از دنبال‌شونده، تعامل ۵ → `(20+100+6-2)*0.8*1.5*1.5 ≈ 223.2`.

## سیگنال‌های رفتاری

دو کوئری اضافه در هر `/feed` (همان اتصال، با ایندکس‌های موجود، زمان‌سنجی `affinity`/`engagement`):

- **تعامل با نویسنده** (`_get_author_affinity`، بازه ۳۰ روزه): `event_logs JOIN posts` گروه‌بندی‌شده بر نویسنده با وزن‌ها `VIEW=1, LIKE=3, COMMENT=4, REPOST/QUOTE=5, DISLIKE=-2` (فقط بازدید ساده؛ dwell موکول شد). نویسنده ناآشنا `0`.
- **سطح تعامل کاربر** (`_get_user_boost`، بازه ۳۰ روزه): یک تجمیع `VIEW/LIKE/COMMENT` → ضریب نرخ `engagement_boost`.
- ایندکس `V10 (user_id, type, created_at)` موکول شد — ابتدا `EXPLAIN` روی داده واقعی.

`ranked = sorted(candidates, key=score_post, reverse=True)` ترتیب نهایی را تعیین می‌کند.

---

## کش

کش read-through ردیس (`cache.py`): کلید `feed:{MODEL_VERSION}:user:{id}:page:{p}:size:{s}`، مقدار `{posts, total}`، ماندگاری `FEED_CACHE_TTL_SECONDS` (۴۵ ثانیه). کلید از `MODEL_VERSION` مشتق می‌شود، پس تغییر فرمول صفحات قدیمی را خودکار بازنشسته می‌کند. هیت DB را دور می‌زند؛ هر خطا با `WARN` به DB مستقیم برمی‌گردد. با `feed_cache_events_total{outcome}` ثبت می‌شود.

## صفحه‌بندی و قرارداد

- `page` مبتنی بر صفر، `size` ۱ تا ۱۰۰، اعتبارسنجی توسط FastAPI `Query`.
- `total` تعداد قبل از صفحه‌بندی است؛ بک‌اند آن را به‌عنوان `Page.totalElements` (`FeedService.java:65`) استفاده کرده و ترتیب رتبه را حتی پس از فیلتر `post_id` نامعتبر یا ردیف حذف‌شده/یافت‌نشده حفظ می‌کند (ممکن است در صفحه آخر حفره ایجاد شود).
- صفحه خارج از محدوده `posts: []` با همان `total` برمی‌گرداند.
- قرارداد مورد نظر فقط شناسه‌ها است؛ `score` فعلی برای اشکال‌زدایی است و توسط بک‌اند جز برای مرتب‌سازی نادیده گرفته می‌شود.

---

## متریک‌ها

علاوه بر هیستوگرام‌های پیش‌فرض instrumentator (`metrics.py`): `feed_cache_events_total{outcome}`، `feed_db_query_seconds{query}`، `feed_candidates_count{source}`، `feed_scoring_seconds`، `feed_request_seconds{outcome}`، `feed_result_total`، `feed_scores`، `feed_model_info{version}`. در `monitoring/prometheus.yml` به‌عنوان `sarv-recommendation` scrape می‌شود.

## یکپارچه‌سازی با هسته مرکزی

جریان `GET /api/feed/recommended` (`FeedController.java:37`):

1. استخراج `userId` از `UserRepository.findByUsername(username)` (از `@AuthenticationPrincipal`؛ در صورت نبود `404` بدون fallback).
2. `RecommendationClient.getRecommendations(userId, page, size)` → `GET http://recommendation:8000/feed?user_id=&page=&size=` از طریق `RestClient` (`RestClientConfig.java:13` `baseUrl=${recommendation.base-url}` / `RECOMMENDATION_URL`، `SimpleClientHttpRequestFactory` با تایم‌اوت ۱۵۰۰ میلی‌ثانیه، در تست ۵۰۰).
3. استخراج شناسه‌ها (رد کردن `NumberFormatException` با `log.warn`)، مقدار `total` از پاسخ.
4. هیدراته `findAllByIdsFiltered(rankedIds)` (`WHERE id IN :ids AND deletedAt IS NULL`)، ساخت `Map<id,Post>`، مرتب مجدد به ترتیب `rankedIds`، فیلتر `null`/`deletedAt`، نگاشت به `PostResponse`، `new PageImpl<>(content, pageable, total)`.
5. در صورت `rankedIds.isEmpty()` یا هر `Exception` (تایم‌اوت، `RestClientException`، بدنه `null`) → ثبت `log.warn` و fallback به `getChronological(pageable)` (تخریب مهربانانه)؛ برای کلاینت شفاف است — شکل یکسان `Page<PostResponse>`.
6. ثبت `REQUEST_FEED` با `metadata {feed_type: recommended}` از طریق `EventLogService` (ناهمگام، بهترین‌تلاش).

`docker-compose.yaml` پورت‌های بک‌اند `8080`، توصیه‌گر `8000`، Postgres `5432` و فرانت‌اند `3000` (به [7-Frontend.md](./7-Frontend.md) مراجعه کنید) را اکسپوز کرده و `RECOMMENDATION_URL` را برای بک‌اند ست می‌کند.

---

## استقرار

**Dockerfile** (`intelligence/recommendation/Dockerfile:40`):

- چندمرحله‌ای: `python:3.13-slim` بیلد + ران‌تایم، `ghcr.io/astral-sh/uv:latest` (`uv`/`uvx`)، `UV_COMPILE_BYTECODE=1`, `UV_LINK_MODE=copy`.
- `COPY pyproject.toml uv.lock` → `uv sync --frozen --no-install-project` → `COPY main.py candidate.py scoring.py database.py cache.py metrics.py` → `uv sync --frozen`.
- ران‌تایم: کاربر غیرریشه `appuser`، کپی `.venv` + ۶ فایل پایتون `chown appuser`، `PATH="/app/.venv/bin"`، `EXPOSE 8000`، `CMD ["uvicorn","main:app","--host","0.0.0.0","--port","8000"]`.
- `.dockerignore` ` .env`, `.venv`, `__pycache__` را حذف می‌کند.

**بررسی سلامت** (`docker-compose.yaml:40`):

```yaml
healthcheck:
  test: ["CMD-SHELL", "python -c \"import urllib.request, sys; urllib.request.urlopen('http://localhost:8000/health', timeout=2).read() or sys.exit(1)\""]
  interval: 10s
  timeout: 3s
  retries: 3
  start_period: 10s
```

احراز هویت روی `GET /feed` نیست (به شبکه داخلی docker و اعتماد بک‌اند متکی است).

---

## تست

تست‌های پایتون زیر `intelligence/recommendation/tests/` قرار دارند (`uv run pytest`):

- `test_scoring.py` — موارد لبه `score_post` (صفر/منفی، جریمه دیسلایک، تاریخ آینده، نیمه‌عمر ۴۸ ساعته، ضریب `1.5×` دنبال‌شونده، ترتیب).
- `test_candidate_dedup.py` — هم‌پوشانی داغ/دنبال‌شونده نسخه پرچم‌دار را نگه می‌دارد، پست‌های دنبال‌کننده بدون پرچم، حفظ ترتیب (بدون DB واقعی).
- `test_feed_contract.py` — قرارداد `/feed` با `CandidateGenerator` ساختگی (کلیدها، `score desc`، `page/size/total`، صفحه خالی، `422`).
- `test_feed_cache.py` — هیت کش DB را دور می‌زند، میس ذخیره می‌کند، خطا به DB برمی‌گردد (کش ساختگی).
- `test_metrics.py` — `/metrics` همه سری‌های جدید و نسخه مدل را نشان می‌دهد.

تست‌های قرارداد بک‌اند همچنان منبع حقیقت یکپارچه‌سازی هستند:

- `FeedServiceRecommendedTest.java:10` مورد (هیدراته با حفظ ترتیب رتبه، fallback خالی/استثنا، نادیده‌گرفتن `post_id` نامعتبر، فیلتر حذف‌شده/یافت‌نشده، انتشار `UserNotFound`، صفحه‌بندی، `total`).
- `FeedControllerRecommendedTest.java:13` مورد (۲۰۰ Page shape، خالی، ۴۰۳، ۴۰۴، ۵۰۰، `Pageable` پیش‌فرض بدون مرتب‌سازی، نادیده‌گرفتن sort، principal، فراداده صفحه‌بندی، ۴۰۵).
- `src/test/resources/application.properties:18` مقدار `recommendation.base-url=http://localhost:8000` را شبیه‌سازی می‌کند.

---

## وضعیت پیاده‌سازی

- **تولید کاندید:** پیاده‌سازی‌شده (داغ ۱۰۰، دنبال‌شونده ۵۰، دنبال‌کننده ۵۰، حذف تکراری با اولویت نسخه پرچم‌دار، بازه ۷ روز؛ پست‌های دنبال‌کننده بدون ضریب)
- **امتیازدهی:** پیاده‌سازی‌شده به‌عنوان `heuristic-v1` (`2L + V + 3C - 2D`، نیمه‌عمر `48h`، ضریب `1.5×` دنبال‌شونده، `1.0 تا 2.0×` تعامل با نویسنده، `0.9 تا 1.1×` سطح تعامل کاربر)
- **API:** پیاده‌سازی‌شده (`GET /feed` با `page/size/total`، `GET /health` با `status` و `model`)
- **صفحه‌بندی:** پیاده‌سازی‌شده سمت سرور `score desc`
- **Docker و سلامت:** پیاده‌سازی‌شده
- **یکپارچه‌سازی:** پیاده‌سازی‌شده (بک‌اند `RestClient` + fallback)
- **تست:** پیاده‌سازی‌شده (`tests/test_scoring|dedup|contract|cache|metrics`، ۳۲ مورد)
- **سیگنال‌های رفتاری (P2):** پیاده‌سازی‌شده (تعامل ۳۰ روزه با نویسنده + سطح تعامل کاربر، کلید کش نسخه‌دار؛ ایندکس `V10` موکول شد)
- **لایه داده (P1):** پیاده‌سازی‌شده (`AsyncConnectionPool` + lifespan، `_fetch` یکپارچه، کش read-through ردیس با ۴۵ ثانیه ماندگاری و bypass، ایندکس‌های جزئی `V9`)
- **مشاهده‌پذیری (P1):** پیاده‌سازی‌شده (متریک‌های کش/کوئری/کاندید/امتیاز/درخواست/نتیجه/امتیازها + `feed_model_info`)
- **موارد باقی‌مانده:** قرارداد فقط شناسه

به [5-Backend.md](./5-Backend.md) و [3-Architecture.md](./3-Architecture.md) نیز مراجعه کنید.
