# سرویس تحلیل داده (Analytics Service)

این سند سرویس تحلیل داده را توصیف می‌کند — یک سرویس مستقل پایتون (FastAPI) که داده‌های `event_logs` و `users` را به گزارش‌های آماری برای مدیر سیستم تبدیل می‌کند (UC-16).

این سند هدف، ساختار پروژه، راه‌اندازی سریع، پیکربندی، قراردادهای مشترک کوئری‌ها، مرجع API، تست/CI و وضعیت پیاده‌سازی را پوشش می‌دهد و **وضعیت فعلی پیاده‌سازی** را منعکس می‌کند.

---

## نمای کلی

سرویس تحلیل داده کاربران را مدیریت نمی‌کند و داده‌ای ذخیره نمی‌کند. این سرویس پایگاه داده مشترک PostgreSQL را می‌خواند و شاخص‌های تجمیعی را روی بازه زمانی درخواستی برمی‌گرداند. برخلاف سیستم Recommendation که در مسیر بحرانی درخواست‌های فید قرار دارد، تحلیل داده آفلاین کار می‌کند و هیچ تأثیری بر تأخیر تجربه کاربران ندارد.

ویژگی‌های کلیدی:

- آفلاین — فقط تجمیع خواندنی روی `event_logs` و `users` (بدون نوشتن)
- بدون حالت (Stateless) — قابل مقیاس افقی
- پایگاه داده مشترک — فقط خواندن؛ کوئری‌های بازه‌ای سنگین با `MAX_BUCKETS` محدود می‌شوند
- بدون احراز هویت (شبکه داخلی، مشابه سرویس recommendation)

پوشش اقلام داشبورد (UC-16):

| قلم داشبورد | نقطه پایانی |
|---|---|
| کاربران فعال | `GET /users/active`، `GET /usage/activity`، بخش active در `GET /users/engagement` |
| ساعات اوج فعالیت | `GET /usage/peak-hours` |
| نمودارهای تعامل | `GET /users/engagement`، `GET /events/breakdown` |
| میانگین زمان مشاهده فید | `GET /engagement/viewing-time` |
| پرکارترین کاربران | `GET /users/most-active` |

---

## ساختار پروژه

```
intelligence/analytics/
  main.py              # فقط راه‌اندازی اپ: lifespan و include_router (۶ مورد)
  api/                 # یک ماژول روتر برای هر دامنه (مسیرها + مدل‌های پاسخ)
    activity.py        # /users/active ،/usage/activity
    breakdown.py       # /events/breakdown
    engagement.py      # /users/engagement
    peak_hours.py      # /usage/peak-hours
    viewing_time.py    # /engagement/viewing-time
    rankings.py        # /users/most-active
    _common.py         # or_422(): تبدیل ValueError به خطای 422
  db/
    pool.py            # استخر اتصال مشترک (حداقل ۲، حداکثر ۱۰)
    queries/           # یک ماژول برای هر دامنه + ‎_common.py
  tests/               # یونیت‌تست استاندارد + ‎fakes.py (استخر جعلی)
```

کمک‌متدهای مشترک کوئری‌ها در `db/queries/_common.py` قرار دارند: `parse_interval`، `build_bucket_starts`، `gap_fill`، `MAX_BUCKETS = 1000`، `validate_range` و `resolve_buckets` (اعتبارسنجی بازه + بازه زمانی + سقف باکت در یک فراخوانی). اتصال استخر به صورت تنبل داخل توابع کوئری وارد می‌شود تا کمک‌متدهای خالص بدون پیکربندی دیتابیس قابل واردکردن بمانند.

---

## راه‌اندازی سریع

**محلی (بدون داکر):**

```bash
cd intelligence/analytics
uv sync
export DB_URL=postgresql://postgres:your-password@localhost:5432/sarv
PYTHONPATH=.. uv run uvicorn analytics.main:app --port 8001
curl "http://localhost:8001/usage/activity?start_time=2026-01-01T00:00:00Z&end_time=2026-01-08T00:00:00Z&interval=1d"
```

رابط Swagger به صورت خودکار در `http://localhost:8001/docs` در دسترس است.

هنوز `Dockerfile` یا ورودی `docker-compose.yaml` برای این سرویس وجود ندارد (به بخش «وضعیت پیاده‌سازی» در انتهای همین صفحه مراجعه کنید).

---

## پیکربندی و محیط

سرویس از `pydantic-settings` استفاده می‌کند (`config.py:4`):

| متغیر | توضیح | مثال |
|----------|-------------|---------|
| `DB_URL` | رشته اتصال PostgreSQL (الزامی) | `postgresql://postgres:your-password@localhost:5432/sarv` |

متغیر دیگری لازم نیست. تست‌ها خودشان `DB_URL` را با مقدار ساختگی پر می‌کنند چون استخر کاملاً جعلی است.

---

## قراردادهای مشترک

