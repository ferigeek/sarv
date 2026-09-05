# فرانت‌اند (وب‌کلاینت)

این سند پیاده‌سازی سرویس فرانت‌اند را شرح می‌دهد — وب‌کلاینت پلتفرم Sarv و اصلی‌ترین نقطه ورود کاربران به سیستم.

شامل پشته فناوری، ساختار پروژه، مسیریابی و چیدمان، احراز هویت و مدیریت نشست، لایه API، نماها و کامپوننت‌ها، مدیریت وضعیت، استایل و زبان بصری، مدیریت خطا، تست، پیکربندی و استقرار است و **وضعیت فعلی پیاده‌سازی** را بازتاب می‌دهد؛ مواردی که طراحی شده‌اند ولی هنوز به داده واقعی وصل نیستند در انتهای همین سند تحت «وضعیت پیاده‌سازی» فهرست شده‌اند.

> **نکته درباره نویسنده:** برخلاف هسته مرکزی (Core Backend) و سرویس توصیه‌گر که دستی نوشته شده‌اند، این سرویس **با استفاده از ایجنت‌های هوش مصنوعی** (تولید کد عامل‌محور) بر اساس مشخصات `frontend/Design.md` و قراردادهای API بک‌اند در [5-Backend.md](./5-Backend.md) نوشته شده است. سند طراحی مشخص می‌کند *ظاهر چگونه باشد*؛ این سند توضیح می‌دهد *در عمل چه چیزی ساخته شده است*.

---

## نمای کلی

فرانت‌اند یک تک‌صفحه‌ای (SPA) با TypeScript و Vue 3 و Vite است و مسئول موارد زیر است:

- ورود و ثبت‌نام دومرحله‌ای (نشست JWT، تأیید تکرار رمز عبور)
- فید پست‌ها با دو زبانه **For You** (پیشنهادی) و **Latest** (زمانی)
- پست‌ها: صفحه جزئیات با رشته نظرات، ایجاد (معمولی/نظر/نقل‌قول/بازنشر، متن و/یا رسانه)، لایک/دیسلایک با بازخورد متحرک
- هویت دسته‌بندی: بنرهای آبی ترمینالی و پیش‌نمایش پست اصلی برای پست‌های نظر/بازنشر/نقل‌قول
- پروفایل‌ها: مشاهده، آمار دنبال‌کردن، فهرست پست‌های خود، ویرایش پروفایل خود، فهرست دنبال‌کنندگان/دنبال‌شوندگان هر کاربر، تاریخچه واکنش‌ها (فیلتر لایک/دیسلایک/همه)
- جست‌وجو در مودال وسط صفحه: زبانه‌های عمومی ترکیبی، نام کاربری/نام نمایشی و محتوای پست
- آپلود رسانه (با نوار پیشرفت) و نمایش رسانه/عکس پروفایل، از جمله نمایش تاشوی رسانه در پیش‌نمایش‌های جاسازی‌شده
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
views/        صفحه‌های سطح مسیر (AppShell، Feed، PostDetail، Login، Register،
              Profile، LikedPosts، Following، Followers)
components/   رابط‌های قابل‌استفاده‌مجدد (LeftSidebar، RightSidebar، PostCard،
              PostCreateModal، RepostConfirm، SearchSection، SearchModal،
              UserSummary/List، NavigationMenu، SarvLogo، HotTopicsPanel،
              PlatformNewsPanel، AmbientNetwork، MobileTopBar، MobileBottomNav،
              AppIcon)
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
| `/post/:id` | `post-detail` | `PostDetailView.vue` | احراز هویت |
| `/profile/:id?` | `profile` | `ProfileView.vue` | احراز هویت (`:id?` خالی یعنی خود کاربر) |
| `/liked` | `liked` | `LikedPostsView.vue` | احراز هویت |
| `/following/:id?` | `following` | `FollowingView.vue` | احراز هویت (`:id?` خالی یعنی خود کاربر) |
| `/followers/:id?` | `followers` | `FollowersView.vue` | احراز هویت (`:id?` خالی یعنی خود کاربر) |
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

