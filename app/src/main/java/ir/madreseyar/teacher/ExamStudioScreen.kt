package ir.madreseyar.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.random.Random

private val XBrand = Color(0xFF079C9A)
private val XBrandDark = Color(0xFF075A63)
private val XBg = Color(0xFFF3F9FB)
private val XText = Color(0xFF12313A)
private val XMuted = Color(0xFF71838A)
private val XSuccess = Color(0xFF168B5B)
private val XWarning = Color(0xFFE58A19)

private fun JSONArray.examObjects(): List<JSONObject> = buildList {
    for (i in 0 until length()) optJSONObject(i)?.let(::add)
}

private data class ExamQuestionDraft(
    val id: String = "q_android_${UUID.randomUUID().toString().replace("-", "").take(14)}",
    val type: String,
    val text: String,
    val score: Double,
    val chapterId: String,
    val choices: List<String> = emptyList(),
    val answerIndex: Int? = null,
    val answerText: String = "",
    val boolAnswer: Boolean = true,
    val sourceQuestionId: String = "",
    val sourceChapterId: String = "",
    val explanation: String = "",
    val visualType: String = "",
    val imageDataUrl: String = "",
)

private fun questionTypeLabel(type: String) = when (type) {
    "mcq" -> "چهارگزینه‌ای"
    "descriptive" -> "تشریحی"
    "true_false" -> "صحیح / غلط"
    "fill_blank" -> "جای خالی"
    "short_answer" -> "پاسخ کوتاه"
    else -> "سؤال"
}

private fun bankTypeLabel(type: String) = when (type) {
    "mcq" -> "چهارگزینه‌ای"
    "descriptive" -> "تشریحی"
    "truefalse" -> "صحیح / غلط"
    "fillblank" -> "جای خالی"
    "short" -> "پاسخ کوتاه"
    "image" -> "تصویری"
    else -> type
}

private fun bankQuestionAllowed(question: JSONObject, examCategory: String, academicPeriod: String): Boolean {
    val scope = question.optString("assessmentScope", "shared").ifBlank { "shared" }
    return if (examCategory == "final") {
        scope == "shared" || (scope == "final" && question.optString("academicPeriod") == academicPeriod)
    } else scope == "shared" || scope == "ongoing"
}

private fun bankQuestionToDraft(question: JSONObject): ExamQuestionDraft? {
    val sourceType = question.optString("type")
    if (sourceType == "image" && !question.optString("imageDataUrl").startsWith("data:image/")) return null
    val mappedType = when (sourceType) {
        "mcq" -> "mcq"
        "descriptive" -> "descriptive"
        "truefalse" -> "true_false"
        "fillblank" -> "fill_blank"
        "short", "image" -> "short_answer"
        else -> return null
    }
    val choices = question.optJSONArray("choices")?.let { arr -> List(arr.length()) { arr.optString(it) } }.orEmpty()
    val ref = question.optString("referenceAnswer")
    val boolAnswer = when (ref.trim()) {
        "صحیح", "درست", "بله" -> true
        "غلط", "نادرست", "خیر" -> false
        else -> true
    }
    val answerIndex = if (mappedType == "mcq" && question.has("answer")) question.optInt("answer", 0) else null
    return ExamQuestionDraft(
        id = "q_android_${UUID.randomUUID().toString().replace("-", "").take(14)}",
        type = mappedType,
        text = question.optString("questionText", question.optString("text")).trim(),
        score = question.optDouble("score", 1.0).takeIf { it > 0 } ?: 1.0,
        chapterId = question.optString("chapterId"),
        choices = choices,
        answerIndex = answerIndex,
        answerText = ref,
        boolAnswer = boolAnswer,
        sourceQuestionId = question.optString("id"),
        sourceChapterId = question.optString("chapterId"),
        explanation = question.optString("explanation"),
        visualType = if (sourceType == "image") "image" else "",
        imageDataUrl = question.optString("imageDataUrl"),
    )
}

private fun formatLocalDateTime(date: Date): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }.format(date)

private fun defaultSchedulePair(durationMinutes: Int): Pair<String, String> {
    val start = Calendar.getInstance().apply { add(Calendar.MINUTE, 15); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val end = (start.clone() as Calendar).apply { add(Calendar.MINUTE, durationMinutes.coerceAtLeast(1) + 30) }
    return formatLocalDateTime(start.time) to formatLocalDateTime(end.time)
}

private fun localInputToIso(value: String): String? = runCatching {
    val parser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }
    val date = parser.parse(value.trim()) ?: return@runCatching null
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)
}.getOrNull()

private fun dateToIsoUtc(date: Date): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)

private fun immediateSchedule(durationMinutes: Int): Pair<String, String> {
    val now = System.currentTimeMillis()
    return dateToIsoUtc(Date(now - 60_000L)) to dateToIsoUtc(Date(now + (durationMinutes.coerceAtLeast(1) + 45L) * 60_000L))
}

