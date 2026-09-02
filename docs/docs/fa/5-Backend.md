# بک‌اند اصلی (Core Backend)

این مستند پیاده‌سازی سرویس بک‌اند اصلی را شرح می‌دهد؛ سرویسی که نقطه ورود تمامی درخواست‌های کاربران است و منطق اصلی کسب‌وکار پلتفرم را اجرا می‌کند.

مستند شامل پشته فناوری، ساختار پروژه، مرجع API، احراز هویت، ذخیره‌سازی رسانه، ثبت رویداد، مدیریت خطا، پیکربندی و تست است و **وضعیت فعلی پیاده‌سازی** را بازتاب می‌دهد. بخش‌هایی که طراحی شده‌اند ولی هنوز ساخته نشده‌اند در انتهای مستند، در بخش «وضعیت پیاده‌سازی» آمده‌اند.

---

## نمای کلی

بک‌اند اصلی یک برنامه سمت سرور است که با Java و Spring Boot نوشته شده و مسئولیت‌های زیر را بر عهده دارد:

- مدیریت کاربران، پروفایل‌ها و احراز هویت
- پست‌ها و تعاملات کاربران (واکنش، کامنت، بازنشر، نقل‌قول، دنبال‌کردن)
- آپلود و ارائه فایل‌های رسانه‌ای
- ثبت رویدادهای رفتاری کاربران برای تحلیل و پیشنهاددهی
- ارتباط با سرویس پیشنهاددهی (رتبه‌بندی فید با تخریب مهربانانه)

همه درخواست‌ها از طریق REST API ارائه می‌شوند و با احراز هویت مبتنی بر JWT محافظت می‌شوند.

---

## پشته فناوری

| موضوع | فناوری |
|---------|------------|
| زبان / زمان اجرا | Java 25 |
| فریم‌ورک | Spring Boot 4.1 (MVC, Data JPA, Security, Validation, AOP) |
| پایگاه داده | PostgreSQL با Spring Data JPA / Hibernate |
| مهاجرت‌ها | Flyway (`spring-boot-starter-flyway`) |
| احراز هویت | JWT (JJWT 0.13) و هش رمز عبور با BCrypt |
| مستندات API | springdoc-openapi (Swagger UI) |
| ذخیره‌سازی رسانه | فایل‌سیستم محلی (`LocalStorageService`) |
| ابزار کمکی | Lombok |
| تست | JUnit 5، MockMvc و H2 (در زمان اجرای تست) |

---

## ساختار پروژه

بک‌اند از معماری لایه‌ای پیروی می‌کند. ریشه سورس در `backend/src/main/java/com/github/ferigeek/sarv/` قرار دارد:

```
controller/   نقاط پایانی REST (Auth, User, Follow, Post, Reaction, Media, Feed)
service/      منطق کسب‌وکار (Auth, User, Follow, Post, Reaction, Media, Feed,
              CustomUserDetails, LocalStorage, واسط ObjectStorage)
repository/   مخزن‌های Spring Data JPA
entity/       موجودیت‌های JPA (User, Post, Media, Follow, Reaction, EventLog)
entity/type/  Enum ها (PostCategory, EventType, Gender, UserStatus)
dto/          اشیای انتقال داده request/ و response/
security/     SecurityConfig, JwtUtil, JwtAuthFilter, OpenApiConfig
aspect/       annotation لاج ایونت + EventLoggingAspect
exception/    استثناهای سفارشی + GlobalExceptionHandler
client/       RecommendationClient + RecommendationResponse (رتبه‌بندی فید)
config/       RestClientConfig (کلاینت HTTP توصیه‌گر)
```

درخواست‌های HTTP ابتدا در لایه کنترلر پردازش می‌شوند؛ جایی که احراز هویت، اعتبارسنجی و کنترل دسترسی انجام می‌گیرد. منطق کسب‌وکار در لایه سرویس و ذخیره‌سازی و بازیابی داده‌ها از طریق لایه مخزن انجام می‌شود.

