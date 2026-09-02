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

`docker-compose.yaml` مقدار `DB_HOST=postgres` و `DB_PORT=5432` را تنظیم می‌کند؛ به‌صورت محلی پیش‌فرض‌ها به `localhost` برمی‌گردند. وابستگی `redis` در `pyproject.toml` فعلاً استفاده نمی‌شود.

اتصال بک‌اند جداگانه از طریق `RECOMMENDATION_URL` پیکربندی می‌شود (به [5-Backend.md](./5-Backend.md) مراجعه کنید).

---

## مرجع API

### `GET /health`

بررسی سلامت برای `docker-compose.yaml` (`interval 10s, timeout 3s, retries 3, start_period 10s` — `python -c "urllib.request.urlopen('http://localhost:8000/health')"`).

- **احراز هویت:** ندارد (شبکه داخلی)
- **پاسخ `200`:**
```json
{ "status": "ok" }
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

`CandidateGenerator(user_id).generate_candidates()` (`candidate.py:7`) سه منبع (`search_span_days=7`) را ترکیب و با حفظ ترتیب `trending → following → follower` حذف تکراری می‌کند (`seen = set()`).

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
نیز `from_followed=True` (همان تقویت دنبال‌شونده؛ تفاوت مورد نظر مستند نشده).

حداکثر کاندید خام: `100 + 50 + 50 = 200` قبل از حذف تکراری. `database.py:12` `get_connection()` برای هر فراخوانی یک اتصال جدید `psycopg` باز می‌کند (بدون pool).

---

## امتیازدهی

کلاس `PostFeatures` (`scoring.py:6`):

```python
post_id: str
like_count: int
dislike_count: int
view_count: int
created_at: datetime
from_followed: bool = False
```

`score_post(features, now=None)` (`scoring.py:16`):

```python
engagement = 2 * like_count + view_count - 2 * dislike_count
engagement = max(engagement, 0)

age_hours = max((now - created_at).total_seconds() / 3600, 0)
recency_boost = 1 / (1 + age_hours / 48)  # نیمه‌عمر حدود 48 ساعت

follow_boost = 1.5 if from_followed else 1.0

return engagement * recency_boost * follow_boost
```

- تعامل با وزن `2×` برای لایک/دیسلایک، `1×` برای بازدید، کف ۰.
- پوسیدگی زمانی `1/(1+t/48h)` → ۵۰٪ در ۲ روز، ۳۳٪ در ۴ روز.
- تقویت دنبال‌شدن `1.5×`.

*نمونه:* ۱۰ لایک، ۱۰۰ بازدید، ۱ دیسلایک، ۱۲ ساعت عمر، از دنبال‌شونده → `(20+100-2)*0.8*1.5 ≈ 141.6`.

`ranked = sorted(candidates, key=score_post, reverse=True)` ترتیب نهایی را تعیین می‌کند.

---

## صفحه‌بندی و قرارداد

- `page` مبتنی بر صفر، `size` ۱ تا ۱۰۰، اعتبارسنجی توسط FastAPI `Query`.
- `total` تعداد قبل از صفحه‌بندی است؛ بک‌اند آن را به‌عنوان `Page.totalElements` (`FeedService.java:65`) استفاده کرده و ترتیب رتبه را حتی پس از فیلتر `post_id` نامعتبر یا ردیف حذف‌شده/یافت‌نشده حفظ می‌کند (ممکن است در صفحه آخر حفره ایجاد شود).
- صفحه خارج از محدوده `posts: []` با همان `total` برمی‌گرداند.
- قرارداد مورد نظر فقط شناسه‌ها است؛ `score` فعلی برای اشکال‌زدایی است و توسط بک‌اند جز برای مرتب‌سازی نادیده گرفته می‌شود.

---

## یکپارچه‌سازی با هسته مرکزی

جریان `GET /api/feed/recommended` (`FeedController.java:37`):

1. استخراج `userId` از `UserRepository.findByUsername(username)` (از `@AuthenticationPrincipal`؛ در صورت نبود `404` بدون fallback).
2. `RecommendationClient.getRecommendations(userId, page, size)` → `GET http://recommendation:8000/feed?user_id=&page=&size=` از طریق `RestClient` (`RestClientConfig.java:13` `baseUrl=${recommendation.base-url}` / `RECOMMENDATION_URL`، `SimpleClientHttpRequestFactory` با تایم‌اوت ۱۵۰۰ میلی‌ثانیه، در تست ۵۰۰).
3. استخراج شناسه‌ها (رد کردن `NumberFormatException` با `log.warn`)، مقدار `total` از پاسخ.
4. هیدراته `findAllByIdsFiltered(rankedIds)` (`WHERE id IN :ids AND deletedAt IS NULL`)، ساخت `Map<id,Post>`، مرتب مجدد به ترتیب `rankedIds`، فیلتر `null`/`deletedAt`، نگاشت به `PostResponse`، `new PageImpl<>(content, pageable, total)`.
5. در صورت `rankedIds.isEmpty()` یا هر `Exception` (تایم‌اوت، `RestClientException`، بدنه `null`) → ثبت `log.warn` و fallback به `getChronological(pageable)` (تخریب مهربانانه)؛ برای کلاینت شفاف است — شکل یکسان `Page<PostResponse>`.
6. ثبت `REQUEST_FEED` با `metadata {feed_type: recommended, page,size,total_elements,returned,...}` از طریق `EventLoggingAspect.java:84`.