private fun shuffleMcqChoices(question: ExamQuestionDraft, random: Random): ExamQuestionDraft {
    if (question.type != "mcq" || question.choices.size != 4 || question.answerIndex !in 0..3) return question
    val answer = question.answerIndex ?: return question
    val rows = question.choices.mapIndexed { i, text -> i to text }.shuffled(random)
    val newAnswer = rows.indexOfFirst { it.first == answer }
    return question.copy(choices = rows.map { it.second }, answerIndex = newAnswer)
}

@Composable
fun ExamStudioScreen(workspace: JSONObject?) {
    val scope = rememberCoroutineScope()
    val assignments = workspace?.optJSONArray("assignments")?.examObjects().orEmpty()
    var library by remember { mutableStateOf<JSONObject?>(null) }
    var loadError by remember { mutableStateOf("") }
    var assignmentIndex by remember { mutableIntStateOf(0) }
    var bookIndex by remember { mutableIntStateOf(0) }
    var examCategory by remember { mutableStateOf("ongoing") }
    var academicPeriod by remember { mutableStateOf("semester1") }
    var title by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("20") }
    var showInstantResult by remember { mutableStateOf(true) }
    var questions by remember { mutableStateOf<List<ExamQuestionDraft>>(emptyList()) }
    var editorOpen by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var policyLoading by remember { mutableStateOf(false) }
    var continuousMax by remember { mutableStateOf<Double?>(null) }
    var finalMax by remember { mutableStateOf<Double?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var success by remember { mutableStateOf("") }
    var bankPickerOpen by remember { mutableStateOf(false) }
    var audiencePickerOpen by remember { mutableStateOf(false) }
    var previewOpen by remember { mutableStateOf(false) }
    var customAudience by remember { mutableStateOf(false) }
    var selectedStudentIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var shuffleQuestions by remember { mutableStateOf(true) }
    var shuffleChoices by remember { mutableStateOf(false) }
    var scheduleEnabled by remember { mutableStateOf(false) }
    val initialSchedule = remember { defaultSchedulePair(20) }
    var startInput by remember { mutableStateOf(initialSchedule.first) }
    var endInput by remember { mutableStateOf(initialSchedule.second) }
    var pendingExamId by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try { library = ApiClient.get("/api/staff/teacher-approved-questions/p67") }
        catch (e: Throwable) { loadError = e.message ?: "دریافت کتاب‌ها و فصل‌ها ناموفق بود." }
    }

    val assignment = assignments.getOrNull(assignmentIndex)
    val subject = assignment?.optString("subject").orEmpty()
    val books = library?.optJSONArray("books")?.examObjects().orEmpty().filter {
        it.optString("subject") == subject || it.optString("id") == assignment?.optString("bookId")
    }
    val book = books.getOrNull(bookIndex)
    val chapters = book?.optJSONArray("chapters")?.examObjects().orEmpty()
    val totalScore = questions.sumOf { it.score }
    val classStudents = workspace?.optJSONArray("students")?.examObjects().orEmpty().filter {
        it.optString("classId") == assignment?.optString("classId")
    }
    val bankQuestions = library?.optJSONArray("questions")?.examObjects().orEmpty().filter { q ->
        q.optString("bookId") == book?.optString("id") && bankQuestionAllowed(q, examCategory, academicPeriod)
    }

    LaunchedEffect(examCategory, academicPeriod, assignmentIndex, subject) {
        if (examCategory != "final" || assignment == null || subject.isBlank()) {
            continuousMax = null; finalMax = null; return@LaunchedEffect
        }
        policyLoading = true
        try {
            val c = URLEncoder.encode(assignment.optString("classId"), "UTF-8")
            val s = URLEncoder.encode(subject, "UTF-8")
            val gradebook = ApiClient.get("/api/staff/gradebook?classId=$c&subject=$s&academicPeriod=$academicPeriod")
            val continuous = gradebook.optJSONObject("config")?.optDouble("outputMaxScore", Double.NaN)
            if (continuous != null && continuous.isFinite()) {
                continuousMax = continuous
                finalMax = kotlin.math.round((20.0 - continuous) * 100.0) / 100.0
            } else {
                continuousMax = null; finalMax = null
            }
        } catch (e: Throwable) {
            continuousMax = null; finalMax = null
            error = "بارم آزمون پایانی از سرور خوانده نشد: ${e.message ?: "خطا"}"
        } finally { policyLoading = false }
    }

    fun validateBeforeSubmit(forPublish: Boolean): String? {
        if (assignment == null) return "کلاس و درس انتخاب نشده است."
        if (book == null) return "کتاب مرتبط با این درس پیدا نشد."
        if (chapters.isEmpty()) return "برای این کتاب فصلی ثبت نشده است."
        if (title.trim().length < 3) return "عنوان آزمون را کامل وارد کنید."
        val mins = duration.toIntOrNull() ?: return "مدت آزمون معتبر نیست."
        if (mins !in 1..300) return "مدت آزمون باید بین ۱ تا ۳۰۰ دقیقه باشد."
        if (questions.isEmpty()) return "حداقل یک سؤال به آزمون اضافه کنید."
        if (totalScore <= 0) return "جمع بارم سؤال‌ها باید بیشتر از صفر باشد."
        if (customAudience && selectedStudentIds.isEmpty()) return "حداقل یک دانش‌آموز را انتخاب کنید یا حالت «همه کلاس» را فعال کنید."
        if (examCategory == "final") {
            val max = finalMax ?: return "بارم پایانی هنوز از سرور دریافت نشده است."
            if (kotlin.math.abs(totalScore - max) > 0.001) return "جمع بارم سؤال‌ها باید دقیقاً $max باشد؛ اکنون $totalScore است."
        }
        if (forPublish && scheduleEnabled) {
            localInputToIso(startInput) ?: return "زمان شروع معتبر نیست. نمونه: 2026-11-02 08:00"
            localInputToIso(endInput) ?: return "زمان پایان معتبر نیست. نمونه: 2026-11-02 09:00"
            // Parsing is repeated below by the server; here we only reject an obviously reversed local range.
            val localParser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }
            val localStart = runCatching { localParser.parse(startInput.trim())?.time }.getOrNull()
            val localEnd = runCatching { localParser.parse(endInput.trim())?.time }.getOrNull()
            if (localStart == null || localEnd == null || localEnd <= localStart) return "زمان پایان باید بعد از زمان شروع باشد."
            if (localEnd - localStart < mins * 60_000L) return "بازه آزمون باید حداقل به اندازه مدت آزمون باشد."
        }
        return null
    }

    fun preparedQuestionsFor(examId: String): List<ExamQuestionDraft> {
        if (!shuffleChoices) return questions
        val random = Random(examId.hashCode())
        return questions.map { shuffleMcqChoices(it, random) }
    }

    fun questionsJson(rows: List<ExamQuestionDraft>): JSONArray {
        val out = JSONArray()
        rows.forEach { q ->
            val row = JSONObject()
                .put("id", q.id)
                .put("type", q.type)
                .put("text", q.text.trim())
                .put("score", q.score)
                .put("chapterId", q.chapterId)
            if (q.sourceQuestionId.isNotBlank()) row.put("sourceQuestionId", q.sourceQuestionId)
            if (q.sourceChapterId.isNotBlank()) row.put("sourceChapterId", q.sourceChapterId)
            if (q.explanation.isNotBlank()) row.put("explanation", q.explanation)
            if (q.visualType.isNotBlank()) row.put("visualType", q.visualType)
            if (q.imageDataUrl.isNotBlank()) row.put("imageDataUrl", q.imageDataUrl)
            when (q.type) {
                "mcq" -> row.put("choices", JSONArray(q.choices)).put("answer", q.answerIndex ?: 0)
                "true_false" -> row.put("answer", q.boolAnswer)
                "fill_blank" -> row.put("acceptedAnswers", JSONArray().put(q.answerText.trim())).put("answer", q.answerText.trim())
                "short_answer", "descriptive" -> row.put("referenceAnswer", q.answerText.trim())
            }
            out.put(row)
        }
        return out
    }

    suspend fun submit(status: String) {
        error = ""; success = ""
        val publishing = status == "active"
        validateBeforeSubmit(publishing)?.let { error = it; return }
        val a = assignment ?: return
        val b = book ?: return
        val mins = duration.toInt()
        val examId = pendingExamId.ifBlank {
            "ex_android_${UUID.randomUUID().toString().replace("-", "").take(18)}".also { pendingExamId = it }
        }
        val preparedQuestions = preparedQuestionsFor(examId)
        val qJson = questionsJson(preparedQuestions)
        val selectedChapterIds = preparedQuestions.map { it.chapterId }.filter { it.isNotBlank() }.distinct()
        val schedule = if (publishing) {
            if (scheduleEnabled) (localInputToIso(startInput)!! to localInputToIso(endInput)!!)
            else immediateSchedule(mins)
        } else {
            if (scheduleEnabled) ((localInputToIso(startInput) ?: "") to (localInputToIso(endInput) ?: "")) else ("" to "")
        }
        val audience = if (customAudience) selectedStudentIds.toList() else emptyList()
        val exam = JSONObject()
            .put("id", examId)
            .put("title", title.trim())
            .put("classId", a.optString("classId"))
            .put("bookId", b.optString("id"))
            .put("subject", subject)
            .put("duration", mins)
            .put("examCategory", examCategory)
            .put("academicPeriod", academicPeriod)
            .put("selectedChapterIds", JSONArray(selectedChapterIds))
            .put("targetScore", totalScore)
            .put("showInstantResult", showInstantResult && examCategory == "ongoing")
            .put("shuffleQuestions", shuffleQuestions)
            .put("shuffleOptionsOnce", shuffleChoices)
            .put("studentIds", JSONArray(audience))
            .put("startAt", schedule.first)
            .put("endAt", schedule.second)
            .put("status", "draft")
            .put("questions", qJson)
        busy = true
        try {
            var draftConfirmed = false
            try {
                ApiClient.post("/api/staff/teacher-exams/p673", JSONObject().put("exam", exam))
                draftConfirmed = true
            } catch (createError: ApiException) {
                // If publication succeeded but its response was lost, P67.6 can verify the same publish request idempotently.
                if (!(publishing && createError.status == 409)) throw createError
            }
            if (publishing) {
                val fields = JSONObject()
                    .put("title", title.trim())
                    .put("classId", a.optString("classId"))
                    .put("bookId", b.optString("id"))
                    .put("examCategory", examCategory)
                    .put("academicPeriod", academicPeriod)
                    .put("duration", mins)
                    .put("selectedChapterIds", JSONArray(selectedChapterIds))
                    .put("questions", qJson)
                    .put("studentIds", JSONArray(audience))
                    .put("showInstantResult", showInstantResult && examCategory == "ongoing")
                    .put("startAt", schedule.first)
                    .put("endAt", schedule.second)
                ApiClient.post("/api/staff/teacher-exams/p676", JSONObject().put("action", "publish").put("examId", examId).put("fields", fields))
                success = if (scheduleEnabled) "آزمون با زمان‌بندی تعیین‌شده منتشر شد ✓" else "آزمون منتشر شد و برای کلاس فعال است ✓"
            } else {
                if (!draftConfirmed) throw IllegalStateException("تأیید ذخیره پیش‌نویس دریافت نشد.")
                success = "پیش‌نویس آزمون روی سرور ذخیره شد ✓"
            }
            pendingExamId = ""
        } catch (e: Throwable) { error = e.message ?: "ثبت آزمون ناموفق بود." }
        finally { busy = false }
    }

    Column(Modifier.fillMaxSize().background(XBg)) {
        Surface(color = XBrandDark) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(46.dp), RoundedCornerShape(15.dp), Color.White.copy(.12f)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.EditNote, null, tint = Color.White) }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("استودیو طراحی آزمون", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text("آزمون مستمر و پایانی با ثبت مستقیم روی سرور", color = Color.White.copy(.78f), fontSize = 12.sp)
                    }
                }
            }
        }

        if (loadError.isNotBlank()) Text(loadError, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
        if (busy || policyLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = XBrand)

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                ExamStudioCard {
                    Text("نوع آزمون", fontWeight = FontWeight.Black, color = XText)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = examCategory == "ongoing",
                            onClick = { examCategory = "ongoing"; showInstantResult = true; error = "" },
                            label = { Text("آزمون مستمر") },
                            leadingIcon = { Icon(Icons.Default.AutoGraph, null, Modifier.size(18.dp)) },
                        )
                        FilterChip(
                            selected = examCategory == "final",
                            onClick = { examCategory = "final"; showInstantResult = false; error = "" },
                            label = { Text("آزمون پایانی") },
                            leadingIcon = { Icon(Icons.Default.WorkspacePremium, null, Modifier.size(18.dp)) },
                        )
                    }
                    Text(
                        if (examCategory == "ongoing") "برای ارزشیابی‌های کلاسی، فصل، میان‌ترم و آزمون‌های مستمر."
                        else "برای امتحان رسمی پایان نیم‌سال؛ بارم از سیاست رسمی مدرسه خوانده می‌شود.",
                        color = XMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }

            item {
                ExamStudioCard {
                    Text("نیم‌سال", fontWeight = FontWeight.Black, color = XText)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = academicPeriod == "semester1", onClick = { academicPeriod = "semester1" }, label = { Text("نیم‌سال اول") })
                        FilterChip(selected = academicPeriod == "semester2", onClick = { academicPeriod = "semester2" }, label = { Text("نیم‌سال دوم") })
                    }
                    if (examCategory == "final") {
                        Spacer(Modifier.height(8.dp))
                        Surface(color = Color(0xFFE7F8F2), shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text("بارم رسمی پایان‌ترم", fontWeight = FontWeight.Bold, color = XBrandDark)
                                if (policyLoading) Text("در حال دریافت بارم مصوب…", color = XMuted)
                                else if (finalMax != null) Text("مستمر: ${continuousMax ?: 0.0}  +  پایانی: ${finalMax}  =  ۲۰", color = XText)
                                else Text("برای نمایش بارم، کلاس و درس معتبر را انتخاب کنید.", color = XMuted)
                            }
                        }
                    }
                }
            }

            item {
                ExamStudioCard {
                    Text("کلاس و درس", fontWeight = FontWeight.Black, color = XText)
                    if (assignments.isEmpty()) Text("تخصیص تدریس فعالی برای شما ثبت نشده است.", color = MaterialTheme.colorScheme.error)
                    else assignments.forEachIndexed { i, x ->
                        FilterChip(
                            selected = i == assignmentIndex,
                            onClick = { assignmentIndex = i; bookIndex = 0; questions = emptyList(); customAudience = false; selectedStudentIds = emptySet(); pendingExamId = ""; error = "" },
                            label = { Text("${x.optString("subject")} · ${x.optString("className", x.optString("classId"))}") }
                        )
                    }
                    if (books.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp)); Text("کتاب", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        books.forEachIndexed { i, x -> FilterChip(selected = i == bookIndex, onClick = { bookIndex = i; questions = emptyList(); pendingExamId = "" }, label = { Text(x.optString("title", x.optString("subject"))) }) }
                    }
                    if (chapters.isNotEmpty()) Text("${chapters.size} فصل برای افزودن سؤال در دسترس است.", color = XMuted, fontSize = 11.sp)
                }
            }

            item {
                ExamStudioCard {
                    Text("مشخصات آزمون", fontWeight = FontWeight.Black, color = XText)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(title, { title = it.take(160) }, Modifier.fillMaxWidth(), label = { Text("عنوان آزمون") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(duration, { duration = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text("مدت آزمون (دقیقه)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    if (examCategory == "ongoing") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            Switch(showInstantResult, { showInstantResult = it })
                            Spacer(Modifier.width(7.dp)); Text("نمایش نتیجه فوری به دانش‌آموز", color = XText, fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                ExamStudioCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = XBrand)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("زمان‌بندی آزمون", fontWeight = FontWeight.Black, color = XText)
                            Text(if (scheduleEnabled) "شروع و پایان مشخص؛ دانش‌آموز فقط در بازه تعیین‌شده وارد می‌شود." else "انتشار فوری؛ سرور مهلت امن متناسب با مدت آزمون ایجاد می‌کند.", color = XMuted, fontSize = 11.sp)
                        }
                        Switch(scheduleEnabled, { enabled ->
                            scheduleEnabled = enabled
                            if (enabled) {
                                val pair = defaultSchedulePair(duration.toIntOrNull() ?: 20)
                                startInput = pair.first; endInput = pair.second
                            }
                        })
                    }
                    if (scheduleEnabled) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(startInput, { startInput = it.take(16) }, Modifier.fillMaxWidth(), label = { Text("شروع · YYYY-MM-DD HH:MM") }, singleLine = true)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(endInput, { endInput = it.take(16) }, Modifier.fillMaxWidth(), label = { Text("پایان · YYYY-MM-DD HH:MM") }, singleLine = true)
                    }
                }
            }

            item {
                ExamStudioCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Groups, null, tint = XBrand)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("مخاطبان آزمون", fontWeight = FontWeight.Black, color = XText)
                            Text(
                                if (!customAudience) "همه ${classStudents.size} دانش‌آموز این کلاس" else "${selectedStudentIds.size} دانش‌آموز انتخاب شده",
                                color = XMuted, fontSize = 11.sp
                            )
                        }
                        TextButton(onClick = { audiencePickerOpen = true }, enabled = classStudents.isNotEmpty()) { Text("انتخاب") }
                    }
                }
            }

            item {
                ExamStudioCard {
                    Text("تنظیمات تصادفی‌سازی", fontWeight = FontWeight.Black, color = XText)
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(shuffleQuestions, { shuffleQuestions = it })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("جابه‌جایی ترتیب سؤال‌ها", fontWeight = FontWeight.Bold, color = XText, fontSize = 13.sp)
                            Text("ترتیب سؤال‌ها برای اجرای آزمون قابل جابه‌جایی است.", color = XMuted, fontSize = 10.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(shuffleChoices, { shuffleChoices = it })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("جابه‌جایی گزینه‌های تستی", fontWeight = FontWeight.Bold, color = XText, fontSize = 13.sp)
                            Text("گزینه‌های چهارگزینه‌ای هنگام ثبت نهایی با حفظ کلید صحیح بازچینی می‌شوند.", color = XMuted, fontSize = 10.sp)
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("سؤال‌ها", fontWeight = FontWeight.Black, fontSize = 18.sp, color = XBrandDark)
                        Text("${questions.size} سؤال · جمع بارم ${formatScore(totalScore)}", color = XMuted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(7.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { bankPickerOpen = true }, enabled = book != null && bankQuestions.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Inventory2, null); Spacer(Modifier.width(4.dp)); Text("از بانک سؤال")
                    }
                    Button(onClick = { editingIndex = null; editorOpen = true }, colors = ButtonDefaults.buttonColors(containerColor = XBrand), enabled = chapters.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("سؤال جدید")
                    }
                }
                if (book != null) Text("${bankQuestions.size} سؤال مجاز از بانک برای این آزمون در دسترس است.", color = XMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }

            itemsIndexed(questions) { index, q ->
                ExamStudioCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(36.dp), RoundedCornerShape(12.dp), XBrand.copy(.1f)) { Box(contentAlignment = Alignment.Center) { Text("${index + 1}", fontWeight = FontWeight.Black, color = XBrandDark) } }
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(questionTypeLabel(q.type), color = XBrand, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(q.text, fontWeight = FontWeight.Bold, color = XText)
                            Text("بارم ${formatScore(q.score)}", color = XMuted, fontSize = 11.sp)
                        }
                        IconButton(onClick = { editingIndex = index; editorOpen = true }) { Icon(Icons.Default.Edit, "ویرایش", tint = XBrandDark) }
                        IconButton(onClick = { questions = questions.toMutableList().also { it.removeAt(index) } }) { Icon(Icons.Default.DeleteOutline, "حذف", tint = Color(0xFFC64D4D)) }
                    }
                }
            }

            if (questions.isEmpty()) item {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White) {
                    Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Quiz, null, tint = XBrand, modifier = Modifier.size(42.dp))
                        Text("هنوز سؤالی اضافه نشده", fontWeight = FontWeight.Black, color = XText, modifier = Modifier.padding(top = 8.dp))
                        Text("چهارگزینه‌ای، تشریحی، صحیح/غلط، جای‌خالی و پاسخ کوتاه پشتیبانی می‌شود.", color = XMuted, fontSize = 11.sp)
                    }
                }
            }

            item {
                ExamStudioCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("جمع بارم سؤال‌ها", fontWeight = FontWeight.Bold, color = XText)
                        Text(formatScore(totalScore), fontWeight = FontWeight.Black, fontSize = 18.sp, color = if (examCategory == "final" && finalMax != null && kotlin.math.abs(totalScore - finalMax!!) > .001) XWarning else XBrand)
                    }
                    if (examCategory == "final" && finalMax != null) {
                        val remaining = kotlin.math.round((finalMax!! - totalScore) * 100) / 100.0
                        Text(
                            when {
                                kotlin.math.abs(remaining) < .001 -> "بارم آزمون پایانی کامل است ✓"
                                remaining > 0 -> "${formatScore(remaining)} نمره دیگر باید بین سؤال‌ها توزیع شود."
                                else -> "جمع بارم ${formatScore(-remaining)} نمره بیشتر از سقف رسمی است."
                            },
                            color = if (kotlin.math.abs(remaining) < .001) XSuccess else XWarning,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                }
            }

            if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(4.dp)) }
            if (success.isNotBlank()) item { Text(success, color = XSuccess, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp)) }

            item {
                OutlinedButton(enabled = !busy && questions.isNotEmpty(), onClick = { previewOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Preview, null); Spacer(Modifier.width(5.dp)); Text("پیش‌نمایش برگه و تنظیمات")
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = { scope.launch { submit("draft") } }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Save, null); Spacer(Modifier.width(4.dp)); Text("ذخیره پیش‌نویس")
                    }
                    Button(enabled = !busy, onClick = { scope.launch { submit("active") } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = XBrand)) {
                        Icon(Icons.Default.Publish, null); Spacer(Modifier.width(4.dp)); Text("انتشار آزمون")
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }

    if (editorOpen) {
        QuestionEditorDialog(
            chapters = chapters,
            initial = editingIndex?.let { questions.getOrNull(it) },
            onDismiss = { editorOpen = false; editingIndex = null },
            onSave = { q ->
                val idx = editingIndex
                questions = if (idx == null) questions + q else questions.toMutableList().also { it[idx] = q }
                editorOpen = false; editingIndex = null
            }
        )
    }

    if (bankPickerOpen) {
        BankQuestionPickerDialog(
            questions = bankQuestions,
            existingSourceIds = questions.map { it.sourceQuestionId }.filter { it.isNotBlank() }.toSet(),
            onDismiss = { bankPickerOpen = false },
            onAdd = { selected ->
                val converted = selected.mapNotNull(::bankQuestionToDraft)
                val existing = questions.map { it.sourceQuestionId }.filter { it.isNotBlank() }.toSet()
                questions = questions + converted.filter { it.sourceQuestionId !in existing }
                bankPickerOpen = false
            }
        )
    }

    if (audiencePickerOpen) {
        AudiencePickerDialog(
            students = classStudents,
            customAudience = customAudience,
            selectedIds = selectedStudentIds,
            onDismiss = { audiencePickerOpen = false },
            onApply = { custom, ids ->
                customAudience = custom
                selectedStudentIds = ids
                audiencePickerOpen = false
            }
        )
    }

    if (previewOpen) {
        ExamPreviewDialog(
            title = title.ifBlank { "بدون عنوان" },
            examCategory = examCategory,
            academicPeriod = academicPeriod,
            duration = duration.toIntOrNull() ?: 0,
            questions = questions,
            totalScore = totalScore,
            audienceText = if (!customAudience) "همه ${classStudents.size} دانش‌آموز کلاس" else "${selectedStudentIds.size} دانش‌آموز منتخب",
            scheduleText = if (scheduleEnabled) "$startInput  ←  $endInput" else "انتشار فوری",
            shuffleQuestions = shuffleQuestions,
            shuffleChoices = shuffleChoices,
            onDismiss = { previewOpen = false }
        )
    }
}