---

## مرجع API

همه نقاط پایانی با پیشوند `/api` شروع می‌شوند. به جز مواردی که با **عمومی** مشخص شده‌اند، هر نقطه پایانی به هدر `Authorization: Bearer <token>` نیاز دارد و در صورت نبود یا نامعتبر بودن JWT پاسخ `401` برمی‌گرداند.

### احراز هویت (`/api/auth`)

| متد | مسیر | احراز هویت | توضیح |
|--------|------|------|-------------|
| POST | `/api/auth/register` | عمومی | ثبت‌نام کاربر جدید و بازگرداندن پروفایل به همراه توکن JWT |
| POST | `/api/auth/login` | عمومی | احراز هویت کاربر و بازگرداندن توکن JWT |

فیلدهای ثبت‌نام: `username` (حداقل ۲ کاراکتر)، `password` (۸ تا ۵۰ کاراکتر)، `email`، `displayName` (حداقل ۲ کاراکتر)، `gender` (`MALE`, `FEMALE`, `RATHER_NOT_TO_SAY`). نام کاربری تکراری با `409 Conflict` رد می‌شود. در هر ورود موفق رویداد `LOGIN` ثبت می‌شود؛ ثبت‌نام نیز ورود خودکار انجام می‌دهد و بنابراین رویداد `LOGIN` نیز تولید می‌کند.

### کاربران و پروفایل (`/api/users`)

| متد | مسیر | احراز هویت | توضیح |
|--------|------|------|-------------|
| GET | `/api/users/{userId}` | bearer | بازگرداندن پروفایل یک کاربر؛ ثبت رویداد `VIEW_PROFILE` |
| GET | `/api/users/me` | bearer | بازگرداندن پروفایل کاربر احراز هویت‌شده؛ ثبت رویداد `VIEW_PROFILE` |
| PUT | `/api/users/me` | bearer | به‌روزرسانی پروفایل کاربر احراز هویت‌شده |
| GET | `/api/users?query=` | bearer | جست‌وجوی کاربران بر اساس نام کاربری یا نام نمایشی (بدون حساسیت به بزرگی/کوچکی حروف، تطبیق جزئی)، صفحه‌بندی‌شده |

فیلدهای به‌روزرسانی پروفایل: `displayName` (الزامی، حداقل ۲ کاراکتر)، `bio` (اختیاری)، `location` (اختیاری)، `gender` (الزامی)، `profilePictureId` (شناسه رسانه، اختیاری). از معنای `PUT` استفاده می‌شود: فیلدهایی که `null` یا خالی ارسال شوند پاک می‌شوند، به جز `displayName` و `gender` که الزامی هستند.

### دنبال‌کردن (`/api/users/{userId}/followers`)

| متد | مسیر | احراز هویت | توضیح |
|--------|------|------|-------------|
| GET | `/api/users/{userId}/followers` | bearer | فهرست صفحه‌بندی‌شده دنبال‌کننده‌های کاربر |
| GET | `/api/users/{userId}/following` | bearer | فهرست صفحه‌بندی‌شده کاربرانی که کاربر دنبال می‌کند |
| POST | `/api/users/{userId}/followers` | bearer | دنبال‌کردن کاربر؛ پاسخ `201 Created`؛ ثبت رویداد `FOLLOW_USER` |
| DELETE | `/api/users/{userId}/followers` | bearer | لغو دنبال‌کردن؛ پاسخ `204 No Content`؛ ثبت رویداد `UNFOLLOW_USER` |

دنبال‌کردن خود با یک محدودیت چک در پایگاه داده جلوگیری می‌شود.

### پست‌ها (`/api/posts`)