- **مرحله ۱ (اجباری):** `username`، `password` (حداقل ۸ کاراکتر، بررسی سمت کلاینت)، `confirmPassword` (باید با رمز مطابقت داشته باشد، بررسی سمت کلاینت؛ بک‌اند مغایرت را با ۴۰۰ رد می‌کند)، `email`، `displayName`، `gender` ← `auth.register()` ← رفتن به مرحله ۲.
- **مرحله ۲ (اختیاری):** `bio` (حداکثر ۲۵۵)، `location` (حداکثر ۳۰)، عکس پروفایل (`accept="image/*"` با انتخاب‌گر استایل‌شده و پیش‌نمایش زنده، نه کنترل خام مرورگر). اگر چیزی وارد شده باشد، ابتدا عکس با `POST /api/media` آپلود و سپس پروفایل با `PUT /api/users/me` ذخیره می‌شود؛ وگرنه هیچ درخواستی فرستاده نمی‌شود. دکمه **رد شدن** مستقیم به فید می‌رود.

---

## لایه API

`api/client.ts:21` نمونه `axios` با `baseURL: '/api'` می‌سازد (same-origin؛ در توسعه و production به بک‌اند پراکسی می‌شود، پس CORS یا متغیر محیطی فرانت‌اند لازم نیست). خطاها به `ApiError { status, title, detail, instance }` از `ProblemDetail` استاندارد RFC 9457 بک‌اند نرمال می‌شوند (`types/api.ts:90`).

| ماژول | توابع | اندپوینت‌های بک‌اند |
|--------|--------|---------------------|
| `api/auth.ts` | `login`، `register` | `POST /api/auth/login`، `POST /api/auth/register` |
| `api/users.ts` | `getMe`، `getUser`، `updateMe`، `searchUsers(query, pageable)`، `getUserPosts`، `getReactedPosts(filter)`، `getUserStats` | `GET /api/users/me`، `GET /api/users/{id}`، `PUT /api/users/me`، `GET /api/users?query=`، `GET /api/users/{id}/posts`، `GET /api/users/{id}/reacted-posts?filter=`، `GET /api/users/{id}/stats` |
| `api/feed.ts` | `getChronologicalFeed`، `getRecommendedFeed` | `GET /api/feed/chronological`، `GET /api/feed/recommended` |
| `api/posts.ts` | `getPost`، `createPost`، `updatePost`، `deletePost`، `searchPosts`، `getComments`، `repostPost`، `quotePost` | `GET/POST /api/posts`، `PUT/DELETE /api/posts/{id}`، `GET /api/posts/search?query=`، `GET /api/posts/{id}/comments?sortBy=`، بازنشر/نقل‌قول با `POST /api/posts` |
| `api/reactions.ts` | `addReaction(1\|-1)`، `getReaction`، `removeReaction` | `POST/GET/DELETE /api/posts/{id}/reactions` |
| `api/follows.ts` | `getFollowers`، `getFollowing`، `follow`، `unfollow` | `GET/POST/DELETE /api/users/{id}/followers`، `GET /api/users/{id}/following` |
| `api/media.ts` | `uploadMedia(file, onProgress?)`، `getMediaBlob`، `getMediaMetadata` | `POST /api/media` (multipart با فیلد `file`)، `GET /api/media/{id}`، `GET /api/media/{id}/metadata` |

تایپ‌های `types/api.ts:4` فیلدبه‌فیلد با بک‌اند مطابق‌اند (`Gender`، `UserStatus`، `PostCategory`، `CommentSort`، `ReactionFilter`، `UserResponse`، `UserSummaryResponse`، `UserStatsResponse`، `PostResponse` شامل `commentCount`، `ReactionResponse`، `MediaResponse` و `Page<T>` با `page { size, number, totalElements, totalPages }`).

### رفتار فید

`FeedView.vue:36` دو زبانه **For You** (پیش‌فرض) و **Latest** دارد (`size = 20`، صفحه‌بندی «بارگذاری بیشتر» از روی `page.totalPages`):

