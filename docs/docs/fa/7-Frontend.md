# فرانت‌اند (وب‌کلاینت)

این سند پیاده‌سازی سرویس فرانت‌اند را شرح می‌دهد — وب‌کلاینت پلتفرم Sarv و اصلی‌ترین نقطه ورود کاربران به سیستم.

شامل پشته فناوری، ساختار پروژه، مسیریابی و چیدمان، احراز هویت و مدیریت نشست، لایه API، نماها و کامپوننت‌ها، مدیریت وضعیت، استایل و زبان بصری، مدیریت خطا، تست، پیکربندی و استقرار است و **وضعیت فعلی پیاده‌سازی** را بازتاب می‌دهد؛ مواردی که طراحی شده‌اند ولی هنوز به داده واقعی وصل نیستند در انتهای همین سند تحت «وضعیت پیاده‌سازی» فهرست شده‌اند.

> **نکته درباره نویسنده:** برخلاف هسته مرکزی (Core Backend) و سرویس توصیه‌گر که دستی نوشته شده‌اند، این سرویس **با استفاده از ایجنت‌های هوش مصنوعی** (تولید کد عامل‌محور) بر اساس مشخصات `frontend/Design.md` و قراردادهای API بک‌اند در [5-Backend.md](./5-Backend.md) نوشته شده است. سند طراحی مشخص می‌کند *ظاهر چگونه باشد*؛ این سند توضیح می‌دهد *در عمل چه چیزی ساخته شده است*.

---

## نمای کلی

فرانت‌اند یک تک‌صفحه‌ای (SPA) با TypeScript و Vue 3 و Vite است و مسئول موارد زیر است:

- ورود و ثبت‌نام دومرحله‌ای (نشست JWT)
- فید پست‌ها با دو زبانه **For You** (پیشنهادی) و **Latest** (زمانی)
- پست‌ها: مشاهده، ایجاد (متن و/یا رسانه)، لایک/دیسلایک با بازخورد متحرک
- پروفایل‌ها: مشاهده، ویرایش پروفایل خود، فهرست دنبال‌کنندگان/دنبال‌شوندگان، تاریخچه پست‌های لایک‌شده
- جست‌وجوی کاربر (با نام کاربری/نام نمایشی؛ زبانه‌های عمومی و محتوای پست رزرو شده‌اند)
- آپلود رسانه (با نوار پیشرفت) و نمایش رسانه/عکس پروفایل
- مدیریت نشست: ذخیره توکن، گاردهای احراز هویت، هدایت هنگام انقضای نشست

همه داده‌ها از REST API هسته مرکزی (`/api`، به [5-Backend.md](./5-Backend.md) مراجعه کنید) می‌آیند. فرانت‌اند هیچ وضعیت تجاری مستقلی نگه نمی‌دارد — بک‌اند مرجع حقیقت برای کاربران، پست‌ها، واکنش‌ها، دنبال‌کردن‌ها و رسانه‌هاست. زبان بصری (هندسه مربعی، هویت سبز ماتریکسی/هکری، انیمیشن فراوان) در `frontend/Design.md` تعریف شده است.

---

## پشته فناوری

| حوزه | فناوری |
|------|--------|
| زبان | TypeScript (سخت‌گیرانه، `vue-tsc`) |
| فریم‌ورک | Vue 3.5 (کامپوزیشن API، `<script setup>`، SFC) |
| بیلد / توسعه | Vite 8 (`@vitejs/plugin-vue`، `vite-plugin-vue-devtools`) |
| مسیریابی | `vue-router` نسخه 5 (history mode، نماهای lazy، گاردهای ناوبری) |
| وضعیت | `pinia` نسخه 4 (فقط استور احراز هویت؛ بقیه وضعیت محلی کامپوننت است) |
| HTTP | `axios` نسخه 1 (کلاینت مشترک `apiClient` با اینترسپتور) |
| انیمیشن | `gsap` نسخه 3 (ترنزیشن مودال، پیشرفت آپلود، افکت‌های لوگو/بازخورد) |
| ابزار کمکی | `@vueuse/core`، `@iconify/vue` + مجموعه آیکون پیکسلی محلی (`assets/icons/pixelarticons.ts`) |
| تست | Vitest 4 + `jsdom` + `@vue/test-utils` (یونیت)، Playwright نسخه 1 (سراسری، chromium/firefox/webkit) |
| لینت / قالب‌بندی | `oxlint` + `eslint` (+ `eslint-plugin-vue`، `@vue/eslint-config-typescript`)، `prettier` |
| سرو production | `nginx:1.27-alpine` (fallback تک‌صفحه‌ای + ریورس‌پراکسی `/api/`، به بخش «پیکربندی و استقرار» همین سند مراجعه کنید) |