| متد | مسیر | احراز هویت | توضیح |
|--------|------|------|-------------|
| GET | `/api/posts/{postId}` | bearer | بازگرداندن پست و افزایش `view_count` آن؛ ثبت رویداد `VIEW_POST` |
| POST | `/api/posts` | bearer | ایجاد پست؛ پاسخ `201 Created` با هدر `Location`؛ ثبت رویداد `CREATE_POST` |
| PUT | `/api/posts/{postId}` | bearer | به‌روزرسانی محتوا/رسانه پست؛ فقط مالک (در غیر این صورت `403`) |
| DELETE | `/api/posts/{postId}` | bearer | حذف نرم پست؛ فقط مالک |

پست با این فیلدها ساخته می‌شود: `postCategory`، `content` (حداکثر ۲۸۰ کاراکتر)، `mediaId`، `parentId`، `repostOfId`. دسته پست تعیین می‌کند کدام فیلدها معتبر هستند:

| دسته | محتوا/رسانه | `parentId` | `repostOfId` |
|----------|---------------|------------|--------------|
| NORMAL | حداقل یکی الزامی | ممنوع | ممنوع |
| COMMENT | حداقل یکی الزامی | الزامی | اختیاری (کامنت به سبک نقل‌قول) |
| QUOTE | حداقل یکی الزامی | ممنوع | الزامی |
| REPOST | هر دو ممنوع | ممنوع | الزامی |

ترکیب‌های نامعتبر با `400 Bad Request` (`PostNotValidException`) رد می‌شوند. به‌روزرسانی پست از معنای `PUT` استفاده می‌کند تا فیلد با `null` صریحاً پاک شود. حذف پست `deleted_at` را تنظیم و ارجاع نویسنده را پاک می‌کند (حذف نرم).

### واکنش‌ها (`/api/posts/{postId}/reactions`)

| متد | مسیر | احراز هویت | توضیح |
|--------|------|------|-------------|
| POST | `/api/posts/{postId}/reactions` | bearer | افزودن یا تغییر واکنش (`reactionType`: `1` = لایک، `-1` = دیسلایک)؛ ثبت رویداد `LIKE_POST` |
| GET | `/api/posts/{postId}/reactions` | bearer | بازگرداندن تعداد لایک/دیسلایک و واکنش فعلی کاربر (`0` = بدون واکنش) |
| DELETE | `/api/posts/{postId}/reactions` | bearer | حذف واکنش کاربر؛ پاسخ `204 No Content` |

هر کاربر حداکثر یک واکنش برای هر پست دارد (محدودیت یکتا روی `post_id + user_id`). افزودن واکنش از نوع مخالف، واکنش قبلی را تغییر می‌دهد و شمارنده‌های `like_count` / `dislike_count` پست متناسباً به‌روزرسانی می‌شوند. در پیاده‌سازی فعلی رویداد `LIKE_POST` برای هر دو نوع لایک و دیسلایک ثبت می‌شود؛ حذف واکنش ثبت نمی‌شود.

### فید (`/api/feed`)

| متد | مسیر | احراز هویت | توضیح |
|--------|------|------|-------------|
| GET | `/api/feed/chronological` | bearer | فید زمانی — `deletedAt IS NULL` مرتب `createdAt DESC`. پیش‌فرض `page=0,size=20,sort=createdAt,DESC` (`@PageableDefault`)؛ پارامتر `sort` کلاینت اعمال می‌شود |
| GET | `/api/feed/recommended` | bearer | فید شخصی‌سازی‌شده — ارسال `page/size` به `GET http://recommendation:8000/feed?user_id=&page=&size=` ( `RecommendationClient`، تایم‌اوت ۱۵۰۰ میلی‌ثانیه)، ترتیب `score desc`، **هر پارامتر `sort` نادیده گرفته می‌شود** (تبدیل به `PageRequest` بدون مرتب‌سازی)، تخریب مهربانانه به زمانی در صورت خالی/تایم‌اوت/خطای ۵۰۰ |