@Composable
private fun ExamStudioCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(15.dp), content = content)
    }
}

@Composable
private fun BankQuestionPickerDialog(
    questions: List<JSONObject>,
    existingSourceIds: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (List<JSONObject>) -> Unit,
) {
    var selected by remember(questions) { mutableStateOf<Set<String>>(emptySet()) }
    var filter by remember { mutableStateOf("") }
    val visible = questions.filter {
        filter.isBlank() || it.optString("text").contains(filter, true) || it.optString("questionText").contains(filter, true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب از بانک سؤال", fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(filter, { filter = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("جست‌وجوی سؤال") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text("${visible.size} سؤال مجاز · ${selected.size} انتخاب شده", color = XMuted, fontSize = 11.sp)
                Spacer(Modifier.height(5.dp))
                LazyColumn(Modifier.heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(visible) { _, q ->
                        val id = q.optString("id")
                        val already = id in existingSourceIds
                        Card(colors = CardDefaults.cardColors(if (already) Color(0xFFF1F4F5) else Color.White)) {
                            Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.Top) {
                                Checkbox(
                                    checked = already || id in selected,
                                    enabled = !already,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + id else selected - id
                                    }
                                )
                                Spacer(Modifier.width(5.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(q.optString("questionText", q.optString("text")), fontWeight = FontWeight.Bold, color = XText, fontSize = 12.sp)
                                    Text("${bankTypeLabel(q.optString("type"))} · ${q.optString("difficulty", "—")} · بارم ${formatScore(q.optDouble("score", 1.0))}", color = XMuted, fontSize = 10.sp)
                                    if (already) Text("قبلاً به آزمون اضافه شده", color = XSuccess, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(questions.filter { it.optString("id") in selected }) },
                enabled = selected.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = XBrand)
            ) { Text("افزودن ${selected.size} سؤال") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun AudiencePickerDialog(
    students: List<JSONObject>,
    customAudience: Boolean,
    selectedIds: Set<String>,
    onDismiss: () -> Unit,
    onApply: (Boolean, Set<String>) -> Unit,
) {
    var custom by remember { mutableStateOf(customAudience) }
    var selected by remember(students) { mutableStateOf(if (selectedIds.isEmpty()) students.map { it.optString("id") }.toSet() else selectedIds) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("دانش‌آموزان دریافت‌کننده آزمون", fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !custom, onClick = { custom = false })
                    Text("همه دانش‌آموزان کلاس (${students.size})")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = custom, onClick = { custom = true })
                    Text("انتخاب دانش‌آموزان خاص")
                }
                if (custom) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { selected = students.map { it.optString("id") }.toSet() }) { Text("انتخاب همه") }
                        TextButton(onClick = { selected = emptySet() }) { Text("پاک کردن") }
                    }
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        itemsIndexed(students) { _, st ->
                            val id = st.optString("id")
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = id in selected, onCheckedChange = { checked -> selected = if (checked) selected + id else selected - id })
                                Column {
                                    Text("${st.optString("firstName")} ${st.optString("lastName")}".trim().ifBlank { "دانش‌آموز" }, fontWeight = FontWeight.Bold, color = XText)
                                    Text(st.optString("studentCode"), color = XMuted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(custom, if (custom) selected else emptySet()) }, enabled = !custom || selected.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = XBrand)) {
                Text("تأیید مخاطبان")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun ExamPreviewDialog(
    title: String,
    examCategory: String,
    academicPeriod: String,
    duration: Int,
    questions: List<ExamQuestionDraft>,
    totalScore: Double,
    audienceText: String,
    scheduleText: String,
    shuffleQuestions: Boolean,
    shuffleChoices: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("پیش‌نمایش آزمون", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                item {
                    Surface(color = Color(0xFFE7F8F7), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(title, fontWeight = FontWeight.Black, color = XBrandDark, fontSize = 17.sp)
                            Text("${if (examCategory == "final") "پایانی" else "مستمر"} · ${if (academicPeriod == "semester2") "نیم‌سال دوم" else "نیم‌سال اول"} · $duration دقیقه", color = XText, fontSize = 11.sp)
                            Text("$audienceText · $scheduleText", color = XMuted, fontSize = 10.sp)
                            Text("${questions.size} سؤال · جمع بارم ${formatScore(totalScore)}", color = XText, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            if (shuffleQuestions || shuffleChoices) Text("تصادفی‌سازی: ${listOfNotNull(if (shuffleQuestions) "سؤال‌ها" else null, if (shuffleChoices) "گزینه‌ها" else null).joinToString(" و ")}", color = XBrand, fontSize = 10.sp)
                        }
                    }
                }
                itemsIndexed(questions) { index, q ->
                    Card(colors = CardDefaults.cardColors(Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("سؤال ${index + 1} · ${questionTypeLabel(q.type)}", color = XBrand, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("${formatScore(q.score)} نمره", color = XMuted, fontSize = 10.sp)
                            }
                            Text(q.text, color = XText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            if (q.type == "mcq") q.choices.forEachIndexed { i, choice ->
                                Text("${listOf("الف", "ب", "ج", "د").getOrElse(i) { "•" }}. $choice", color = if (q.answerIndex == i) XSuccess else XText, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = XBrand)) { Text("تأیید پیش‌نمایش") } }
    )
}

@Composable
private fun QuestionEditorDialog(
    chapters: List<JSONObject>,
    initial: ExamQuestionDraft?,
    onDismiss: () -> Unit,
    onSave: (ExamQuestionDraft) -> Unit,
) {
    var type by remember(initial) { mutableStateOf(initial?.type ?: "mcq") }
    var text by remember(initial) { mutableStateOf(initial?.text ?: "") }
    var score by remember(initial) { mutableStateOf(initial?.score?.let(::formatScore) ?: "1") }
    var chapterIndex by remember(initial, chapters) {
        mutableIntStateOf(chapters.indexOfFirst { it.optString("id") == initial?.chapterId }.coerceAtLeast(0))
    }
    val choices = remember(initial) { mutableStateListOf<String>().apply { addAll(initial?.choices?.takeIf { it.size == 4 } ?: listOf("", "", "", "")) } }
    var answerIndex by remember(initial) { mutableIntStateOf(initial?.answerIndex ?: 0) }
    var answerText by remember(initial) { mutableStateOf(initial?.answerText ?: "") }
    var boolAnswer by remember(initial) { mutableStateOf(initial?.boolAnswer ?: true) }
    var localError by remember { mutableStateOf("") }
    val types = listOf("mcq", "descriptive", "true_false", "fill_blank", "short_answer")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "افزودن سؤال" else "ویرایش سؤال", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                item {
                    Text("نوع سؤال", fontWeight = FontWeight.Bold)
                    types.forEach { t -> FilterChip(selected = type == t, onClick = { type = t; localError = "" }, label = { Text(questionTypeLabel(t)) }) }
                    Spacer(Modifier.height(5.dp))
                    Text("فصل", fontWeight = FontWeight.Bold)
                    chapters.forEachIndexed { i, c ->
                        FilterChip(selected = i == chapterIndex, onClick = { chapterIndex = i }, label = { Text(c.optString("title", c.optString("name", "فصل ${i + 1}"))) })
                    }
                    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("متن سؤال") }, minLines = 2)
                    OutlinedTextField(score, { score = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6) }, Modifier.fillMaxWidth(), label = { Text("بارم سؤال") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    when (type) {
                        "mcq" -> choices.indices.forEach { i ->
                            OutlinedTextField(
                                choices[i], { choices[i] = it }, Modifier.fillMaxWidth(), label = { Text("گزینه ${i + 1}") }, singleLine = true,
                                trailingIcon = { RadioButton(selected = answerIndex == i, onClick = { answerIndex = i }) }
                            )
                        }
                        "true_false" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = boolAnswer, onClick = { boolAnswer = true }, label = { Text("صحیح") })
                            FilterChip(selected = !boolAnswer, onClick = { boolAnswer = false }, label = { Text("غلط") })
                        }
                        "fill_blank" -> OutlinedTextField(answerText, { answerText = it }, Modifier.fillMaxWidth(), label = { Text("پاسخ صحیح جای خالی") }, singleLine = true)
                        "short_answer" -> OutlinedTextField(answerText, { answerText = it }, Modifier.fillMaxWidth(), label = { Text("پاسخ مرجع کوتاه") }, minLines = 2)
                        "descriptive" -> OutlinedTextField(answerText, { answerText = it }, Modifier.fillMaxWidth(), label = { Text("پاسخ مرجع / راهنمای تصحیح (اختیاری)") }, minLines = 3)
                    }
                    if (localError.isNotBlank()) Text(localError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(colors = ButtonDefaults.buttonColors(containerColor = XBrand), onClick = {
                val parsed = score.toDoubleOrNull()
                localError = when {
                    chapters.isEmpty() -> "فصل آزمون مشخص نیست."
                    text.trim().isEmpty() -> "متن سؤال را وارد کنید."
                    parsed == null || parsed <= 0.0 || parsed > 20.0 -> "بارم سؤال باید بیشتر از صفر و حداکثر ۲۰ باشد."
                    type == "mcq" && choices.any { it.trim().isEmpty() } -> "هر چهار گزینه را کامل کنید."
                    type == "fill_blank" && answerText.trim().isEmpty() -> "پاسخ صحیح جای خالی را وارد کنید."
                    else -> ""
                }
                if (localError.isBlank()) {
                    onSave(
                        ExamQuestionDraft(
                            id = initial?.id ?: "q_android_${UUID.randomUUID().toString().replace("-", "").take(14)}",
                            type = type,
                            text = text.trim(),
                            score = parsed!!,
                            chapterId = chapters.getOrNull(chapterIndex)?.optString("id").orEmpty(),
                            choices = if (type == "mcq") choices.toList() else emptyList(),
                            answerIndex = if (type == "mcq") answerIndex else null,
                            answerText = answerText.trim(),
                            boolAnswer = boolAnswer,
                            sourceQuestionId = initial?.sourceQuestionId.orEmpty(),
                            sourceChapterId = initial?.sourceChapterId.orEmpty(),
                            explanation = initial?.explanation.orEmpty(),
                            visualType = initial?.visualType.orEmpty(),
                            imageDataUrl = initial?.imageDataUrl.orEmpty(),
                        )
                    )
                }
            }) { Text("ثبت سؤال") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

private fun formatScore(v: Double): String = if (kotlin.math.abs(v - v.toInt()) < .0001) v.toInt().toString() else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
