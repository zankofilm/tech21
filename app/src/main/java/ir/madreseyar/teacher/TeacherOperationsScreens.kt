package ir.madreseyar.teacher

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val OBrand = Color(0xFF0CA6A4)
private val OBrandDark = Color(0xFF075A63)
private val OBg = Color(0xFFF3F9FB)
private val OText = Color(0xFF12313A)
private val OMuted = Color(0xFF71838A)
private val OSuccess = Color(0xFF16A36A)
private val OWarning = Color(0xFFF5A524)

private fun JSONArray.opObjects(): List<JSONObject> = buildList {
    for (i in 0 until length()) optJSONObject(i)?.let(::add)
}

@Composable
private fun OpHeader(title: String, subtitle: String) {
    Surface(color = OBrandDark) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Color.White.copy(.78f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun OpCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        RoundedCornerShape(18.dp),
        CardDefaults.cardColors(Color.White),
        CardDefaults.cardElevation(1.dp)
    ) { Column(Modifier.padding(14.dp), content = content) }
}

@Composable
fun AttendanceDisciplineInteractiveScreen() {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var recordStudent by remember { mutableStateOf<JSONObject?>(null) }

    suspend fun load() {
        loading = true; error = ""
        try { data = ApiClient.get("/api/staff/attendance-discipline") }
        catch (e: Throwable) { error = e.message ?: "خطا در دریافت وضعیت حضور و انضباط" }
        finally { loading = false }
    }
    LaunchedEffect(Unit) { load() }

    val summary = data?.optJSONObject("summary")
    val students = data?.optJSONArray("students")?.opObjects().orEmpty()
    val recent = data?.optJSONArray("recentBehavior")?.opObjects().orEmpty()

    Column(Modifier.fillMaxSize().background(OBg)) {
        OpHeader("حضور و انضباط", "غیبت، تأخیر و ثبت رویداد رفتاری")
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = OBrand)
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                if (summary != null) {
                    OpCard {
                        Text("خلاصه امروز و سوابق", fontWeight = FontWeight.Black, color = OText)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricBox("دانش‌آموز", summary.optInt("students"), OBrand, Modifier.weight(1f))
                            MetricBox("غیبت", summary.optInt("absent"), Color(0xFFD64545), Modifier.weight(1f))
                            MetricBox("تأخیر", summary.optInt("late"), OWarning, Modifier.weight(1f))
                        }
                    }
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
                Text("دانش‌آموزان", fontWeight = FontWeight.Black, color = OBrandDark, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            }
            items(students) { st ->
                val att = st.optJSONObject("attendance") ?: JSONObject()
                val disc = st.optJSONObject("discipline") ?: JSONObject()
                OpCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(42.dp), RoundedCornerShape(14.dp), if (st.optBoolean("attention")) Color(0xFFFFF1EC) else Color(0xFFE8F9F4)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(if (st.optBoolean("attention")) Icons.Default.WarningAmber else Icons.Default.Person, null, tint = if (st.optBoolean("attention")) Color(0xFFD7662B) else OBrand)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(st.optString("name", "دانش‌آموز"), fontWeight = FontWeight.Black)
                            Text(st.optString("className"), color = OMuted, fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = { recordStudent = st }) { Text("ثبت رویداد") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("غیبت ${att.optInt("absent")} · تأخیر ${att.optInt("late")} · تشویق ${disc.optInt("positive")} · موارد انضباطی ${disc.optInt("incidents")}", color = OMuted, fontSize = 12.sp)
                }
            }
            if (recent.isNotEmpty()) {
                item { Text("آخرین رویدادها", fontWeight = FontWeight.Black, color = OBrandDark, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                items(recent.take(30)) { row ->
                    OpCard {
                        Text("${row.optString("studentName")} · ${row.optString("title")}", fontWeight = FontWeight.Bold)
                        if (row.optString("note").isNotBlank()) Text(row.optString("note"), color = OMuted, fontSize = 12.sp)
                        Text(row.optString("createdAt"), color = OMuted, fontSize = 10.sp)
                    }
                }
            }
        }
    }

    recordStudent?.let { student ->
        DisciplineRecordDialog(
            student = student,
            onDismiss = { recordStudent = null },
            onSaved = { recordStudent = null; scope.launch { load() } }
        )
    }
}

@Composable
private fun MetricBox(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier, RoundedCornerShape(14.dp), color.copy(alpha = .09f)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), fontWeight = FontWeight.Black, fontSize = 19.sp, color = color)
            Text(label, fontSize = 10.sp, color = color)
        }
    }
}