هر دو نقطه پایانی به `Authorization: Bearer <token>` نیاز دارند و `Page<PostResponse>` با شکل یکسان برمی‌گردانند:

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
      "dislikeCount": 1
    }
  ],
  "page": { "size": 20, "number": 0, "totalElements": 100, "totalPages": 5 }
}
```

**درخواست:**
`GET /api/feed/chronological?page=0&size=20&sort=createdAt,desc` و `GET /api/feed/recommended?page=1&size=10` (هر `sort` در recommended نادیده گرفته می‌شود؛ رتبه‌بندی همیشه سمت سرور). فید خالی `content: []` با `totalElements: 0` برمی‌گرداند.

**قوانین کسب‌وکار:**
- فیلتر حذف نرم — هر دو `findChronologicalFeed` و `findAllByIdsFiltered` روی `deletedAt IS NULL` فیلتر می‌کنند.
- حفظ ترتیب رتبه — recommended از طریق `findAllByIdsFiltered` هیدراته و در حافظه به ترتیب `score desc` دوباره مرتب می‌شود؛ `post_id` نامعتبر نادیده گرفته می‌شود، پست‌های حذف‌شده/یافت‌نشده حذف می‌شوند اما `total` همچنان `total` توصیه‌گر را نشان می‌دهد (ممکن است در صفحه آخر حفره صفحه‌بندی ایجاد شود).
- صفحه‌بندی — `total` زمانی = شمارش DB؛ `total` پیشنهادی = `total` سرویس توصیه‌گر قبل از فیلتر (در صورت قدیمی بودن به `content.size()` می‌افتد).

**خطاها:**
`403 Forbidden` بدون احراز هویت، `404 Not Found` `User not found with username: <ghost>` فقط در recommended (جست‌وجوی نام کاربری)، `405 Method Not Allowed` برای `POST/PUT/DELETE` روی همان مسیر، `400 Bad Request` برای `?page=abc` (`MethodArgumentTypeMismatchException`)، `500 Internal Server Error` فقط وقتی هم recommended و هم fallback زمانی خطا دهند. تایم‌اوت/۵۰۰/بدنه خالی/خطای تجزیه توصیه‌گر هیچ‌وقت ۵۰۰ برنمی‌گرداند — `WARN` ثبت کرده و صفحه زمانی را شفاف برمی‌گرداند.

**وابستگی‌ها:**
`recommendation.base-url` (متغیر `RECOMMENDATION_URL`، پیش‌فرض `http://recommendation:8000` از طریق `RestClientConfig`) و `recommendation.timeout-ms` (`RECOMMENDATION_TIMEOUT_MS`، پیش‌فرض `1500`، در تست `500`) با `SimpleClientHttpRequestFactory` برای تایم‌اوت connect/read و بررسی سلامت `GET /health` (docker-compose `interval 10s`).

هر دو نقطه پایانی `REQUEST_FEED` را با `metadata {feed_type: chronological|recommended, page,size,total_elements,returned,requested_page,requested_size}` برای تحلیل ثبت می‌کنند؛ به بخش ثبت رویداد مراجعه کنید.

### رسانه (`/api/media`)

| متد | مسیر | احراز هویت | توضیح |
|--------|------|------|-------------|
| POST | `/api/media` | bearer | آپلود فایل (`multipart/form-data`، فیلد `file`، حداکثر ۵۰ مگابایت)؛ بازگرداندن `{id, url}` |
| GET | `/api/media/{mediaId}` | bearer | ارسال فایل ذخیره‌شده با نوع MIME آن |
| GET | `/api/media/{mediaId}/metadata` | bearer | بازگرداندن فراداده: `id`, `size`, `name`, `mimeType`, `createdAt` |

آپلودها با SHA-256 آدرس‌دهی محتوا می‌شوند؛ بنابراین محتوای یکسان فقط یک بار ذخیره می‌شود.

---

## احراز هویت و مجوز