- **Latest** ← مستقیم `GET /api/feed/chronological`.
- **For You** ← `GET /api/feed/recommended`؛ اگر صفحه اول خالی باشد یا درخواست خطا بدهد، خود نما **به زمانی برمی‌گردد** (`FeedView.vue:50`) — علاوه بر تخریب مهربانانه سمت بک‌اند (به [5-Backend.md](./5-Backend.md) مراجعه کنید). تعویض سریع زبانه با شمارنده توالی محافظت می‌شود تا پاسخ‌های قدیمی نادیده گرفته شوند.

### ایجاد پست (اول رسانه)

`PostCreateModal.vue:34` گردش‌کار `Design.md §8` را با فازهای صریح اجرا می‌کند (`idle ← uploading ← uploaded ← publishing` به‌علاوه `error`):

1. کاربر متن و/یا فایل انتخاب می‌کند (پیش‌نمایش با object URL).
2. **اول آپلود:** دکمه `⇪ upload media` صدای `POST /api/media` را با کال‌بک پیشرفت می‌زند که نوار پیشرفت پیکسلی GSAP را جلو می‌برد (`media.ts:8`، `PostCreateModal.vue:80`).
3. **بعد ثبت:** `createPost({ postCategory: 'NORMAL'، content، mediaId، parentId: null، repostOfId: null })`. دکمه ثبت تا وقتی متن یا `mediaId` آپلودشده نباشد غیرفعال است.

سازنده سه حالت دارد (`PostCreateModal.vue:12` — همان پنجره، همان گردش اول-رسانه):

- **post** (پیش‌فرض): پست `NORMAL` مثل بالا.
- **comment** (با `parentId`، بازشونده از صفحه جزئیات پست): `COMMENT` با `parentId` و بدون `repostOfId`.
- **quote** (با `repostOfId`، بازشونده از دکمه نقل‌قول کارت): پیش‌نمایش غیرتعاملی پست نقل‌شده در بالا، بعد متن (اجباری — بک‌اند نقل‌قول خالی را رد می‌کند) به‌علاوه رسانه اختیاری با کمک‌تابع `quotePost`.

### صفحه جزئیات پست و نظرات

`PostDetailView.vue` پست را با `PostCard` در حالت `detailed` نشان می‌دهد و زیر آن رشته نظرات است: `GET /api/posts/{id}/comments?sortBy=` با کلید **جدیدترین** (پیش‌فرض) / **پُرلایک‌ترین**، صفحه‌بندی «بارگذاری بیشتر» (`size = 10`)، حالت خالی دعوت‌کننده به اولین نظر، و دکمه نوشتن نظر که سازنده را در حالت نظر باز می‌کند (پس از انتشار، پست و نظرات تازه می‌شوند). کلیک روی بدنه کارت فید (یا دکمه نظرش) به اینجا می‌آید؛ لایک/دیسلایک، لینک نویسنده و رسانه جلوی انتشار کلیک را می‌گیرند.

### بازنشر

دکمه بازنشر کارت پنجره تأیید `RepostConfirm.vue` را باز می‌کند (قطعه‌ای از پست، دکمه‌های `repost`/`cancel`، بستن با `Escape`/پس‌زمینه) و تأیید، `postCategory: 'REPOST'` با محتوای null را با `repostPost` می‌فرستد. موفقیت دکمه را آبی ترمینالی می‌کند و رویداد `reposted` می‌دهد تا فید تازه شود؛ خطاها در همان پنجره می‌مانند با امکان تلاش مجدد.

### واکنش‌ها، دنبال‌کردن‌ها، پروفایل‌ها، نمایش رسانه