@Composable
private fun DisciplineRecordDialog(student: JSONObject, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf("positive") }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val types = listOf("positive" to "تشویق", "warning" to "هشدار", "incident" to "رویداد انضباطی")

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("ثبت رویداد برای ${student.optString("name")}", fontWeight = FontWeight.Black) },
        text = {
            Column {
                types.forEach { (id, label) ->
                    FilterChip(selected = type == id, onClick = { type = id }, label = { Text(label) })
                }
                OutlinedTextField(title, { title = it.take(180) }, Modifier.fillMaxWidth(), label = { Text("عنوان") })
                OutlinedTextField(note, { note = it.take(1200) }, Modifier.fillMaxWidth(), label = { Text("توضیح") }, minLines = 3)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && title.trim().length >= 2,
                onClick = {
                    scope.launch {
                        busy = true; error = ""
                        try {
                            ApiClient.post(
                                "/api/staff/discipline/record",
                                JSONObject()
                                    .put("studentId", student.optString("id"))
                                    .put("type", type)
                                    .put("title", title.trim())
                                    .put("note", note.trim())
                                    .put("academicPeriod", "semester2")
                            )
                            onSaved()
                        } catch (e: Throwable) { error = e.message ?: "ثبت رویداد ناموفق بود." }
                        finally { busy = false }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = OBrand)
            ) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("انصراف") } }
    )
}

@Composable
fun ActionCenterInteractiveScreen() {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var createCase by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true; error = ""
        try { data = ApiClient.get("/api/staff/action-center") }
        catch (e: Throwable) { error = e.message ?: "خطا در مرکز اقدام" }
        finally { loading = false }
    }
    LaunchedEffect(Unit) { load() }

    val summary = data?.optJSONObject("summary")
    val tasks = data?.optJSONArray("tasks")?.opObjects().orEmpty()
    val cases = data?.optJSONArray("cases")?.opObjects().orEmpty()

    Column(Modifier.fillMaxSize().background(OBg)) {
        OpHeader("مرکز اقدام", "کارها و پرونده‌های پیگیری آموزشی")
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { createCase = true }, colors = ButtonDefaults.buttonColors(containerColor = OBrand), modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.AddTask, null); Spacer(Modifier.width(5.dp)); Text("پرونده جدید")
            }
            OutlinedButton(onClick = { scope.launch { load() } }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Refresh, null); Text("تازه‌سازی") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = OBrand)
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            item {
                if (summary != null) OpCard {
                    Text("خلاصه پیگیری", fontWeight = FontWeight.Black)
                    Text("پرونده باز ${summary.optInt("openCases")} · کار باز ${summary.optInt("openTasks")} · عقب‌افتاده ${summary.optInt("overdueTasks") + summary.optInt("overdueCases")}", color = OMuted)
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Text("کارهای من", fontWeight = FontWeight.Black, color = OBrandDark, modifier = Modifier.padding(top = 8.dp))
            }
            items(tasks) { task ->
                OpCard {
                    Text(task.optString("title", "اقدام"), fontWeight = FontWeight.Black)
                    Text("${task.optString("priority")} · ${task.optString("dueDate")}", color = OMuted, fontSize = 11.sp)
                    if (task.optString("note").isNotBlank()) Text(task.optString("note"), color = OMuted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (task.optString("status") == "open") OutlinedButton(onClick = {
                            scope.launch { try { ApiClient.post("/api/staff/action-center/task-status", JSONObject().put("taskId", task.optString("id")).put("status", "in_progress")); load() } catch (e: Throwable) { error = e.message ?: "خطا" } }
                        }) { Text("شروع") }
                        if (task.optString("status") !in listOf("done", "closed")) Button(onClick = {
                            scope.launch { try { ApiClient.post("/api/staff/action-center/task-status", JSONObject().put("taskId", task.optString("id")).put("status", "done")); load() } catch (e: Throwable) { error = e.message ?: "خطا" } }
                        }, colors = ButtonDefaults.buttonColors(containerColor = OBrand)) { Text("انجام شد") }
                    }
                }
            }
            item { Text("پرونده‌های پیگیری", fontWeight = FontWeight.Black, color = OBrandDark, modifier = Modifier.padding(top = 12.dp)) }
            items(cases) { row ->
                OpCard {
                    Text(row.optString("studentName", "دانش‌آموز"), fontWeight = FontWeight.Black)
                    Text("${row.optString("status")} · اولویت ${row.optString("priority")} · ${row.optString("dueDate")}", color = OMuted, fontSize = 11.sp)
                    if (row.optString("note").isNotBlank()) Text(row.optString("note"), color = OMuted, fontSize = 12.sp)
                }
            }
        }
    }

    if (createCase) {
        ActionCaseDialog(
            students = data?.optJSONArray("studentOptions")?.opObjects().orEmpty(),
            onDismiss = { createCase = false },
            onSaved = { createCase = false; scope.launch { load() } }
        )
    }
}

