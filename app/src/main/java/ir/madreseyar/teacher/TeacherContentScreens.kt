package ir.madreseyar.teacher

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val CBrand = Color(0xFF0CA6A4)
private val CBrandDark = Color(0xFF075A63)
private val CBg = Color(0xFFF3F9FB)
private val CText = Color(0xFF12313A)
private val CMuted = Color(0xFF71838A)
private val CSuccess = Color(0xFF16A36A)

private fun JSONArray.contentObjects(): List<JSONObject> = buildList {
    for (i in 0 until length()) optJSONObject(i)?.let(::add)
}

@Composable
private fun ContentHeader(title: String, subtitle: String) {
    Surface(color = CBrandDark) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Color.White.copy(alpha = .78f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ContentCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) { Column(Modifier.padding(14.dp), content = content) }
}

private data class PracticeQuestion(
    val text: String = "",
    val options: List<String> = listOf("", "", "", ""),
    val answer: Int = 0,
)

@Composable
fun PracticeTeacherScreen() {
    val scope = rememberCoroutineScope()
    var quizzes by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var assignments by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var createOpen by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true
        error = ""
        try {
            quizzes = ApiClient.get("/api/practice/p59/list").optJSONArray("quizzes")?.contentObjects().orEmpty()
            assignments = ApiClient.get("/api/practice/p59/contacts").optJSONArray("assignments")?.contentObjects().orEmpty()
        } catch (e: Throwable) {
            error = e.message ?: "خطا در دریافت بانک تمرین"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(Modifier.fillMaxSize().background(CBg)) {
        ContentHeader("بانک تمرین", "ساخت و انتشار تمرین مستقل از نمره رسمی")
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { createOpen = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = CBrand)
            ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("تمرین جدید") }
            OutlinedButton(onClick = { scope.launch { reload() } }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(5.dp)); Text("تازه‌سازی")
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = CBrand)
        if (error.isNotBlank()) Text(error, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(quizzes) { q ->
                ContentCard {
                    Text(q.optString("title", "تمرین"), fontWeight = FontWeight.Black, color = CText, fontSize = 16.sp)
                    Text("${q.optString("subject")} · ${q.optInt("count")} سؤال", color = CMuted, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = CSuccess, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (q.optBoolean("published")) "منتشر شده" else "پیش‌نویس", color = CMuted, fontSize = 11.sp)
                    }
                }
            }
            if (!loading && quizzes.isEmpty() && error.isBlank()) item {
                Text("هنوز تمرینی ثبت نشده است.", Modifier.padding(24.dp), color = CMuted)
            }
        }
    }

    if (createOpen) {
        PracticeCreateDialog(
            assignments = assignments,
            onDismiss = { createOpen = false },
            onSaved = { createOpen = false; scope.launch { reload() } }
        )
    }
}