---

## ساختار پروژه

ریشه سورس `frontend/src/` است:

```
api/          لایه HTTP — یک ماژول برای هر دامنه (client، auth، users، posts،
              feed، follows، reactions، media)
router/       جدول مسیرها + گاردهای احراز هویت (index.ts)
stores/       استورهای Pinia (فقط auth.ts — تنها استور مشترک)
types/        تایپ‌های منطبق با بک‌اند (api.ts: کاربر/پست/واکنش/رسانه، Page)
utils/        توکن (token.ts — کمک‌تابع‌های localStorage)
views/        صفحه‌های سطح مسیر (AppShell، Feed، Login، Register، Profile،
              LikedPosts، Following، Followers)
components/   رابط‌های قابل‌استفاده‌مجدد (LeftSidebar، RightSidebar، PostCard،
              PostCreateModal، SearchSection، UserSummary/List، NavigationMenu،
              SarvLogo، HotTopicsPanel، PlatformNewsPanel، AmbientNetwork،
              MobileTopBar، MobileBottomNav، AppIcon)
assets/       استایل پایه (main.css — توکن‌های طراحی) و icons/pixelarticons.ts
__tests__/    تست‌های یونیت نماها/استور/روتر؛ components/__tests__/ برای کامپوننت‌ها
App.vue       ریشه (<router-view> + بازیابی نشست)
main.ts       راه‌اندازی (pinia، روتر، هوک انقضای نشست، آیکون‌ها، css)
```

پیکربندی سطح بالا: `vite.config.ts` (نام مستعار `@`، پراکسی `/api` در توسعه)، `vitest.config.ts` (محیط jsdom، حذف `e2e/`)، `playwright.config.ts` (سرور dev/preview، سه مرورگر)، `nginx.conf`، `Dockerfile`، و مشخصات `e2e/`.

---

## مسیریابی و چیدمان

جدول مسیرها (`router/index.ts:5`):

| مسیر | نام | کامپوننت | دسترسی |
|------|-----|-----------|--------|
| `/login` | `login` | `LoginView.vue` | عمومی |
| `/register` | `register` | `RegisterView.vue` | عمومی |
| `/` | `feed` (فرزند `''`) | `AppShell.vue` ← `FeedView.vue` | احراز هویت |
| `/profile/:id?` | `profile` | `ProfileView.vue` | احراز هویت (`:id?` خالی یعنی خود کاربر) |
| `/liked` | `liked` | `LikedPostsView.vue` | احراز هویت |
| `/following` | `following` | `FollowingView.vue` | احراز هویت |
| `/followers` | `followers` | `FollowersView.vue` | احراز هویت |
| `/:pathMatch(.*)*` | — | هدایت به `login` | — |

گارد ناوبری (`router/index.ts:36`):

1. در مسیر نیازمند احراز هویت که توکن هست ولی کاربر لود نشده → `fetchMe()`؛ در صورت شکست `logout()` و هدایت به `login`.
2. در مسیر نیازمند احراز هویت بدون توکن → هدایت به `login` با حفظ `?redirect=` (به‌جز `/`).
3. در `login` در حالت احرازهویت‌شده → هدایت به `feed`.

چیدمان (`views/AppShell.vue:116`): پوسته احرازهویت‌شده یک گرید سه‌ستونه است — `LeftSidebar | router-view (وسط، بزرگ‌ترین) | RightSidebar` (`grid-template-columns: 300px minmax(0, 1fr) 320px` با باریک‌شدن در 1100 پیکسل). ستون وسط کانتینر اسکرول است. زیر 900 پیکسل سایدبار راست به کشوی لغزنده تبدیل می‌شود؛ زیر 640 پیکسل سایدبار چپ هم کشویی می‌شود (اسکریم + بستن با `Escape`) و `MobileTopBar` بالا و `MobileBottomNav` پایین قرار می‌گیرند. پست جدید یک `feedRefreshKey` تزریق‌شده را زیاد می‌کند تا `FeedView` دوباره واکشی کند (`AppShell.vue:11`).

---

## احراز هویت و نشست

گردش کار (`stores/auth.ts:9`، `api/auth.ts:17`، `utils/token.ts:1`):

