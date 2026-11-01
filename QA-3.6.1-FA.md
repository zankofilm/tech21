# QA نسخه 3.6.1 Host Compatible

## Runtime روی سورس هاست ارسالی
- Login: HTTP 200، حدود 0.09s در محیط محلی
- `/api/staff/me`: HTTP 200
- `/api/teacher/dashboard`: HTTP 200
- `/api/teacher/workspace`: HTTP 200
- `/api/state`: HTTP 200
- پروفایل دانش‌آموز: HTTP 200
- پرونده دانش‌آموز: HTTP 200
- دفتر نمره: HTTP 200
- ذخیره دفتر کلاسی از `/api/state`: HTTP 200 + read-back موفق
- ساخت تکلیف معتبر: HTTP 200
- ثبت مستمر معتبر: HTTP 200
- ثبت پایانی معتبر: HTTP 200

## ممیزی مسیرها
تمام خانواده‌های endpoint استفاده‌شده در سورس Android در runtime هاست probe شدند. هیچ‌کدام پاسخ generic «مسیر امنیت کارکنان پیدا نشد» یا «مسیر اپ معلم پیدا نشد» ندادند؛ مسیرهایی که داده لازم نداشتند پاسخ اعتبارسنجی 400/403/404 اختصاصی دادند، یعنی route وجود دارد.

## استاتیک
- WebView / NativeBridge در `app/src`: صفر
- `/api/staff/mobile/...` در کد اجرایی: صفر
- خطای parser صریح Kotlin (`expecting` / `unexpected tokens`): صفر
- Build کامل Android در GitHub Actions لازم است.
