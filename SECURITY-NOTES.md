# Security notes

- فقط HTTPS در Build production مجاز است.
- Bearer token در حافظه برنامه نگهداری می‌شود.
- برای ورود اثرانگشتی، token به صورت AES/GCM با کلید Android Keystore روی دستگاه رمز می‌شود.
- WebView و JavaScript bridge وجود ندارند.
- اطلاعات ورود داخل URL یا WebView تزریق نمی‌شوند.
- logout، توکن محلی رمزنگاری‌شده را حذف می‌کند.