1. `POST /api/auth/login` یک رشته JWT خام و `POST /api/auth/register` آبجکت `{...، token}` برمی‌گرداند. استور آن را در `localStorage` با کلید `sarv.jwt` ذخیره و برای پر کردن `user` صدای `GET /api/users/me` (`fetchMe`) را می‌زند.
2. `isAuthenticated` فقط از وجود توکن مشتق می‌شود (`stores/auth.ts:13`).
3. هر درخواست با اینترسپتور `apiClient` هدر `Authorization: Bearer <token>` می‌گیرد (`api/client.ts:25`).
4. توکن نامعتبر/منقضی از سمت Spring Security خطای `403` با بدنه خالی می‌دهد. اینترسپتور پاسخ (`api/client.ts:33`) آن را انقضای نشست می‌داند: توکن را پاک و هوک `onSessionExpired` متصل‌شده در `main.ts:19` را صدا می‌زند که خروج و هدایت به `login` انجام می‌دهد.
5. `App.vue:8` هنگام رفرش صفحه نشست را بازیابی می‌کند (اگر توکن هست ولی کاربر نیست، `fetchMe`).

رابط ورود (`views/LoginView.vue`): جعبه وسط‌چین، نام کاربری + رمز عبور؛ `401` یعنی «نام کاربری یا رمز عبور اشتباه است»، وگرنه `detail` بک‌اند نمایش داده می‌شود؛ موفقیت با `?redirect=` یا رفتن به `feed`.

رابط ثبت‌نام (`views/RegisterView.vue`، دومرحله‌ای مطابق `Design.md §§12`):

- **مرحله ۱ (اجباری):** `username`، `password` (حداقل ۸ کاراکتر، بررسی سمت کلاینت)، `email`، `displayName`، `gender` ← `auth.register()` ← رفتن به مرحله ۲.
- **مرحله ۲ (اختیاری):** `bio` (حداکثر ۲۵۵)، `location` (حداکثر ۳۰)، عکس پروفایل (`accept="image/*"`). اگر چیزی وارد شده باشد، ابتدا عکس با `POST /api/media` آپلود و سپس پروفایل با `PUT /api/users/me` ذخیره می‌شود؛ وگرنه هیچ درخواستی فرستاده نمی‌شود. دکمه **رد شدن** مستقیم به فید می‌رود.

---

## لایه API

`api/client.ts:21` نمونه `axios` با `baseURL: '/api'` می‌سازد (same-origin؛ در توسعه و production به بک‌اند پراکسی می‌شود، پس CORS یا متغیر محیطی فرانت‌اند لازم نیست). خطاها به `ApiError { status, title, detail, instance }` از `ProblemDetail` استاندارد RFC 9457 بک‌اند نرمال می‌شوند (`types/api.ts:90`).

| ماژول | توابع | اندپوینت‌های بک‌اند |
|--------|--------|---------------------|
| `api/auth.ts` | `login`، `register` | `POST /api/auth/login`، `POST /api/auth/register` |
| `api/users.ts` | `getMe`، `getUser`، `updateMe`، `searchUsers(query, pageable)` | `GET /api/users/me`، `GET /api/users/{id}`، `PUT /api/users/me`، `GET /api/users?query=` |
| `api/feed.ts` | `getChronologicalFeed`، `getRecommendedFeed` | `GET /api/feed/chronological`، `GET /api/feed/recommended` |
| `api/posts.ts` | `getPost`، `createPost`، `updatePost`، `deletePost` | `GET/POST /api/posts`، `PUT/DELETE /api/posts/{id}` |
| `api/reactions.ts` | `addReaction(1\|-1)`، `getReaction`، `removeReaction` | `POST/GET/DELETE /api/posts/{id}/reactions` |
| `api/follows.ts` | `getFollowers`، `getFollowing`، `follow`، `unfollow` | `GET/POST/DELETE /api/users/{id}/followers`، `GET /api/users/{id}/following` |
| `api/media.ts` | `uploadMedia(file, onProgress?)`، `getMediaBlob`، `getMediaMetadata` | `POST /api/media` (multipart با فیلد `file`)، `GET /api/media/{id}`، `GET /api/media/{id}/metadata` |

تایپ‌های `types/api.ts:4` فیلدبه‌فیلد با بک‌اند مطابق‌اند (`Gender`، `UserStatus`، `PostCategory`، `ReactionType`، `UserResponse`، `UserSummaryResponse`، `PostResponse`، `ReactionResponse`، `MediaResponse` و `Page<T>` با `page { size, number, totalElements, totalPages }`).

