# QA نسخه 3.6.0

- XML: OK
- Workflow YAML: OK (در محیط QA با parser موجود)
- `server.mjs`: `node --check` OK
- ساختار delimiter همه فایل‌های Kotlin: OK
- WebView/NativeBridge در سورس اجرایی: صفر
- پنج صفحه جدید و شش endpoint تجمیعی: موجود
- smoke test runtime دفتر کلاسی: POST=200 و GET=200
- smoke test runtime endpointهای Students/Gradebook/Homework/Grading Queue: HTTP 200
- Build کامل Android: در این محیط Gradle نصب نیست؛ Workflow GitHub برای Gradle 8.9 + Android 35 آماده است.