1. `POST /api/auth/register` کاربر را می‌سازد (رمز عبور با BCrypt هش می‌شود) و بلافاصله JWT برمی‌گرداند. `POST /api/auth/login` اعتبارنامه را از طریق `AuthenticationManager` اسپرینگ سکیوریتی بررسی و توکن جدید صادر می‌کند.
2. JWT با HS256 امضا می‌شود و شامل `sub` (نام کاربری)، `iat` و `exp` است. کلید امضا از متغیر محیطی `JWT_SECRET` خوانده می‌شود و باید حداقل ۳۲ بایت باشد؛ `JWT_EXPIRATION` طول عمر توکن را به میلی‌ثانیه مشخص می‌کند.
3. هر درخواست از `JwtAuthFilter` عبور می‌کند؛ این فیلتر توکن را از هدر `Authorization: Bearer <token>` استخراج، اعتبار آن را بررسی، کاربر را بارگذاری و زمینه امنیتی را تنظیم می‌کند. نشست‌ها بدون حالت (stateless) هستند و CSRF غیرفعال است.
4. مسیرهای عمومی: `/api/auth/login`، `/api/auth/register`، `/swagger-ui.html`، `/swagger-ui/**`، `/v3/api-docs/**`. بقیه نقاط پایانی نیازمند احراز هویت هستند.
5. `CustomUserDetailsService` وضعیت کاربر را به حالت حساب نگاشت می‌کند: فقط کاربران `ACTIVE` فعال هستند و کاربران `SUSPENDED` حساب قفل‌شده دارند.

مشخصات OpenAPI با طرح امنیتی سراسری `bearerAuth` در `/swagger-ui.html` در دسترس است.

---

## ذخیره‌سازی رسانه

فایل‌های رسانه‌ای روی **فایل‌سیستم محلی** ذخیره می‌شوند — در پیاده‌سازی فعلی از Object Storage استفاده نمی‌شود.

- `ObjectStorageService` یک واسط کوچک (`uploadObject`, `download`, `delete`) است که جریان رسانه را از پیاده‌سازی مشخص جدا می‌کند.
- `LocalStorageService` پیاده‌سازی فعلی است. فایل‌ها در دایرکتوری مشخص‌شده توسط `STORAGE_DIR` (پیش‌فرض `uploads`) نوشته می‌شوند. نام فایل، هش SHA-256 محتواست که هم حذف داده تکراری (deduplication) و هم کلیدهای پایدار فراهم می‌کند.
- عملیات دانلود و حذف بررسی می‌کنند که مسیر نهایی داخل دایرکتوری ذخیره‌سازی بماند تا از حملات مسیرگذاری (path traversal) جلوگیری شود.
- پایگاه داده فقط فراداده را نگه می‌دارد: `size`, `name`, `mime_type`, `sha_256` (یکتا), `created_at`, `owner_id`. رسانه از پست‌ها (`media_id`) و پروفایل کاربران (`profile_picture`) ارجاع داده می‌شود.

---

## ثبت رویداد

رفتار کاربران از طریق مکانیزم مبتنی بر AOP ثبت می‌شود:

- متدهای کنترلر که با `@LogEvent(EventType.XXX)` علامت‌گذاری شده‌اند، پس از اجرای موفق (`@AfterReturning`) یک ردیف در `event_logs` ایجاد می‌کنند.
- `EventLoggingAspect` کاربر عامل، نوع رویداد، زمان و — بسته به نوع رویداد — پست یا کاربر هدف را ذخیره می‌کند.
- اسکیمای `event_logs` شامل `session_id` (گروه‌بندی کنش‌های یک نشست کاربری؛ بی‌رابطه با JWT) و `metadata` (JSONB، برای اطلاعات خاص هر رویداد) نیز هست. برای `REQUEST_FEED` اکنون aspect مقدار `metadata` را با `{feed_type: chronological|recommended, page, size, total_elements, returned, requested_page, requested_size}` پر می‌کند.