### رفتار فید

`FeedView.vue:36` دو زبانه **For You** (پیش‌فرض) و **Latest** دارد (`size = 20`، صفحه‌بندی «بارگذاری بیشتر» از روی `page.totalPages`):

- **Latest** ← مستقیم `GET /api/feed/chronological`.
- **For You** ← `GET /api/feed/recommended`؛ اگر صفحه اول خالی باشد یا درخواست خطا بدهد، خود نما **به زمانی برمی‌گردد** (`FeedView.vue:50`) — علاوه بر تخریب مهربانانه سمت بک‌اند (به [5-Backend.md](./5-Backend.md) مراجعه کنید). تعویض سریع زبانه با شمارنده توالی محافظت می‌شود تا پاسخ‌های قدیمی نادیده گرفته شوند.

### ایجاد پست (اول رسانه)

`PostCreateModal.vue:34` گردش‌کار `Design.md §8` را با فازهای صریح اجرا می‌کند (`idle ← uploading ← uploaded ← publishing` به‌علاوه `error`):

1. کاربر متن و/یا فایل انتخاب می‌کند (پیش‌نمایش با object URL).
2. **اول آپلود:** دکمه `⇪ upload media` صدای `POST /api/media` را با کال‌بک پیشرفت می‌زند که نوار پیشرفت پیکسلی GSAP را جلو می‌برد (`media.ts:8`، `PostCreateModal.vue:80`).
3. **بعد ثبت:** `createPost({ postCategory: 'NORMAL'، content، mediaId، parentId: null، repostOfId: null })`. دکمه ثبت تا وقتی متن یا `mediaId` آپلودشده نباشد غیرفعال است.

### واکنش‌ها، دنبال‌کردن‌ها، پروفایل‌ها، نمایش رسانه

- `PostCard.vue:73` هنگام mount وضعیت واکنش هر پست (`likeCount/dislikeCount/userReaction`)، پروفایل نویسنده، باینری آواتار و باینری رسانه پست را لود می‌کند؛ لایک یعنی شست بالا (سبز در حالت فعال)، دیسلایک یعنی شست پایین (قرمز در حالت فعال) با بازخورد پیکسلی شاد/غمگین GSAP پس از موفقیت (مطابق `Design.md §7.3`).
- `ProfileView.vue:42`: حذف `:id?` یعنی پروفایل خود کاربر؛ وضعیت دنبال‌کردن از صفحه اول فهرست دنبال‌شوندگان خود بیننده مشتق می‌شود (API فیلد `isFollowing` ندارد). پروفایل خودی فرم ویرایش دارد (`displayName`، `bio`، `location`، `gender`، آپلود آواتار ← `updateMe`)؛ فقط همین فیلدها قابل ویرایش‌اند.
- آواتارها و رسانه پست‌ها به‌صورت blob (`GET /api/media/{id}`) گرفته و با `URL.createObjectURL` نمایش داده می‌شوند و هنگام تعویض/unmount آزاد می‌شوند.

---

## نماها و کامپوننت‌ها

| صفحه | فایل | توضیح |
|------|------|--------|
| پوسته | `views/AppShell.vue` | چیدمان سه‌ستونه، کشوهای موبایل، میزبان مودال ایجاد پست |
| فید | `views/FeedView.vue` | زبانه‌های For You / Latest، حالت‌های تلاش‌مجدد + خالی + بارگذاری بیشتر |
| ورود / ثبت‌نام | `views/LoginView.vue`، `views/RegisterView.vue` | جعبه‌های احراز هویت وسط‌چین |
| پروفایل | `views/ProfileView.vue` | مشاهده + ویرایش خودی، دنبال‌کردن/لغو دنبال‌کردن |
| لایک‌شده‌ها / دنبال‌شوندگان / دنبال‌کنندگان | `views/LikedPostsView.vue`، `views/FollowingView.vue`، `views/FollowersView.vue` | فهرست‌های صفحه‌بندی‌شده `UserSummaryList` / پست |

