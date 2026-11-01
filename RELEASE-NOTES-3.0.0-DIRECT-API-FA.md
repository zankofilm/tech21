# Release 3.0.0 — Direct API

این نسخه بازنویسی معماری بعد از شکست روش DOM/WebView است.

- حذف `NativeBridgeActivity`
- حذف `native-bridge.js` و `auth-bridge.js`
- حذف dependency `androidx.webkit`
- افزودن `ApiClient.kt`
- افزودن `SecureTokenStore.kt`
- اتصال مستقیم به APIهای واقعی `server.mjs`
- اصلاح API base بر اساس `APP_BASE_PATH=/app`
- Login/MFA/Password Change Native
- Dashboard/Workspace/Attendance/Gradebook/Messages Native
- تست runtime API با خود سورس هاست