- **بازه‌های زمانی** نیم‌باز `[start_time, end_time)` هستند؛ `start_time >= end_time` با خطای `422` رد می‌شود.
- **بازه‌های زمانی (interval)** با رشته‌هایی مثل `15m`، `1h`، `1d`، `1w` بیان می‌شوند (حروف کوچک اجباری؛ `1M` برای جلوگیری از ابهام ماه/دقیقه رد می‌شود). ورودی خام هرگز در SQL قرار نمی‌گیرد — از طریق فهرست سفید به بازه معتبر Postgres تبدیل می‌شود.
- **باکت‌ها از `start_time` تراز می‌شوند** (`date_bin(..., origin=start_time)`) و باکت‌های خالی با صفر پر می‌شوند (به‌جز `GET /users/active` که گروه‌های ساعتی تُنُک برمی‌گرداند).
- **خطاهای اعتبارسنجی** (`ValueError`) با `or_422` (`api/_common.py`) به خطای HTTP `422` تبدیل می‌شوند.
- **سقف باکت**: حداکثر `MAX_BUCKETS = 1000` باکت در هر درخواست، در غیر این صورت `422`.
- **ساعت‌ها و منطقه زمانی**: تجمیع ساعت‌روز با `AT TIME ZONE 'UTC'` ثابت شده است؛ بقیه زمان‌ها همان‌طور که داده شده‌اند (ISO 8601) عبور می‌کنند.
- **`limit`** در جدول‌های رتبه‌بندی پیش‌فرض ۱۰ و بازه مجاز ۱ تا ۱۰۰ است (خارج از بازه `422`).

---

## مرجع API

### `GET /users/active`

کاربران فعال متمایز ساعتی در بازه (تُنُک — ساعت‌های بدون رویداد حذف می‌شوند، نه صفرگذاری).

- **پارامترها:** `start_time`، `end_time`
- **پاسخ `200`:** `[{"period_start": "...", "active_users": 12}, ...]`

### `GET /usage/activity`

کاربران فعال متمایز در هر باکت بازه زمانی، با صفرگذاری شکاف‌ها.

- **پارامترها:** `start_time`، `end_time`، `interval` (الزامی، مثل `1h`)
- **پاسخ `200`:**
```json
{
  "start_time": "...", "end_time": "...", "interval": "1h",
  "buckets": [{"period_start": "...", "active_users": 12}, ...]
}
```

### `GET /events/breakdown`

تعداد رویدادها به تفکیک نوع، با سری زمانی اختیاری در هر باکت (داده نمودار میله‌ای انباشته).

- **پارامترها:** `start_time`، `end_time`، `interval` (اختیاری)
- هر ۱۳ نوع شناخته‌شده (`VIEW_POST`، `LIKE_POST`، `DISLIKE_POST`، `CREATE_COMMENT`، `REPOST_POST`، `FOLLOW_USER`، `UNFOLLOW_USER`، `VIEW_PROFILE`، `CREATE_POST`، `REQUEST_FEED`، `LOGIN`، `REGISTER`، `QUOTE_POST`) همیشه با صفرگذاری برمی‌گردند. این فهرست enum نوع رویداد را آینه می‌کند (`V1` به‌علاوه `REGISTER`/`QUOTE_POST` از `V7`) — هنگام افزودن نوع جدید در بک‌اند، `KNOWN_EVENT_TYPES` در `db/queries/event_breakdown.py` را به‌روز نگه دارید.
- **پاسخ `200`:**
```json
{
  "start_time": "...", "end_time": "...", "interval": "1h",
  "totals": [{"event_type": "LOGIN", "count": 1240}, ...],
  "buckets": [{"period_start": "...", "counts": {"LOGIN": 41, ...}}, ...]
}
```
(بدون `interval`، مقدار `buckets` برابر `null` است.)

### `GET /users/engagement`

کاربران کل، فعال، جدید و بازگشتی، با سری زمانی اختیاری در هر باکت.

- **پارامترها:** `start_time`، `end_time`، `interval` (اختیاری)
- تعریف‌ها: **جدید** = `users.created_at` در بازه (ثبت رویداد بهترین‌تلاش است، پس رویدادهای `REGISTER` ثبت‌نام‌ها را کمتر می‌شمارند)؛ **بازگشتی** = فعال در بازه و ساخته‌شده پیش از بازه (در هر باکت: پیش از شروع همان باکت)؛ **کل** = عکس لحظه‌ای `status='ACTIVE'` در `end_time` (در هر باکت: انباشته تا پایان باکت). کاربران فعال، کنشگران متمایز رویدادها هستند بدون توجه به وضعیت.
- ثبت‌نام‌های جدید هر باکت حتی اگر در آن باکت فعالیتی نداشته باشند شمرده می‌شوند، پس در هر باکت لزوماً `new + returning` برابر `active` نیست.
- **پاسخ `200`:**
```json
{
  "start_time": "...", "end_time": "...", "interval": "1d",
  "totals": {"total_users": 100, "active_users": 60, "new_users": 10, "returning_users": 50},
  "buckets": [{"period_start": "...", "total_users": 95, "active_users": 30, "new_users": 5, "returning_users": 25}, ...]
}
```

