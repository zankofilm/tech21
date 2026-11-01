# وضعیت پروژه 3.6.1

- معماری: Native Jetpack Compose + Direct API
- WebView: صفر
- Patch اجباری هاست: ندارد
- endpoint جعلی `/api/staff/mobile/...`: صفر
- ورود/MFA: مستقیم روی `/api/staff/login`
- قفل بیومتریک: بازشدن فوری UI پس از تأیید محلی، اعتبارسنجی نشست در پس‌زمینه
- پنج ماژول روزانه: روی APIهای واقعی هاست بازنویسی شده‌اند
- تست runtime محلی با خود `server.mjs` ارسالی: موفق
- Build کامل APK: توسط GitHub Actions انجام می‌شود؛ Android SDK/Gradle کامل در محیط محلی موجود نیست.