- `PostCard.vue:73` هنگام mount وضعیت واکنش هر پست (`likeCount/dislikeCount/userReaction`)، پروفایل نویسنده، باینری آواتار و باینری رسانه پست را لود می‌کند؛ لایک یعنی شست بالا (سبز در حالت فعال)، دیسلایک یعنی شست پایین (قرمز در حالت فعال) با بازخورد پیکسلی شاد/غمگین GSAP پس از موفقیت (مطابق `Design.md §7.3`). سربرگ نویسنده به `profile/:userId` می‌رود؛ بدنه کارت و دکمه نظر به `post-detail` می‌روند.
- هویت دسته‌بندی: پست‌های `COMMENT`/`REPOST`/`QUOTE` نواری آبی ترمینالی (`--sarv-blue`) در بالای کارت دارند که به پست والد/ارجاع‌شده لینک می‌شود؛ کارت‌های بازنشر/نقل‌قول پیش‌نمایش تک‌سطحی از پست اصلی دارند (نویسنده، قطعه متن، شمارنده‌ها) با پیام جایگزین «پست اصلی در دسترس نیست» و کلید «نمایش رسانه پیوست» که تصویر/ویدیوی اصلی را در همان‌جا باز می‌کند، وقتی رسانه دارد.
- `ProfileView.vue:42`: حذف `:id?` یعنی پروفایل خود کاربر؛ وضعیت دنبال‌کردن از صفحه اول فهرست دنبال‌شوندگان خود بیننده مشتق می‌شود (API فیلد `isFollowing` ندارد). سربرگ آمار دنبال‌کردن (`GET /api/users/{id}/stats`) را نشان می‌دهد که به فهرست‌های دنبال‌کنندگان/دنبال‌شوندگان همان کاربر لینک‌اند و بعد از آن فهرست صفحه‌بندی‌شده پست‌های خود کاربر (`GET /api/users/{id}/posts` با استفاده مجدد از `PostCard`) می‌آید. پروفایل خودی فرم ویرایش دارد (`displayName`، `bio`، `location`، `gender`، آواتار با انتخاب‌گر استایل‌شده و پیش‌نمایش زنده ← `updateMe`)؛ فقط همین فیلدها قابل ویرایش‌اند.
- `LikedPostsView.vue` تاریخچه پست‌های واکنش‌نشان‌داده‌شده است (`GET /api/users/{id}/reacted-posts`) با زبانه‌های **لایک‌شده** (پیش‌فرض) / **دیسلایک‌شده** / **همه**؛ برچسب آیتم ناوبری «recent reactions» است.
- آواتارها و رسانه پست‌ها به‌صورت blob (`GET /api/media/{id}`) گرفته و با `URL.createObjectURL` نمایش داده می‌شوند و هنگام تعویض/unmount آزاد می‌شوند.

---

## نماها و کامپوننت‌ها

| صفحه | فایل | توضیح |
|------|------|--------|
| پوسته | `views/AppShell.vue` | چیدمان سه‌ستونه، کشوهای موبایل، میزبان مودال ایجاد پست |
| فید | `views/FeedView.vue` | زبانه‌های For You / Latest، حالت‌های تلاش‌مجدد + خالی + بارگذاری بیشتر |
| جزئیات پست | `views/PostDetailView.vue` | پست + رشته نظرات با کلید مرتب‌سازی، سازنده نظر |
| ورود / ثبت‌نام | `views/LoginView.vue`، `views/RegisterView.vue` | جعبه‌های احراز هویت وسط‌چین |
| پروفایل | `views/ProfileView.vue` | آمار، پست‌های خود، مشاهده + ویرایش خودی، دنبال‌کردن/لغو دنبال‌کردن |
| واکنش‌ها / دنبال‌شوندگان / دنبال‌کنندگان | `views/LikedPostsView.vue`، `views/FollowingView.vue`، `views/FollowersView.vue` | فید پست‌های واکنش‌نشان‌داده‌شده با فیلتر؛ فهرست‌های دنبال‌کردن هر کاربر |