### `GET /usage/peak-hours`

تعداد رویدادها و کاربران فعال متمایز در هر ساعت از شبانه‌روز (UTC) روی بازه.

- **پارامترها:** `start_time`، `end_time`
- هر ۲۴ ساعت با صفرگذاری برمی‌گردد؛ `peak_hour` ساعتی با بیشترین رویداد است (تساوی به نفع ساعت زودتر شکسته می‌شود).
- **پاسخ `200`:**
```json
{
  "start_time": "...", "end_time": "...", "timezone": "UTC",
  "peak_hour": {"hour": 18, "event_count": 900, "active_users": 200},
  "buckets": [{"hour": 0, "event_count": 12, "active_users": 5}, ...]
}
```

### `GET /engagement/viewing-time`

میانگین زمان مشاهده پست از روی ردیف‌های dwell (رویداد `VIEW_POST` با `metadata.duration_ms`، نوشته‌شده توسط `POST /api/posts/{id}/dwell` — به [5-Backend.md](./5-Backend.md) مراجعه کنید)، با روند اختیاری و فیلتر منبع.

- **پارامترها:** `start_time`، `end_time`، `interval` (اختیاری)، `source` (اختیاری: `FEED` یا `DETAIL`، در غیر این صورت `422`)
- مقادیر با عبارت منظم عددی و محدوده معتبر بک‌اند (`1..1800000` میلی‌ثانیه) نگهبانی می‌شوند؛ ردیف‌های بدون کلید `source` به‌جای حذف، زیر `"UNKNOWN"` گزارش می‌شوند. میانگین کلی هر باکت از جمع‌های هر منبع وزن‌دهی می‌شود، نه از میانگین‌گیریِ میانگین‌ها. بازه‌های خالی `average_duration_ms: null, samples: 0` برمی‌گردانند.
- **پاسخ `200`:**
```json
{
  "start_time": "...", "end_time": "...", "interval": "1h",
  "overall": {"average_duration_ms": 4500.0, "samples": 100},
  "by_source": {"FEED": {"average_duration_ms": 5000.0, "samples": 60}, ...},
  "buckets": [{"period_start": "...", "average_duration_ms": 4600.0, "samples": 20, "by_source": {...}}, ...]
}
```

### `GET /users/most-active`

جدول پرکارترین کاربران بر اساس تعداد رویداد در بازه.

- **پارامترها:** `start_time`، `end_time`، `limit` (پیش‌فرض ۱۰، بازه ۱ تا ۱۰۰)
- هر رویداد ثبت‌شده فعالیت محسوب می‌شود (ردیف‌های بازدید و dwell)؛ جوین با `users` کنشگران `NULL` (کاربران حذف‌شده) را کنار می‌گذارد.
- **پاسخ `200`:**
```json
{
  "start_time": "...", "end_time": "...", "limit": 10,
  "users": [{"user_id": 7, "username": "feri", "display_name": "Feri", "event_count": 900}, ...]
}
```

هیچ نقطه پایانی احراز هویت ندارد (با تکیه بر شبکه داخلی، مشابه سرویس recommendation).

---

## تست و CI

یونیت‌تست استاندارد (بدون وابستگی به رانر تست)، با استخر کاملاً جعلی (`tests/fakes.py` که در هر فراخوانی یک یا چند مجموعه نتیجه آماده برمی‌گرداند):

```bash
cd intelligence/analytics
PYTHONPATH=.. uv run python -m unittest discover -s tests
```

گردش‌کار `.github/workflows/analytics.yml` همین دستور را روی هر push/PR که `intelligence/analytics/**` را لمس کند اجرا می‌کند (به‌علاوه `workflow_dispatch`)؛ نیازی به سرویس دیتابیس نیست.

---

## وضعیت پیاده‌سازی

- **کوئری‌ها:** پیاده‌سازی‌شده (`usage_time`، `event_breakdown`، `engagement`، `peak_hours`، `viewing_time`، `rankings` + ‎`_common.py` مشترک)
- **API:** پیاده‌سازی‌شده (۷ نقطه پایانی روی ۶ روتر + ‎`/users/active` ساعتی قدیمی)
- **تست‌ها:** پیاده‌سازی‌شده (۵۷ مورد، استخر جعلی، CI روی push/PR)
- **باقی‌مانده:** جدول پست‌های پرتعامل (کنار گذاشته‌شده)، احراز هویت سرویس، ورودی `Dockerfile` / کامپوز / بررسی سلامت، متریک‌های Prometheus

به [5-Backend.md](./5-Backend.md) (ثبت رویداد، قراردادهای dwell) و [3-Architecture.md](./3-Architecture.md) نیز مراجعه کنید.