انواع رویداد: `VIEW_POST`, `LIKE_POST`, `DISLIKE_POST`, `CREATE_COMMENT`, `REPOST_POST`, `FOLLOW_USER`, `UNFOLLOW_USER`, `VIEW_PROFILE`, `CREATE_POST`, `REQUEST_FEED`, `LOGIN`. `REQUEST_FEED` توسط هر دو نقطه پایانی فید (`GET /api/feed/chronological` و `GET /api/feed/recommended`) تولید می‌شود.

---

## مدیریت خطا

همه خطاها توسط `GlobalExceptionHandler` به پاسخ‌های `ProblemDetail` (RFC 9457) تبدیل می‌شوند:

| وضعیت | کد HTTP |
|-----------|-------------|
| موجودیت یافت نشد (کاربر، پست، رسانه) | `404 Not Found` |
| خطای اعتبارسنجی، JSON نامعتبر، درخواست بد | `400 Bad Request` |
| تغییر بدون مجوز (غیر از مالک) | `403 Forbidden` |
| نام کاربری تکراری | `409 Conflict` |
| اعتبارنامه نادرست / شکست احراز هویت | `401 Unauthorized` |
| خطای ذخیره‌سازی و استثناهای غیرمنتظره | `500 Internal Server Error` |

هر `ProblemDetail` شامل `status`, `title`, `detail` و `instance` (URI درخواست) است.

---

## صفحه‌بندی

نقاط پایانی فهرستی، اشیای `Page` اسپرینگ دیتا را با `page`, `size`, `totalElements` و `totalPages` برمی‌گردانند. اندازه پیش‌فرض صفحه ۲۰ است؛ مرتب‌سازی پیش‌فرض: جست‌وجوی کاربران بر اساس `username`، دنبال‌کننده‌ها بر اساس `follower.username`، دنبال‌شونده‌ها بر اساس `followed.username`، فید زمانی بر اساس `createdAt DESC`؛ فید پیشنهادی **بدون مرتب‌سازی** است (رتبه‌بندی بر اساس `score desc` سمت سرور، هر `sort` کلاینت نادیده گرفته می‌شود). کلاینت‌ها می‌توانند با پارامترهای استاندارد `page`, `size`, `sort` آن را تغییر دهند؛ recommended مقدار `page/size` را به سرویس توصیه‌گر (`size` ۱ تا ۱۰۰) ارسال کرده و `total` آن را برای فراداده صفحه استفاده می‌کند.

---

## پایگاه داده و مهاجرت‌ها

- اسکیما منحصراً با مهاجرت‌های Flyway در `backend/src/main/resources/db/migration/` مدیریت می‌شود (`V1` اسکیمای اولیه تا `V5` ایندکس‌ها).
- Hibernate با `ddl-auto=validate` پیکربندی شده است؛ بنابراین نگاشت موجودیت‌ها هنگام راه‌اندازی با اسکیمای مهاجرت‌شده بررسی می‌شود.
- اسکیمای کامل در [4-Database.md](./4-Database.md) شرح داده شده است.

---

## پیکربندی و متغیرهای محیطی

متغیرهای محیطی مورد نیاز (به `.env.example` در ریشه مخزن مراجعه کنید):

| متغیر | توضیح |
|----------|-------------|
| `DB_URL` | آدرس JDBC پایگاه داده PostgreSQL |
| `DB_USERNAME` | نام کاربری پایگاه داده |
| `DB_PASSWORD` | رمز عبور پایگاه داده |
| `JWT_SECRET` | کلید امضای JWT (حداقل ۳۲ بایت) |
| `JWT_EXPIRATION` | طول عمر توکن به میلی‌ثانیه |
| `STORAGE_DIR` | دایرکتوری ذخیره‌سازی رسانه (پیش‌فرض `uploads`) |
| `RECOMMENDATION_URL` | آدرس پایه سرویس توصیه‌گر (پیش‌فرض `http://recommendation:8000`؛ به‌صورت محلی `http://localhost:8000`) |
| `RECOMMENDATION_TIMEOUT_MS` | تایم‌اوت HTTP فراخوانی توصیه‌گر به میلی‌ثانیه (پیش‌فرض `1500`؛ در تست `500`) |