| کامپوننت | نقش (ارجاع Design.md) |
|-----------|------------------------|
| `LeftSidebar.vue` + `SearchSection.vue`، `UserSummary.vue`، `NavigationMenu.vue` | جست‌وجو (بالا)، خلاصه کاربر، اکشن ایجاد پست، ناوبری پروفایل/لایک‌ها/دنبال‌کردن‌ها (§4) |
| `PostCard.vue`، `PostCreateModal.vue` | پست‌های فید، شمارنده‌ها، اکشن‌ها (§7)؛ پنجره ایجاد در همان صفحه (§8) |
| `RightSidebar.vue` + `SarvLogo.vue`، `HotTopicsPanel.vue`، `PlatformNewsPanel.vue` | نام متحرک Sarv، داغ‌ترین موضوعات، اخبار پلتفرم (§9) |
| `UserSummaryList.vue` | ردیف‌های مشترک آواتار/نام‌کاربری/نام‌نمایشی (§6) |
| `MobileTopBar.vue`، `MobileBottomNav.vue`، `AmbientNetwork.vue`، `AppIcon.vue` | کروم ریسپانسیو، افکت پس‌زمینه، آیکون‌های شست/جست‌وجو/کاربر |

جست‌وجو (`SearchSection.vue:33`): سه زبانه مطابق مشخصات — **username** زنده است (دیباونس ۳۰۰ میلی‌ثانیه، `GET /api/users?query=`، حداکثر ۸ نتیجه، کلیک ← پروفایل)؛ **general** و **post** نگهدارنده «به‌زودی» هستند (هنوز اندپوینت بک‌اند ندارند). نتایج در پنل همان صفحه باز می‌شوند مطابق `Design.md §4.1`.

داده سایدبار راست (`HotTopicsPanel.vue:7`، `PlatformNewsPanel.vue:8`): فعلاً **فهرست‌های ثابت نگهدارنده** (۵ تگ موضوعی، ۳ رکورد انتشار) — هنوز API تحلیل/موضوعات وجود ندارد. کلیک روی آن‌ها کاری نمی‌کند، مطابق قانون «کنترل‌های پیاده‌سازی‌نشده کاری نمی‌کنند» (`Design.md §13`).

---

## مدیریت وضعیت

فقط یک استور مشترک وجود دارد: `useAuthStore` (`stores/auth.ts:9` — شامل `token`، `user`، `isAuthenticated` و توابع `login/register/logout/fetchMe`). بقیه موارد (صفحه‌های فید، نتایج جست‌وجو، مودال‌ها، فرم‌ها، وضعیت دنبال‌کردن) وضعیت محلی `ref` داخل نماها/کامپوننت‌ها هستند و با props/emits یا تزریق `feedRefreshKey` منتقل می‌شوند. ماندگاری JWT یک لفاف نازک `localStorage` است (`utils/token.ts:1` با کلید `sarv.jwt`) — توکن تازه‌سازی یا ردیابی انقضا سمت کلاینت وجود ندارد.

---

## استایل و زبان بصری

`assets/main.css` توکن‌های طراحی Sarv را تعریف می‌کند (`--sarv-green`، `--sarv-panel/bg/border`، `--sarv-glow`، مقیاس فاصله‌گذاری) که همه استایل‌های scoped کامپوننت‌ها مصرف می‌کنند. پیاده‌سازی از قیدهای `frontend/Design.md` پیروی می‌کند: هندسه مربعی تیز (بدون کارت/دکمه گرد)، زیبایی ترمینالی سبزروی‌تیره، آیکون‌های پیکسل‌آرت (`assets/icons/pixelarticons.ts`، ثبت‌شده در `main.ts:11`) و حرکت مبتنی بر GSAP (ساخته‌شدن لوگو، باز/بسته‌شدن مودال، بازخورد لایک/دیسلایک، نوار اسکن آپلود). راهبرد ریسپانسیو سلسله‌مراتب «فید وسط بزرگ‌ترین است» را حفظ می‌کند: سایدبارها به‌جای فشرده‌کردن فید به کشو تبدیل می‌شوند (`AppShell.vue:173`، `Design.md §14`).

---

## مدیریت خطا و تجربه کاربری

- `ProblemDetail` بک‌اند ← `ApiError.detail` به‌صورت درون‌خطی نمایش داده می‌شود (فرم‌های احراز هویت، تلاش‌مجدد فید، وضعیت سازنده پست)؛ خطاهای ناشناخته/شبکه پیام عمومی می‌گیرند.
- فید: هنگام خالی‌بودن خطای تمام‌صفحه با دکمه `retry`، هنگام صفحه‌بندی خطای درون‌خطی با حفظ فهرست (`FeedView.vue:139`).
- سازنده پست: شکست آپلود و انتشار فاز `error` می‌سازد با پیام و مسدودشدن ثبت تا ریست.
- تصاویر/رسانه‌های ناموجود بی‌صدا به حالت آیکون/خالی برمی‌گردند (لود آواتار/رسانه catch و پاک می‌شود).
- رابط‌های پیاده‌سازی‌نشده (زبانه‌های general/post جست‌وجو، اکشن‌های بازنشر/نقل‌قول/نظر، ردیف‌های ثابت سایدبار) رندر می‌شوند ولی کاری نمی‌کنند — هیچ رفتار جعلی بک‌اند اختراع نمی‌شود.

