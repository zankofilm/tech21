# نگاشت امکانات پنل معلم به اپ Native — V3.2

| بخش Native | منبع/قابلیت هاست | API اصلی |
|---|---|---|
| داشبورد | teacher dashboard | `/api/teacher/dashboard` |
| کتاب‌ها و کلاس‌های من | myTeaching | `/api/teacher/workspace` |
| آموزش و فصل‌ها | curriculum | `/api/staff/teacher-approved-questions/p67` |
| کلاس و دانش‌آموزان | classroom | `/api/teacher/workspace` |
| برنامه هفتگی | teacher schedule | `/api/teacher/dashboard` |
| کلاس آنلاین | online learning | `/api/teacher/online-class/session/start` |
| حضور و غیاب | attendance | `/api/teacher/attendance/submit` |
| حضور و انضباط | attendanceDiscipline | `/api/staff/attendance-discipline`, `/api/staff/discipline/record` |
| دفتر نمره | gradebook | `/api/staff/gradebook`, `/api/staff/gradebook/entry` |
| مخزن سؤال | P63.5 | `/api/p635/questions` |
| بانک سؤال و آزمون مستقل | P63.10/P63.12 | `/api/p635/quizzes`, `/api/p635/teacher/quizzes` |
| ساخت آزمون رسمی | create | `/api/staff/teacher-exams/p673` |
| آزمون‌ها | exams | `/api/teacher/workspace` |
| تصحیح و نمرات | grading | `/api/staff/exam-grading/p6722` |
| مرکز تکالیف | P65.2 | `/api/staff/homework/p652/save` |
| بانک تمرین | P59 | `/api/practice/p59/list`, `/api/practice/p59/create` |
| کتابخانه دیجیتال | P62 | `/api/library/p62/list`, `/api/library/p62/create` |
| برنامه آموزشی | P67.37 | `/api/staff/learning-planner`, `/api/staff/learning-planner/event` |
| پیام مستقیم | P58 | `/api/academic-messages/p58/*` |
| ارتباطات | P67.34 | `/api/staff/communications-center` |
| ملاقات اولیا | V22 | `/api/staff/guardian-meeting-invitations` |
| گزارش مستمر | continuousReport | `/api/teacher/continuous-p11` |
| تحلیل دانش‌آموز | education analytics scoped teacher view | `/api/staff/student-analysis` |
| مرکز اقدام | P67.33 | `/api/staff/action-center/*` |
| اعلان‌ها | dashboard notifications | `/api/teacher/dashboard` |
| حساب و امنیت | sessions | `/api/staff/me`, `/api/staff/sessions` |