`docker-compose.yaml` پورت‌های بک‌اند `8080`، توصیه‌گر `8000`، Postgres `5432` را اکسپوز کرده و `RECOMMENDATION_URL` را برای بک‌اند ست می‌کند.

---

## استقرار

**Dockerfile** (`intelligence/recommendation/Dockerfile:40`):

- چندمرحله‌ای: `python:3.13-slim` بیلد + ران‌تایم، `ghcr.io/astral-sh/uv:latest` (`uv`/`uvx`)، `UV_COMPILE_BYTECODE=1`, `UV_LINK_MODE=copy`.
- `COPY pyproject.toml uv.lock` → `uv sync --frozen --no-install-project` → `COPY main.py candidate.py scoring.py database.py` → `uv sync --frozen`.
- ران‌تایم: کاربر غیرریشه `appuser`، کپی `.venv` + ۴ فایل پایتون `chown appuser`، `PATH="/app/.venv/bin"`، `EXPOSE 8000`، `CMD ["uvicorn","main:app","--host","0.0.0.0","--port","8000"]`.
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

فعلاً تست واحد پایتون نیست (`test*` زیر `intelligence/recommendation` وجود ندارد). تست‌های قرارداد بک‌اند منبع حقیقت هستند:

- `FeedServiceRecommendedTest.java:10` مورد (هیدراته با حفظ ترتیب رتبه، fallback خالی/استثنا، نادیده‌گرفتن `post_id` نامعتبر، فیلتر حذف‌شده/یافت‌نشده، انتشار `UserNotFound`، صفحه‌بندی، `total`).
- `FeedControllerRecommendedTest.java:13` مورد (۲۰۰ Page shape، خالی، ۴۰۳، ۴۰۴، ۵۰۰، `Pageable` پیش‌فرض بدون مرتب‌سازی، نادیده‌گرفتن sort، principal، فراداده صفحه‌بندی، ۴۰۵).
- `src/test/resources/application.properties:18` مقدار `recommendation.base-url=http://localhost:8000` را شبیه‌سازی می‌کند.

پیشنهاد: افزودن `pytest` برای موارد لبه `score_post`، حذف تکراری کاندید و DB mock.

---

## وضعیت پیاده‌سازی

- **تولید کاندید:** پیاده‌سازی‌شده (داغ ۱۰۰، دنبال‌شونده ۵۰، دنبال‌کننده ۵۰، حذف تکراری، بازه ۷ روز)
- **امتیازدهی:** پیاده‌سازی‌شده (`2*like + view -2*dislike`، نیمه‌عمر `48h`، ضریب `1.5×`)
- **API:** پیاده‌سازی‌شده (`GET /feed` با `page/size/total`، `GET /health`)
- **صفحه‌بندی:** پیاده‌سازی‌شده سمت سرور `score desc`
- **Docker و سلامت:** پیاده‌سازی‌شده
- **یکپارچه‌سازی:** پیاده‌سازی‌شده (بک‌اند `RestClient` + fallback)
- **موارد باقی‌مانده:** قرارداد فقط شناسه، باگ `from_followed` (دنبال‌کننده vs دنبال‌شونده)، کش `redis` (اعلام‌شده ولی استفاده نشده)، تست واحد پایتون، متریک/Prometheus

به [5-Backend.md](./5-Backend.md) و [3-Architecture.md](./3-Architecture.md) نیز مراجعه کنید.