| کامپوننت | نقش (ارجاع Design.md) |
|-----------|------------------------|
| `LeftSidebar.vue` + `SearchSection.vue`، `SearchModal.vue`، `UserSummary.vue`، `NavigationMenu.vue` | محرک‌های جست‌وجو + مودال نتایج وسط‌چین، خلاصه کاربر، اکشن ایجاد پست، ناوبری خانه/پروفایل/واکنش‌ها/دنبال‌کردن‌ها (§4) |
| `PostCard.vue`، `PostCreateModal.vue`، `RepostConfirm.vue` | پست‌های فید، بنرها، پیش‌نمایش‌ها، شمارنده‌ها، اکشن‌ها (§7)؛ پنجره‌های ایجاد/نظر/نقل‌قول و تأیید بازنشر در همان صفحه (§8) |
| `RightSidebar.vue` + `SarvLogo.vue`، `HotTopicsPanel.vue`، `PlatformNewsPanel.vue` | نام متحرک Sarv، داغ‌ترین موضوعات، اخبار پلتفرم (§9) |
| `UserSummaryList.vue` | ردیف‌های مشترک آواتار/نام‌کاربری/نام‌نمایشی با کلیک به پروفایل (§6) |
| `MobileTopBar.vue`، `MobileBottomNav.vue`، `AmbientNetwork.vue`، `AppIcon.vue` | کروم ریسپانسیو، افکت پس‌زمینه، آیکون‌های شست/جست‌وجو/کاربر/خانه/تصویر |

بلوک برند بالای سایدبار چپ (لوگو + شعار) به فید می‌رود، مثل آیتم پررنگ `home` ناوبری با آیکونش.

جست‌وجو (`SearchModal.vue`، بازشونده از ورودی/زبانه‌های سایدبار): پنجره‌ای ثابت روی مرکز صفحه با همان سه زبانه — **username** (`GET /api/users?query=`، حداکثر ۸ نتیجه، کلیک ← پروفایل)، **post** (`GET /api/posts/search?query=`، ردیف‌های کوچک، کلیک ← جزئیات پست)، و **general** که هر دو را ترکیب می‌کند با میان‌برهای «دیدن همه». دیباونس ۳۰۰ میلی‌ثانیه، بستن با `Escape`/پس‌زمینه/دکمه بستن، آواتار برای ردیف‌های کاربری.

داده سایدبار راست (`HotTopicsPanel.vue:7`، `PlatformNewsPanel.vue:8`): فعلاً **فهرست‌های ثابت نگهدارنده** (۵ تگ موضوعی، ۳ رکورد انتشار) — هنوز API تحلیل/موضوعات وجود ندارد. کلیک روی آن‌ها کاری نمی‌کند، مطابق قانون «کنترل‌های پیاده‌سازی‌نشده کاری نمی‌کنند» (`Design.md §13`).

---

## مدیریت وضعیت

فقط یک استور مشترک وجود دارد: `useAuthStore` (`stores/auth.ts:9` — شامل `token`، `user`، `isAuthenticated` و توابع `login/register/logout/fetchMe`). بقیه موارد (صفحه‌های فید، نتایج جست‌وجو، مودال‌ها، فرم‌ها، وضعیت دنبال‌کردن) وضعیت محلی `ref` داخل نماها/کامپوننت‌ها هستند و با props/emits یا تزریق `feedRefreshKey` منتقل می‌شوند. ماندگاری JWT یک لفاف نازک `localStorage` است (`utils/token.ts:1` با کلید `sarv.jwt`) — توکن تازه‌سازی یا ردیابی انقضا سمت کلاینت وجود ندارد.

---

## استایل و زبان بصری

`assets/main.css` توکن‌های طراحی Sarv را تعریف می‌کند (`--sarv-green`، `--sarv-blue` برای هویت دسته‌بندی پست، `--sarv-panel/bg/border`، `--sarv-glow`، مقیاس فاصله‌گذاری) که همه استایل‌های scoped کامپوننت‌ها مصرف می‌کنند. پیاده‌سازی از قیدهای `frontend/Design.md` پیروی می‌کند: هندسه مربعی تیز (بدون کارت/دکمه گرد)، زیبایی ترمینالی سبزروی‌تیره، آیکون‌های پیکسل‌آرت (`assets/icons/pixelarticons.ts`، ثبت‌شده در `main.ts:11`) و حرکت مبتنی بر GSAP (ساخته‌شدن لوگو، باز/بسته‌شدن مودال، بازخورد لایک/دیسلایک، نوار اسکن آپلود). راهبرد ریسپانسیو سلسله‌مراتب «فید وسط بزرگ‌ترین است» را حفظ می‌کند: سایدبارها به‌جای فشرده‌کردن فید به کشو تبدیل می‌شوند (`AppShell.vue:173`، `Design.md §14`).