@Composable
private fun ActionCaseDialog(students: List<JSONObject>, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var studentIndex by remember { mutableIntStateOf(0) }
    var priority by remember { mutableStateOf("normal") }
    var dueDate by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("پرونده پیگیری جدید", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                item {
                    Text("دانش‌آموز", fontWeight = FontWeight.Bold)
                    students.take(80).forEachIndexed { index, student ->
                        FilterChip(selected = studentIndex == index, onClick = { studentIndex = index }, label = { Text(student.optString("name")) })
                    }
                    Text("اولویت", fontWeight = FontWeight.Bold)
                    listOf("low" to "کم", "normal" to "عادی", "high" to "بالا", "urgent" to "فوری").forEach { (id, label) ->
                        FilterChip(selected = priority == id, onClick = { priority = id }, label = { Text(label) })
                    }
                    OutlinedTextField(dueDate, { dueDate = it.take(10) }, Modifier.fillMaxWidth(), label = { Text("سررسید YYYY-MM-DD") })
                    OutlinedTextField(note, { note = it.take(1500) }, Modifier.fillMaxWidth(), label = { Text("یادداشت پیگیری") }, minLines = 3)
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && students.isNotEmpty(),
                onClick = {
                    val st = students.getOrNull(studentIndex) ?: return@Button
                    scope.launch {
                        busy = true; error = ""
                        try {
                            ApiClient.post(
                                "/api/staff/action-center/case",
                                JSONObject()
                                    .put("studentId", st.optString("id"))
                                    .put("status", "open")
                                    .put("priority", priority)
                                    .put("dueDate", dueDate.trim())
                                    .put("note", note.trim())
                            )
                            onSaved()
                        } catch (e: Throwable) { error = e.message ?: "ایجاد پرونده ناموفق بود." }
                        finally { busy = false }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = OBrand)
            ) { Text("ایجاد پرونده") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("انصراف") } }
    )
}

@Composable
fun CommunicationsInteractiveScreen() {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    suspend fun load() {
        loading = true; error = ""
        try { data = ApiClient.get("/api/staff/communications-center") }
        catch (e: Throwable) { error = e.message ?: "خطا در مرکز ارتباطات" }
        finally { loading = false }
    }
    LaunchedEffect(Unit) { load() }

    val summary = data?.optJSONObject("summary")
    val inbox = data?.optJSONArray("inbox")?.opObjects().orEmpty()
    val announcements = data?.optJSONArray("announcements")?.opObjects().orEmpty()

    Column(Modifier.fillMaxSize().background(OBg)) {
        OpHeader("ارتباطات و پیام‌ها", "اعلان‌های داخلی و اطلاعیه‌های مدرسه")
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = OBrand)
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                if (summary != null) OpCard {
                    Text("${summary.optInt("unread")} پیام خوانده‌نشده · ${summary.optInt("announcements")} اطلاعیه", fontWeight = FontWeight.Black)
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Text("صندوق من", fontWeight = FontWeight.Black, color = OBrandDark, modifier = Modifier.padding(top = 8.dp))
            }
            items(inbox) { msg ->
                OpCard {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(msg.optString("title", "پیام"), fontWeight = FontWeight.Black)
                            Text(msg.optString("body"), color = OMuted, fontSize = 12.sp)
                            Text(msg.optString("createdAt"), color = OMuted, fontSize = 10.sp)
                        }
                        if (!msg.optBoolean("read")) FilledTonalButton(onClick = {
                            scope.launch {
                                try { ApiClient.post("/api/staff/communications/read", JSONObject().put("id", msg.optString("id"))); load() }
                                catch (e: Throwable) { error = e.message ?: "خطا" }
                            }
                        }) { Text("خواندم") }
                    }
                }
            }
            item { Text("اطلاعیه‌های مدرسه", fontWeight = FontWeight.Black, color = OBrandDark, modifier = Modifier.padding(top = 12.dp)) }
            items(announcements) { a ->
                OpCard {
                    Text(a.optString("title", "اطلاعیه"), fontWeight = FontWeight.Black)
                    Text(a.optString("body"), color = OMuted, fontSize = 12.sp)
                    Text(a.optString("createdAt"), color = OMuted, fontSize = 10.sp)
                }
            }
        }
    }
}