---

## تست

| مجموعه | پوشش | دستور |
|--------|------|--------|
| یونیت (Vitest، jsdom) | `src/__tests__/` (استور احراز هویت، گاردهای روتر، لاگین/ثبت‌نام/پروفایل/فید/پوسته/سایدبار/موبایل) + `src/components/__tests__/` (PostCard، PostCreateModal، AppIcon، UserSummaryList) | `npm run test:unit` |
| سراسری (Playwright) | `e2e/vue.spec.ts`، `e2e/mobile.spec.ts`، `e2e/sticky-tabs.spec.ts` (کرومیوم، فایرفاکس، وب‌کیت؛ سرور dev محلی، preview در CI) | `npm run test:e2e` |
| بررسی تایپ | `vue-tsc --build` (بخشی از `npm run build`) | `npm run type-check` |
| لینت | `oxlint` + `eslint` (قوانین Vue و TS) | `npm run lint` |

---

## پیکربندی و استقرار

فرانت‌اند به متغیر محیطی نیاز ندارد — با `/api` هم‌مبدأ صحبت می‌کند:

- **توسعه:** `vite.config.ts:20` مسیر `/api ← http://localhost:8080` (بک‌اند روی هاست) را پراکسی می‌کند. `npm install` ← `npm run dev` (پورت 5173).
- **production:** `Dockerfile` چندمرحله‌ای (بیلد `node:24-alpine` ← ران‌تایم `nginx:1.27-alpine` برای سرو `dist/`). `nginx.conf:20` مسیر `/api/ ← http://core_backend:8080` را پراکسی می‌کند با fallback تک‌صفحه‌ای (`try_files … /index.html`)، `gzip` و کش immutable برای فایل‌های هش‌دار (`index.html` هرگز کش نمی‌شود).
- **کامپوز:** `docker-compose.yaml` سرویس `frontend` را می‌سازد (`3000:80` با `depends_on: core_backend`)؛ بک‌اند روی `8080` و توصیه‌گر روی `8000` می‌مانند.

```
docker compose up --build
# frontend ← http://localhost:3000 ، backend ← http://localhost:8080
```

---

## وضعیت پیاده‌سازی

**پیاده‌سازی‌شده:** احراز هویت (ورود، ثبت‌نام دومرحله‌ای شامل آواتار/بیو/موقعیت اختیاری)، گاردها + انقضای نشست، فید For You/Latest با fallback سمت کلاینت، ایجاد پست (اول رسانه + پیشرفت)، لایک/دیسلایک با شمارنده + بازخورد، مشاهده/ویرایش پروفایل، صفحه‌های دنبال‌کنندگان/دنبال‌شوندگان/لایک‌شده‌ها، جست‌وجوی زنده نام کاربری، رندر blob آواتار/رسانه، پوسته ریسپانسیو + کشوها، مجموعه‌های یونیت + سراسری، استقرار Docker/nginx.

**طراحی‌شده ولی هنوز وصل‌نشده (رابط هست، کاری نمی‌کند یا نگهدارنده نشان می‌دهد):**

- زبانه‌های عمومی و جست‌وجوی محتوای پست (`SearchSection.vue:174` با پیام «به‌زودی» — اندپوینت بک‌اند ندارد).
- محتوای موضوعات داغ و اخبار پلتفرم (فهرست‌های ثابت در `HotTopicsPanel.vue:7` و `PlatformNewsPanel.vue:8` — API تحلیل/موضوعات برنامه‌ریزی‌شده و ساخته‌نشده است).
- اکشن‌های بازنشر / نقل‌قول / نظر روی پست‌ها (دکمه‌ها مطابق `Design.md §7.4` رندر می‌شوند؛ فقط ایجاد `NORMAL` پیاده شده است).
- ویرایش/حذف پست از رابط کاربری (لافاصله‌های API در `api/posts.ts:27` هست ولی دکمه‌ای در UI نیست).

همچنین به [5-Backend.md](./5-Backend.md)، [3-Architecture.md](./3-Architecture.md) و [6-Recommendation.md](./6-Recommendation.md) مراجعه کنید.