آپلود فایل به ۵۰ مگابایت در هر درخواست محدود شده است (`spring.servlet.multipart`).

---

## تست

مجموعه تست شامل موارد زیر است:

- **تست‌های کنترلر** با MockMvc برای همه کنترلرها از جمله `FeedController` (زمانی ۱۳ مورد و پیشنهادی ۱۳ مورد: صفحه‌بندی، احراز هویت، ۴۰۴، ۴۰۵، شکل یکسان `Page<PostResponse>`، نادیده‌گرفتن sort).
- **تست‌های واحد سرویس** برای Auth, User, Follow, Post, Reaction, Media، `Feed` (زمانی ۸ مورد و پیشنهادی ۱۰ مورد: هیدراته با حفظ ترتیب رتبه، fallback خالی/استثنا به زمانی، نادیده‌گرفتن `post_id` نامعتبر، فیلتر حذف‌شده، انتشار `UserNotFound`، فراداده total)، و `CustomUserDetailsService`.
- از H2 به عنوان پایگاه داده تست استفاده می‌شود، Flyway غیرفعال است و `recommendation.base-url=http://localhost:8000` در `src/test/resources/application.properties` شبیه‌سازی شده است.

اجرای تست‌ها از دایرکتوری `backend/`:

```
./mvnw test
```

---

## اجرای سرویس

سرویس کانتینری شده است. از ریشه مخزن:

```
docker compose up --build
```

این دستور PostgreSQL، بک‌اند اصلی (پورت `8080`) و سرویس پیشنهاددهی (پورت `8000`) را راه‌اندازی می‌کند. یک volume نام‌گذاری‌شده (`uploads`) فایل‌های رسانه را در طول راه‌اندازی مجدد کانتینرها حفظ می‌کند. همچنین می‌توانید با `./mvnw spring-boot:run` پس از تنظیم متغیرهای محیطی بالا، به‌صورت محلی اجرا کنید.

---

## وضعیت پیاده‌سازی

اجزای زیر همچنان **طراحی شده ولی هنوز در بک‌اند اصلی پیاده‌سازی نشده‌اند**:

- **تولید فید:** ✅ پیاده‌سازی‌شده — `GET /api/feed/chronological` (`deletedAt IS NULL ORDER BY createdAt DESC`) و `GET /api/feed/recommended` (`RecommendationClient` → `GET /feed?user_id=&page=&size=` → هیدراته از طریق `findAllByIdsFiltered` با حفظ ترتیب رتبه، تخریب مهربانانه به زمانی در صورت خالی/تایم‌اوت، شکل یکسان `Page<PostResponse>`) با ثبت `REQUEST_FEED` و `PageableDefault(size=20)`.
- **یکپارچه‌سازی با سرویس پیشنهاددهی:** ✅ پیاده‌سازی‌شده — `RestClientConfig` (`recommendation.base-url` / `RECOMMENDATION_URL`، تایم‌اوت ۱۵۰۰ میلی‌ثانیه)، `RecommendationClient`/`RecommendationResponse`/`RankedPost`، `docker-compose.yaml` با بررسی سلامت `GET /health`؛ به [سرویس توصیه‌گر](./6-Recommendation.md) مراجعه کنید.
- **مانیتورینگ:** Spring Boot Actuator به عنوان وابستگی وجود دارد اما پشته Prometheus/Grafana یا خروجی متریک متصل نشده است.
- **Redis:** در `docker-compose.yaml` حضور دارد اما هنوز توسط برنامه استفاده نمی‌شود.