@Composable
private fun PracticeCreateDialog(assignments: List<JSONObject>, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var assignmentIndex by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    val questions = remember { mutableStateListOf(PracticeQuestion()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("ساخت تمرین جدید", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 560.dp)) {
                item {
                    if (assignments.isEmpty()) {
                        Text("هیچ درس تخصیص‌یافته‌ای پیدا نشد.", color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("کلاس و درس", fontWeight = FontWeight.Bold)
                        assignments.forEachIndexed { index, a ->
                            FilterChip(
                                selected = assignmentIndex == index,
                                onClick = { assignmentIndex = index },
                                label = { Text("${a.optString("subject")} · ${a.optString("classId")}") }
                            )
                        }
                    }
                    OutlinedTextField(
                        title, { title = it.take(120) }, Modifier.fillMaxWidth(),
                        label = { Text("عنوان تمرین") }, singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                }
                items(questions.size) { qi ->
                    val q = questions[qi]
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        RoundedCornerShape(16.dp),
                        CardDefaults.cardColors(Color(0xFFF8FCFC))
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("سؤال ${qi + 1}", fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                                if (questions.size > 1) IconButton(onClick = { questions.removeAt(qi) }) {
                                    Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            OutlinedTextField(
                                q.text,
                                { questions[qi] = q.copy(text = it.take(500)) },
                                Modifier.fillMaxWidth(),
                                label = { Text("متن سؤال") }
                            )
                            q.options.forEachIndexed { oi, value ->
                                OutlinedTextField(
                                    value,
                                    { nv ->
                                        val next = q.options.toMutableList().also { it[oi] = nv.take(200) }
                                        questions[qi] = questions[qi].copy(options = next)
                                    },
                                    Modifier.fillMaxWidth(),
                                    label = { Text("گزینه ${oi + 1}") },
                                    trailingIcon = {
                                        RadioButton(
                                            selected = questions[qi].answer == oi,
                                            onClick = { questions[qi] = questions[qi].copy(answer = oi) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                item {
                    if (questions.size < 30) TextButton(onClick = { questions.add(PracticeQuestion()) }) {
                        Icon(Icons.Default.Add, null); Text("افزودن سؤال")
                    }
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && assignments.isNotEmpty() && title.isNotBlank() && questions.all { q -> q.text.isNotBlank() && q.options.all(String::isNotBlank) },
                onClick = {
                    val a = assignments.getOrNull(assignmentIndex) ?: return@Button
                    scope.launch {
                        busy = true; error = ""
                        try {
                            val arr = JSONArray()
                            questions.forEach { q ->
                                arr.put(
                                    JSONObject()
                                        .put("text", q.text.trim())
                                        .put("options", JSONArray(q.options.map { it.trim() }))
                                        .put("answer", q.answer)
                                )
                            }
                            ApiClient.post(
                                "/api/practice/p59/create",
                                JSONObject()
                                    .put("classId", a.optString("classId"))
                                    .put("subject", a.optString("subject"))
                                    .put("title", title.trim())
                                    .put("questions", arr)
                            )
                            onSaved()
                        } catch (e: Throwable) {
                            error = e.message ?: "ثبت تمرین ناموفق بود."
                        } finally { busy = false }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CBrand)
            ) { Text(if (busy) "در حال انتشار…" else "انتشار تمرین") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
fun LibraryTeacherScreen(workspace: JSONObject?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var itemsList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var assignments by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var addOpen by remember { mutableStateOf(false) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var openingId by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedUri = uri
        if (uri != null) runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    suspend fun reload() {
        loading = true; error = ""
        try {
            itemsList = ApiClient.get("/api/library/p62/list").optJSONArray("items")?.contentObjects().orEmpty()
            assignments = ApiClient.get("/api/library/p62/assignments").optJSONArray("assignments")?.contentObjects().orEmpty()
        } catch (e: Throwable) {
            error = e.message ?: "خطا در کتابخانه"
        } finally { loading = false }
    }

    suspend fun openItem(item: JSONObject) {
        val id = item.optString("id")
        if (id.isBlank()) return
        openingId = id
        try {
            val response = ApiClient.download("/api/library/p62/file/$id")
            val mime = response.contentType?.substringBefore(';') ?: item.optString("mime", "application/octet-stream")
            val ext = when (mime) {
                "application/pdf" -> "pdf"
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                "text/plain" -> "txt"
                else -> "bin"
            }
            val dir = File(context.cacheDir, "library-open").apply { mkdirs() }
            val file = File(dir, "${id}.$ext").apply { writeBytes(response.bytes) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            (context as? MainActivity)?.suppressNextUnlock()
            context.startActivity(Intent.createChooser(intent, "باز کردن فایل آموزشی"))
        } finally { openingId = "" }
    }

    LaunchedEffect(Unit) { reload() }

    Column(Modifier.fillMaxSize().background(CBg)) {
        ContentHeader("کتابخانه دیجیتال", "منابع آموزشی، جزوه و فایل‌های کلاس")
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { selectedUri = null; addOpen = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = CBrand)
            ) { Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(5.dp)); Text("افزودن محتوا") }
            OutlinedButton(onClick = { scope.launch { reload() } }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(5.dp)); Text("تازه‌سازی")
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = CBrand)
        if (error.isNotBlank()) Text(error, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(itemsList) { item ->
                ContentCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(44.dp), RoundedCornerShape(13.dp), CBrand.copy(alpha = .1f)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Description, null, tint = CBrand) }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.optString("title", "محتوا"), fontWeight = FontWeight.Black, color = CText)
                            Text("${item.optString("subject")} · ${item.optString("teacherName")}", color = CMuted, fontSize = 11.sp)
                        }
                        FilledTonalButton(
                            enabled = openingId != item.optString("id"),
                            onClick = { scope.launch { try { openItem(item) } catch (e: Throwable) { error = e.message ?: "باز کردن فایل ناموفق بود." } } }
                        ) { Text(if (openingId == item.optString("id")) "…" else "باز کردن") }
                    }
                }
            }
            if (!loading && itemsList.isEmpty() && error.isBlank()) item {
                Text("هنوز محتوایی ثبت نشده است.", Modifier.padding(24.dp), color = CMuted)
            }
        }
    }

    if (addOpen) {
        LibraryCreateDialog(
            assignments = assignments,
            selectedUri = selectedUri,
            onPickFile = {
                (context as? MainActivity)?.suppressNextUnlock()
                picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp", "text/plain"))
            },
            onDismiss = { addOpen = false; selectedUri = null },
            onSaved = { addOpen = false; selectedUri = null; scope.launch { reload() } }
        )
    }
}

@Composable
private fun LibraryCreateDialog(
    assignments: List<JSONObject>,
    selectedUri: Uri?,
    onPickFile: () -> Unit,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var assignmentIndex by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val displayName = remember(selectedUri) {
        selectedUri?.let { uri ->
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("افزودن محتوای آموزشی", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                item {
                    Text("کلاس و درس", fontWeight = FontWeight.Bold)
                    assignments.forEachIndexed { index, a ->
                        FilterChip(
                            selected = assignmentIndex == index,
                            onClick = { assignmentIndex = index },
                            label = { Text("${a.optString("className", a.optString("classId"))} · ${a.optString("subject")}") }
                        )
                    }
                    OutlinedTextField(title, { title = it.take(140) }, Modifier.fillMaxWidth(), label = { Text("عنوان") })
                    OutlinedTextField(description, { description = it.take(600) }, Modifier.fillMaxWidth(), label = { Text("توضیحات") }, minLines = 2)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onPickFile, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.AttachFile, null); Spacer(Modifier.width(5.dp)); Text(displayName ?: "انتخاب PDF، تصویر یا متن")
                    }
                    Text("حداکثر حجم فایل: ۱۲ مگابایت", color = CMuted, fontSize = 11.sp)
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && assignments.isNotEmpty() && title.isNotBlank() && selectedUri != null,
                onClick = {
                    val a = assignments.getOrNull(assignmentIndex) ?: return@Button
                    val uri = selectedUri ?: return@Button
                    scope.launch {
                        busy = true; error = ""
                        try {
                            val file = withContext(Dispatchers.IO) {
                                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                    ?: throw IllegalStateException("فایل قابل خواندن نیست.")
                                if (bytes.size > 12 * 1024 * 1024) throw IllegalArgumentException("حجم فایل بیش از ۱۲ مگابایت است.")
                                bytes
                            }
                            val mime = context.contentResolver.getType(uri) ?: when {
                                displayName?.endsWith(".pdf", true) == true -> "application/pdf"
                                displayName?.endsWith(".png", true) == true -> "image/png"
                                displayName?.endsWith(".webp", true) == true -> "image/webp"
                                displayName?.endsWith(".jpg", true) == true || displayName?.endsWith(".jpeg", true) == true -> "image/jpeg"
                                displayName?.endsWith(".txt", true) == true -> "text/plain"
                                else -> throw IllegalArgumentException("نوع فایل پشتیبانی نمی‌شود.")
                            }
                            val allowed = setOf("application/pdf", "image/jpeg", "image/png", "image/webp", "text/plain")
                            if (mime !in allowed) throw IllegalArgumentException("نوع فایل پشتیبانی نمی‌شود.")
                            ApiClient.post(
                                "/api/library/p62/create",
                                JSONObject()
                                    .put("classId", a.optString("classId"))
                                    .put("subject", a.optString("subject"))
                                    .put("title", title.trim())
                                    .put("description", description.trim())
                                    .put("mime", mime)
                                    .put("name", displayName ?: "file")
                                    .put("data", Base64.encodeToString(file, Base64.NO_WRAP))
                            )
                            onSaved()
                        } catch (e: Throwable) {
                            error = e.message ?: "بارگذاری فایل ناموفق بود."
                        } finally { busy = false }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CBrand)
            ) { Text(if (busy) "در حال بارگذاری…" else "ذخیره محتوا") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
fun IndependentExamCenterScreen() {
    val scope = rememberCoroutineScope()
    var assignments by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var questions by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var quizzes by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var publishOpen by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<JSONObject?>(null) }
    var resultTitle by remember { mutableStateOf("") }

    suspend fun reload() {
        loading = true; error = ""
        try {
            assignments = ApiClient.get("/api/p635/assignments").optJSONArray("assignments")?.contentObjects().orEmpty()
            questions = ApiClient.get("/api/p635/questions").optJSONArray("questions")?.contentObjects().orEmpty()
            quizzes = ApiClient.get("/api/p635/teacher/quizzes").optJSONArray("quizzes")?.contentObjects().orEmpty()
        } catch (e: Throwable) {
            error = e.message ?: "خطا در مرکز آزمون مستقل"
        } finally { loading = false }
    }

    LaunchedEffect(Unit) { reload() }
    Column(Modifier.fillMaxSize().background(CBg)) {
        ContentHeader("بانک سؤال و آزمون مستقل", "مرکز تمرینی مستقل از نمرات رسمی مدرسه")
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = CBrand)
        if (error.isNotBlank()) Text(error, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { publishOpen = true },
                enabled = questions.isNotEmpty() && assignments.isNotEmpty(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = CBrand)
            ) { Icon(Icons.Default.Publish, null); Spacer(Modifier.width(5.dp)); Text("انتشار آزمون") }
            OutlinedButton(onClick = { scope.launch { reload() } }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(5.dp)); Text("تازه‌سازی")
            }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            item {
                ContentCard {
                    Text("وضعیت مرکز آزمون", fontWeight = FontWeight.Black, color = CText)
                    Text("${questions.size} سؤال شخصی · ${quizzes.size} آزمون منتشرشده", color = CMuted, fontSize = 12.sp)
                }
            }
            item { Text("آزمون‌های منتشرشده", fontWeight = FontWeight.Black, color = CBrandDark, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)) }
            items(quizzes) { q ->
                ContentCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(q.optString("title", "آزمون تمرینی"), fontWeight = FontWeight.Black, color = CText)
                            Text("${q.optString("subject")} · ${q.optString("className")}", color = CMuted, fontSize = 12.sp)
                            Text("${q.optInt("questionCount")} سؤال · ${q.optInt("participantCount")} شرکت‌کننده · ${q.optInt("attemptCount")} تلاش", color = CMuted, fontSize = 11.sp)
                        }
                        Surface(shape = RoundedCornerShape(10.dp), color = if (q.optBoolean("active", true)) Color(0xFFE8F8F0) else Color(0xFFF1F3F5)) {
                            Text(if (q.optBoolean("active", true)) "فعال" else "غیرفعال", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = if (q.optBoolean("active", true)) CSuccess else CMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                try {
                                    val id = q.optString("id")
                                    results = ApiClient.get("/api/p635/teacher/quizzes/$id/results")
                                    resultTitle = q.optString("title", "نتایج آزمون")
                                } catch (e: Throwable) { error = e.message ?: "خطا" }
                            }
                        }) { Text("نتایج") }
                        if (q.optBoolean("active", true)) {
                            TextButton(onClick = {
                                scope.launch {
                                    try {
                                        ApiClient.patch("/api/p635/teacher/quizzes/${q.optString("id")}", JSONObject().put("active", false))
                                        reload()
                                    } catch (e: Throwable) { error = e.message ?: "خطا" }
                                }
                            }) { Text("غیرفعال‌سازی", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            if (quizzes.isEmpty() && !loading) item { Text("هنوز آزمون مستقلی منتشر نشده است.", Modifier.padding(22.dp), color = CMuted) }
        }
    }

    if (publishOpen) IndependentQuizPublishDialog(
        assignments = assignments,
        questions = questions,
        onDismiss = { publishOpen = false },
        onSaved = { publishOpen = false; scope.launch { reload() } }
    )
    results?.let { data ->
        val participants = data.optJSONArray("participants")?.contentObjects().orEmpty()
        val stats = data.optJSONObject("stats")
        AlertDialog(
            onDismissRequest = { results = null },
            title = { Text(resultTitle, fontWeight = FontWeight.Black) },
            text = {
                LazyColumn {
                    item {
                        Text("${stats?.optInt("participantCount", 0)} شرکت‌کننده · میانگین ${stats?.opt("averagePercent") ?: "—"}٪", color = CMuted)
                        Spacer(Modifier.height(8.dp))
                    }
                    items(participants) { p ->
                        Text(p.optString("studentName"), fontWeight = FontWeight.Bold)
                        val latest = p.optJSONObject("latest")
                        Text("بهترین: ${p.optInt("bestPercent")}٪ · آخرین: ${latest?.optInt("percent", 0) ?: 0}٪ · ${p.optInt("attemptCount")} تلاش", fontSize = 11.sp, color = CMuted)
                        HorizontalDivider(Modifier.padding(vertical = 7.dp))
                    }
                    if (participants.isEmpty()) item { Text("هنوز دانش‌آموزی شرکت نکرده است.", color = CMuted) }
                }
            },
            confirmButton = { TextButton(onClick = { results = null }) { Text("بستن") } }
        )
    }
}

@Composable
private fun IndependentQuizPublishDialog(
    assignments: List<JSONObject>,
    questions: List<JSONObject>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var assignmentIndex by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("0") }
    var attempts by remember { mutableStateOf("0") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }
    val assignment = assignments.getOrNull(assignmentIndex)
    val allowedQuestions = questions.filter { q ->
        q.optString("classId") == assignment?.optString("classId") && q.optString("subject") == assignment?.optString("subject")
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("انتشار آزمون تمرینی", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn {
                item {
                    Text("کلاس و درس", fontWeight = FontWeight.Bold)
                    assignments.forEachIndexed { index, a ->
                        FilterChip(
                            selected = index == assignmentIndex,
                            onClick = { assignmentIndex = index; selectedIds.clear() },
                            label = { Text("${a.optString("className")} · ${a.optString("subject")}") }
                        )
                    }
                    OutlinedTextField(title, { title = it.take(140) }, Modifier.fillMaxWidth(), label = { Text("عنوان آزمون") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(duration, { duration = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), label = { Text("مدت دقیقه؛ ۰ نامحدود") })
                        OutlinedTextField(attempts, { attempts = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("دفعات مجاز؛ ۰ نامحدود") })
                    }
                    Text("انتخاب سؤال‌ها (${selectedIds.size})", Modifier.padding(top = 10.dp, bottom = 4.dp), fontWeight = FontWeight.Bold)
                }
                items(allowedQuestions) { q ->
                    val id = q.optString("id")
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selectedIds.contains(id), onCheckedChange = { checked -> if (checked) selectedIds.add(id) else selectedIds.remove(id) })
                        Column(Modifier.weight(1f)) {
                            Text(q.optString("text", "سؤال"), fontSize = 12.sp, color = CText)
                            Text(q.optString("chapter"), fontSize = 10.sp, color = CMuted)
                        }
                    }
                }
                if (allowedQuestions.isEmpty()) item { Text("برای این کلاس و درس سؤال مستقل ثبت نشده است.", color = MaterialTheme.colorScheme.error) }
                if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && assignment != null && selectedIds.isNotEmpty(),
                onClick = {
                    val a = assignment ?: return@Button
                    scope.launch {
                        busy = true; error = ""
                        try {
                            val ids = JSONArray(); selectedIds.forEach { ids.put(it) }
                            val d = duration.toIntOrNull() ?: 0
                            val max = attempts.toIntOrNull() ?: 0
                            ApiClient.post("/api/p635/quizzes", JSONObject()
                                .put("classId", a.optString("classId"))
                                .put("subject", a.optString("subject"))
                                .put("title", title.trim())
                                .put("questionIds", ids)
                                .put("count", ids.length())
                                .put("chapter", "")
                                .put("practicePolicy", JSONObject().put("durationMinutes", d).put("maxAttempts", max).put("resultMode", "immediate")))
                            onSaved()
                        } catch (e: Throwable) { error = e.message ?: "انتشار آزمون ناموفق بود." }
                        finally { busy = false }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = CBrand)
            ) { Text(if (busy) "در حال انتشار…" else "انتشار آزمون") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("انصراف") } }
    )
}
