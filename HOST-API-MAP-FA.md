# نقشه API واقعی نسخه 3.6.1

این نسخه فقط از endpointهای موجود در سورس هاست ارسالی استفاده می‌کند و هیچ `/api/staff/mobile/...` ندارد.

| قابلیت | API واقعی |
|---|---|
| ورود | `POST /api/staff/login` |
| حساب جاری | `GET /api/staff/me` |
| داشبورد | `GET /api/teacher/dashboard` |
| کلاس/تخصیص/دانش‌آموز | `GET /api/teacher/workspace` |
| عکس دانش‌آموز | `GET /api/workbench/students/:id/profile` |
| پرونده دانش‌آموز | `GET /api/workbench/students/:id/dossier` |
| دفتر نمره | `GET /api/staff/gradebook` |
| تأیید مستمر | `POST /api/staff/gradebook/approve-continuous` |
| ثبت پایانی | `POST /api/staff/gradebook/final-mark` |
| state محدود معلم | `GET/POST /api/state` |
| دفتر کلاسی | `lessonLogs` در `/api/state` با کنترل scope سرور |
| ساخت/ویرایش تکلیف | `POST /api/staff/homework/p652/save` |
| تحویل‌های تکلیف | `homeworkSubmissions` در `/api/state` |
| تصحیح تکلیف | `POST /api/staff/homework/grade` |
| صف/جزئیات برگه آزمون | `submissions/exams/grades` در `/api/state` |
| نهایی‌سازی تصحیح آزمون | `POST /api/staff/exam-grading/p6722` |
| حضور حرفه‌ای | `POST /api/staff/attendance/program-save` |