---

## مدیریت خطا و تجربه کاربری

- `ProblemDetail` بک‌اند ← `ApiError.detail` به‌صورت درون‌خطی نمایش داده می‌شود (فرم‌های احراز هویت، تلاش‌مجدد فید، وضعیت سازنده پست)؛ خطاهای ناشناخته/شبکه پیام عمومی می‌گیرند.
- فید: هنگام خالی‌بودن خطای تمام‌صفحه با دکمه `retry`، هنگام صفحه‌بندی خطای درون‌خطی با حفظ فهرست (`FeedView.vue:139`).
- سازنده پست: شکست آپلود و انتشار فاز `error` می‌سازد با پیام و مسدودشدن ثبت تا ریست.
- تصاویر/رسانه‌های ناموجود بی‌صدا به حالت آیکون/خالی برمی‌گردند (لود آواتار/رسانه catch و پاک می‌شود).
- رابط‌های پیاده‌سازی‌نشده (ردیف‌های ثابت سایدبار) رندر می‌شوند ولی کاری نمی‌کنند — هیچ رفتار جعلی بک‌اند اختراع نمی‌شود.

---

## تست

| مجموعه | پوشش | دستور |
|--------|------|--------|
| یونیت (Vitest، jsdom) | `src/__tests__/` (استور احراز هویت، گاردهای روتر، لاگین/ثبت‌نام/پروفایل/فید/جزئیات‌پست/واکنش‌ها/پوسته/سایدبار/موبایل) + `src/components/__tests__/` (PostCard، PostCreateModal، RepostConfirm، AppIcon، UserSummaryList) | `npm run test:unit` |
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

**پیاده‌سازی‌شده:** احراز هویت (ورود، ثبت‌نام دومرحله‌ای شامل تأیید رمز و آواتار/بیو/موقعیت اختیاری)، گاردها + انقضای نشست، فید For You/Latest با fallback سمت کلاینت، ایجاد پست (اول رسانه + پیشرفت، حالت‌های معمولی/نظر/نقل‌قول)، صفحه جزئیات پست با رشته نظرات + مرتب‌سازی + سازنده، بازنشر با تأیید، بنرهای دسته‌بندی + پیش‌نمایش پست اصلی با کلید رسانه، لایک/دیسلایک با شمارنده‌ها (شامل شمارنده نظر) + بازخورد، مشاهده/ویرایش پروفایل با آمار دنبال‌کردن + پست‌های خود، فهرست‌های دنبال‌کنندگان/دنبال‌شوندگان هر کاربر، تاریخچه پست‌های واکنش‌نشان‌داده‌شده با فیلتر، مودال جست‌وجوی کامل سه‌زبانه، رندر blob آواتار/رسانه، انتخاب‌گرهای استایل‌شده عکس، پوسته ریسپانسیو + کشوها، مجموعه‌های یونیت + سراسری، استقرار Docker/nginx.

**طراحی‌شده ولی هنوز وصل‌نشده (رابط هست، کاری نمی‌کند یا نگهدارنده نشان می‌دهد):**

- محتوای موضوعات داغ و اخبار پلتفرم (فهرست‌های ثابت در `HotTopicsPanel.vue:7` و `PlatformNewsPanel.vue:8` — API تحلیل/موضوعات برنامه‌ریزی‌شده و ساخته‌نشده است).
- ویرایش/حذف پست از رابط کاربری (لافاصله‌های API در `api/posts.ts:27` هست ولی دکمه‌ای در UI نیست).

همچنین به [5-Backend.md](./5-Backend.md)، [3-Architecture.md](./3-Architecture.md) و [6-Recommendation.md](./6-Recommendation.md) مراجعه کنید.
