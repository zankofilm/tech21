package ir.madreseyar.teacher

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val DWBrand = Color(0xFF0CA6A4)
private val DWBrandDark = Color(0xFF087782)
private val DWBg = Color(0xFFF3F9FB)
private val DWText = Color(0xFF12313A)
private val DWMuted = Color(0xFF71838A)
private val DWGood = Color(0xFF16A36A)
private val DWWarn = Color(0xFFF5A524)
private val DWBad = Color(0xFFD94A4A)

private fun JSONArray?.dwObjects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
private fun enc(v: String): String = URLEncoder.encode(v, Charsets.UTF_8.name())
private fun JSONObject.displayName(): String = listOf(optString("firstName"), optString("lastName")).filter { it.isNotBlank() }.joinToString(" ").ifBlank { optString("name", "دانش‌آموز") }
private fun fmt(v: Double?): String = if (v == null || v.isNaN()) "—" else if (kotlin.math.abs(v - v.toInt()) < .001) v.toInt().toString() else String.format(Locale.US, "%.2f", v)
private fun JSONObject.optNullableDouble(key: String): Double? = if (!has(key) || isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }
private fun assignmentRows(workspace: JSONObject?): List<JSONObject> = workspace?.optJSONArray("assignments").dwObjects()
private fun workspaceClassName(workspace: JSONObject?, classId: String): String = workspace?.optJSONArray("classes").dwObjects().firstOrNull { it.optString("id") == classId }?.optString("name").orEmpty()
private fun workspaceClassLabel(workspace: JSONObject?, classId: String): String {
    val c = workspace?.optJSONArray("classes").dwObjects().firstOrNull { it.optString("id") == classId } ?: return "کلاس"
    val name = c.optString("name", "کلاس")
    val grade = c.optString("grade").trim()
    return if (grade.isNotBlank() && !name.contains("پایه")) "$name · پایه $grade" else name
}
private fun JSONObject.copyJson(): JSONObject = JSONObject(toString())

@Composable
private fun DWHeader(title: String, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Surface(color = Color.White, shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black, fontSize = 20.sp, color = DWText)
                Text(subtitle, fontSize = 11.sp, color = DWMuted)
            }
            action?.invoke()
        }
    }
}

@Composable
private fun DWCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), RoundedCornerShape(20.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
    }
}

@Composable
private fun ErrorLine(message: String) {
    if (message.isNotBlank()) Surface(color = Color(0xFFFFEEEE), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(message, Modifier.padding(10.dp), color = Color(0xFFA52929), fontSize = 12.sp)
    }
}

@Composable
private fun EmptyLine(text: String) {
    Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Inbox, null, tint = DWMuted.copy(alpha = .6f), modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(8.dp)); Text(text, color = DWMuted, fontSize = 13.sp)
    }
}

private fun decodeDataImage(value: String) = runCatching {
    val b64 = value.substringAfter("base64,", "")
    if (b64.isBlank()) null else Base64.decode(b64, Base64.DEFAULT).let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
}.getOrNull()

@Composable
private fun StudentAvatar(photo: String, name: String, size: Int = 56) {
    val image = remember(photo) { decodeDataImage(photo) }
    Surface(Modifier.size(size.dp), CircleShape, Color(0xFFE7F7F6)) {
        if (image != null) Image(image, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(contentAlignment = Alignment.Center) {
            val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("")
            if (initials.isBlank()) Icon(Icons.Default.Person, null, tint = DWBrand)
            else Text(initials, fontWeight = FontWeight.Black, color = DWBrand, fontSize = (size / 3).sp)
        }
    }
}

private object PhotoCache { val values = ConcurrentHashMap<String, String>() }

@Composable
private fun RemoteStudentAvatar(studentId: String, name: String, size: Int = 56) {
    var photo by remember(studentId) { mutableStateOf(PhotoCache.values[studentId].orEmpty()) }
    LaunchedEffect(studentId) {
        if (studentId.isNotBlank() && photo.isBlank()) {
            runCatching { ApiClient.get("/api/workbench/students/${enc(studentId)}/profile") }
                .getOrNull()?.optString("photoDataUrl")?.takeIf { it.isNotBlank() }?.let {
                    PhotoCache.values[studentId] = it; photo = it
                }
        }
    }
    StudentAvatar(photo, name, size)
}

@Composable
private fun DWMiniStat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 80.dp)) {
        Icon(icon, null, tint = DWBrand, modifier = Modifier.size(20.dp))
        Text(value, fontWeight = FontWeight.Black, color = DWText)
        Text(label, color = DWMuted, fontSize = 10.sp)
    }
}

@Composable
private fun rememberEffectiveWorkspace(initial: JSONObject?): State<JSONObject?> {
    val state = remember { mutableStateOf(initial) }
    LaunchedEffect(initial) {
        if (initial != null) state.value = initial
        else runCatching { ApiClient.get("/api/teacher/workspace") }.getOrNull()?.let { state.value = it }
    }
    return state
}

// ---------- 1. Students + dossier (existing host APIs only) ----------
@Composable
fun StudentsProScreen(workspace: JSONObject?) {
    val effectiveWorkspace by rememberEffectiveWorkspace(workspace)
    val assignments = assignmentRows(effectiveWorkspace)
    val classOptions = remember(assignments) {
        assignments.map { it.optString("classId") }.filter { it.isNotBlank() }.distinct()
    }
    var selectedClassId by remember(effectiveWorkspace) { mutableStateOf(classOptions.firstOrNull().orEmpty()) }
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(classOptions.joinToString("|")) {
        if (selectedClassId !in classOptions) selectedClassId = classOptions.firstOrNull().orEmpty()
    }

    selectedId?.let { id -> StudentDossierView(id, onBack = { selectedId = null }); return }

    val students = effectiveWorkspace?.optJSONArray("students").dwObjects().filter { it.optString("classId") == selectedClassId }
    val filtered = students.filter { query.isBlank() || it.displayName().contains(query, true) || it.optString("studentCode").contains(query, true) }

    Column(Modifier.fillMaxSize().background(DWBg)) {
        DWHeader("دانش‌آموزان من", "ابتدا کلاس و پایه را انتخاب کنید")
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.School, null)
                    Spacer(Modifier.width(8.dp))
                    Text(workspaceClassLabel(effectiveWorkspace, selectedClassId).ifBlank { "کلاس و پایه را انتخاب کنید" }, Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth(.86f)) {
                    classOptions.forEach { classId ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(workspaceClassLabel(effectiveWorkspace, classId), fontWeight = FontWeight.Bold)
                                    val subjects = assignments.filter { it.optString("classId") == classId }.map { it.optString("subject") }.filter { it.isNotBlank() }.distinct()
                                    if (subjects.isNotEmpty()) Text(subjects.joinToString("، "), fontSize = 11.sp, color = DWMuted)
                                }
                            },
                            onClick = { selectedClassId = classId; expanded = false; query = "" }
                        )
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Text("${students.size} دانش‌آموز در ${workspaceClassLabel(effectiveWorkspace, selectedClassId)}", color = DWMuted, fontSize = 11.sp)
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("جستجوی نام یا کد دانش‌آموز") }, singleLine = true, shape = RoundedCornerShape(16.dp))
        if (effectiveWorkspace == null) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DWBrand)
        if (effectiveWorkspace != null && filtered.isEmpty()) EmptyLine("دانش‌آموزی در این کلاس پیدا نشد.")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 6.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(filtered, key = { it.optString("id") }) { s ->
                val name = s.displayName()
                DWCard(Modifier.clickable { selectedId = s.optString("id") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RemoteStudentAvatar(s.optString("id"), name)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.Black, fontSize = 16.sp, color = DWText)
                            Text("${workspaceClassLabel(effectiveWorkspace, s.optString("classId"))} · کد ${s.optString("studentCode", "—")}", color = DWMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.ChevronLeft, null, tint = DWMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentDossierView(studentId: String, onBack: () -> Unit) {
    var dossier by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(studentId) {
        try { dossier = ApiClient.get("/api/workbench/students/${enc(studentId)}/dossier").optJSONObject("dossier") }
        catch (e: Throwable) { error = e.message ?: "پرونده دانش‌آموز دریافت نشد." }
        finally { loading = false }
    }
    val d = dossier
    val profile = d?.optJSONObject("student") ?: JSONObject()
    Column(Modifier.fillMaxSize().background(DWBg)) {
        DWHeader("پرونده دانش‌آموز", "اطلاعات آموزشی، حضور و خانواده") { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = DWBrand) } }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DWBrand)
        ErrorLine(error)
        if (d != null) LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { DWCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudentAvatar(d.optString("photoDataUrl"), profile.displayName(), 76)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.displayName(), fontWeight = FontWeight.Black, fontSize = 20.sp, color = DWText)
                        Text("کد دانش‌آموز: ${profile.optString("studentCode", "—")}", color = DWMuted)
                        Text(profile.optString("className", profile.optString("classId")), color = DWBrandDark, fontWeight = FontWeight.Bold)
                    }
                }
            } }
            item {
                val a = d.optJSONObject("analysis") ?: JSONObject(); val att = d.optJSONObject("attendance") ?: JSONObject()
                DWCard {
                    Text("وضعیت سریع", fontWeight = FontWeight.Black, color = DWText)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DWMiniStat("میانگین", "${fmt(a.optNullableDouble("overallAveragePercent"))}٪", Icons.Default.AutoGraph)
                        DWMiniStat("حضور", att.optInt("present", att.optInt("presentCount", 0)).toString(), Icons.Default.FactCheck)
                        DWMiniStat("غیبت", att.optInt("absent", att.optInt("absentCount", 0)).toString(), Icons.Default.EventBusy)
                    }
                    a.optJSONObject("status")?.let { Text("وضعیت آموزشی: ${it.optString("label", it.optString("title", "—"))}", color = DWBrandDark, fontWeight = FontWeight.Bold) }
                }
            }
            item {
                val guardians = d.optJSONArray("guardians").dwObjects()
                if (guardians.isNotEmpty()) DWCard {
                    Text("خانواده و تماس", fontWeight = FontWeight.Black, color = DWText)
                    guardians.forEach { g -> Text("${g.optString("name", g.displayName())} · ${g.optString("relation", "ولی")} · ${g.optString("mobile", g.optString("phone", "—"))}", color = DWMuted, fontSize = 12.sp) }
                }
            }
            item {
                val subjects = d.optJSONObject("analysis")?.optJSONArray("subjects").dwObjects()
                if (subjects.isNotEmpty()) DWCard {
                    Text("عملکرد در درس‌ها", fontWeight = FontWeight.Black, color = DWText)
                    subjects.take(12).forEach { r -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.optString("subject", "درس"), color = DWText); Text("${fmt(r.optNullableDouble("percent"))}٪", color = DWBrandDark, fontWeight = FontWeight.Bold)
                    } }
                }
            }
        }
    }
}

// ---------- 2. Professional gradebook (existing /api/staff/gradebook + /api/state) ----------
@Composable
fun ProfessionalGradebookScreen(workspace: JSONObject?) {
    val effectiveWorkspace by rememberEffectiveWorkspace(workspace)
    val scope = rememberCoroutineScope(); val assignments = assignmentRows(effectiveWorkspace)
    var assignmentIndex by remember { mutableIntStateOf(0) }; var period by remember { mutableStateOf("semester2") }
    var data by remember { mutableStateOf<JSONObject?>(null) }; var stateDb by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }; var savingId by remember { mutableStateOf("") }
    val continuousInputs = remember { mutableStateMapOf<String, String>() }; val finalInputs = remember { mutableStateMapOf<String, String>() }

    suspend fun reload() {
        val a = assignments.getOrNull(assignmentIndex) ?: return
        loading = true; error = ""
        try {
            val g = ApiClient.get("/api/staff/gradebook?classId=${enc(a.optString("classId"))}&subject=${enc(a.optString("subject"))}&academicPeriod=$period")
            val snapshot = ApiClient.getState(); data = g; stateDb = snapshot.db
            val cMax = g.optJSONObject("config")?.optDouble("outputMaxScore", 5.0) ?: 5.0
            val year = snapshot.db.optJSONObject("school")?.optString("year").orEmpty()
            continuousInputs.clear(); finalInputs.clear()
            g.optJSONArray("students").dwObjects().forEach { s ->
                val id = s.optString("id")
                val approved = snapshot.db.optJSONArray("continuousGradeOverrides").dwObjects().filter { it.optBoolean("active", true) && it.optString("studentId") == id && it.optString("classId") == a.optString("classId") && it.optString("subject") == a.optString("subject") && it.optString("academicPeriod", "semester2") == period && (it.optString("academicYear").isBlank() || it.optString("academicYear") == year) }.maxByOrNull { it.optString("updatedAt") }
                val calc20 = s.optJSONObject("result")?.optNullableDouble("score")
                continuousInputs[id] = fmt(approved?.optNullableDouble("approvedRawScore") ?: calc20?.let { it / 20.0 * cMax })
                val finalMark = snapshot.db.optJSONArray("termFinalOverrides").dwObjects().filter { it.optBoolean("active", true) && it.optString("studentId") == id && it.optString("classId") == a.optString("classId") && it.optString("subject") == a.optString("subject") && it.optString("academicPeriod", "semester2") == period && (it.optString("academicYear").isBlank() || it.optString("academicYear") == year) }.maxByOrNull { it.optString("updatedAt") }
                finalInputs[id] = fmt(finalMark?.optNullableDouble("score"))
            }
        } catch (e: Throwable) { error = e.message ?: "دفتر نمره دریافت نشد." }
        finally { loading = false }
    }
    LaunchedEffect(assignmentIndex, period, assignments.size) { reload() }

    Column(Modifier.fillMaxSize().background(DWBg)) {
        DWHeader("دفتر نمره حرفه‌ای", "مستمر، پایانی و نتیجه نهایی") { IconButton(onClick = { scope.launch { reload() } }) { Icon(Icons.Default.Refresh, null, tint = DWBrand) } }
        if (assignments.isEmpty()) { if (effectiveWorkspace == null) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DWBrand) else EmptyLine("تخصیص تدریس فعالی پیدا نشد."); return@Column }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { DWCard {
                Text("کلاس و درس", fontWeight = FontWeight.Black, color = DWText)
                assignments.take(12).forEachIndexed { i, a -> FilterChip(selected = assignmentIndex == i, onClick = { assignmentIndex = i }, label = { Text("${a.optString("subject")} · ${workspaceClassName(effectiveWorkspace, a.optString("classId"))}") }) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = period == "semester1", onClick = { period = "semester1" }, label = { Text("نیم‌سال اول") })
                    FilterChip(selected = period == "semester2", onClick = { period = "semester2" }, label = { Text("نیم‌سال دوم") })
                }
            } }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = DWBrand) }
            if (error.isNotBlank()) item { ErrorLine(error) }
            data?.let { d ->
                val cMax = d.optJSONObject("config")?.optDouble("outputMaxScore", 5.0) ?: 5.0; val fMax = (20.0 - cMax).coerceAtLeast(0.0)
                item { DWCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DWMiniStat("سهم مستمر", fmt(cMax), Icons.Default.TrendingUp); DWMiniStat("سهم پایانی", fmt(fMax), Icons.Default.AssignmentTurnedIn); DWMiniStat("دانش‌آموز", d.optJSONArray("students")?.length()?.toString() ?: "0", Icons.Default.Groups)
                    }
                    if (d.optJSONObject("lock") != null) Text("این درس/نیم‌سال توسط مدیریت قفل شده است.", color = DWBad, fontWeight = FontWeight.Bold)
                } }
                items(d.optJSONArray("students").dwObjects(), key = { it.optString("id") }) { s ->
                    val id = s.optString("id"); val calc20 = s.optJSONObject("result")?.optNullableDouble("score")
                    val currentC = continuousInputs[id]?.toDoubleOrNull(); val currentF = finalInputs[id]?.toDoubleOrNull(); val termTotal = if (currentC != null || currentF != null) (currentC ?: 0.0) + (currentF ?: 0.0) else null
                    DWCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RemoteStudentAvatar(id, s.displayName(), 44); Spacer(Modifier.width(9.dp)); Column(Modifier.weight(1f)) {
                                Text(s.displayName(), fontWeight = FontWeight.Black, color = DWText); Text("مستمر محاسبه‌شده: ${fmt(calc20)} از ۲۰", color = DWMuted, fontSize = 10.sp)
                            }
                            Surface(color = DWBrand.copy(alpha = .1f), shape = RoundedCornerShape(12.dp)) { Text("${fmt(termTotal)} / ۲۰", Modifier.padding(8.dp), color = DWBrandDark, fontWeight = FontWeight.Black) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(continuousInputs[id].orEmpty(), { continuousInputs[id] = it }, Modifier.weight(1f), label = { Text("مستمر / ${fmt(cMax)}") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                            OutlinedTextField(finalInputs[id].orEmpty(), { finalInputs[id] = it }, Modifier.weight(1f), label = { Text("پایانی / ${fmt(fMax)}") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = fMax > 0)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = savingId.isBlank(), onClick = {
                                val a = assignments[assignmentIndex]; val score = continuousInputs[id]?.toDoubleOrNull(); if (score == null || score !in 0.0..cMax) { error = "نمره مستمر باید بین صفر تا ${fmt(cMax)} باشد."; return@Button }
                                scope.launch { savingId = id; try { ApiClient.post("/api/staff/gradebook/approve-continuous", JSONObject().put("studentId", id).put("classId", a.optString("classId")).put("subject", a.optString("subject")).put("academicPeriod", period).put("score", score)); reload() } catch (e: Throwable) { error = e.message ?: "ثبت نمره ناموفق بود." } finally { savingId = "" } }
                            }, colors = ButtonDefaults.buttonColors(containerColor = DWBrand), modifier = Modifier.weight(1f)) { Text(if (savingId == id) "…" else "ثبت مستمر") }
                            Button(enabled = savingId.isBlank() && fMax > 0, onClick = {
                                val a = assignments[assignmentIndex]; val score = finalInputs[id]?.toDoubleOrNull(); if (score == null || score !in 0.0..fMax) { error = "نمره پایانی باید بین صفر تا ${fmt(fMax)} باشد."; return@Button }
                                scope.launch { savingId = id; try { ApiClient.post("/api/staff/gradebook/final-mark", JSONObject().put("studentId", id).put("classId", a.optString("classId")).put("subject", a.optString("subject")).put("academicPeriod", period).put("score", score)); reload() } catch (e: Throwable) { error = e.message ?: "ثبت نمره ناموفق بود." } finally { savingId = "" } }
                            }, colors = ButtonDefaults.buttonColors(containerColor = DWBrandDark), modifier = Modifier.weight(1f)) { Text(if (savingId == id) "…" else "ثبت پایانی") }
                        }
                    }
                }
            }
        }
    }
}

// ---------- 3. Class journal via existing scoped /api/state ----------
@Composable
fun ClassJournalScreen(workspace: JSONObject?) {
    val effectiveWorkspace by rememberEffectiveWorkspace(workspace); val scope = rememberCoroutineScope(); val assignments = assignmentRows(effectiveWorkspace)
    var assignmentIndex by remember { mutableIntStateOf(0) }; var period by remember { mutableStateOf("semester2") }; var snapshot by remember { mutableStateOf<ApiClient.StateEnvelope?>(null) }
    var logs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }; var loading by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }; var edit by remember { mutableStateOf<JSONObject?>(null) }; var add by remember { mutableStateOf(false) }
    suspend fun reload() {
        val a = assignments.getOrNull(assignmentIndex) ?: return; loading = true; error = ""
        try { val s = ApiClient.getState(); snapshot = s; logs = s.db.optJSONArray("lessonLogs").dwObjects().filter { it.optString("classId") == a.optString("classId") && it.optString("subject") == a.optString("subject") && it.optString("academicPeriod", "semester2") == period }.sortedByDescending { it.optString("date") } }
        catch (e: Throwable) { error = e.message ?: "دفتر کلاسی دریافت نشد." } finally { loading = false }
    }
    LaunchedEffect(assignmentIndex, period, assignments.size) { reload() }
    Column(Modifier.fillMaxSize().background(DWBg)) {
        DWHeader("دفتر کلاسی", "ثبت جلسه، محتوای تدریس، تکلیف و یادداشت") { FilledIconButton(onClick = { add = true }, enabled = assignments.isNotEmpty(), colors = IconButtonDefaults.filledIconButtonColors(containerColor = DWBrand)) { Icon(Icons.Default.Add, null) } }
        if (assignments.isEmpty()) { if (effectiveWorkspace == null) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DWBrand) else EmptyLine("تخصیص تدریس فعالی وجود ندارد."); return@Column }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { DWCard {
                assignments.take(12).forEachIndexed { i, a -> FilterChip(selected = assignmentIndex == i, onClick = { assignmentIndex = i }, label = { Text("${a.optString("subject")} · ${workspaceClassName(effectiveWorkspace, a.optString("classId"))}") }) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = period == "semester1", onClick = { period = "semester1" }, label = { Text("نیم‌سال اول") }); FilterChip(selected = period == "semester2", onClick = { period = "semester2" }, label = { Text("نیم‌سال دوم") }) }
            } }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = DWBrand) }; if (error.isNotBlank()) item { ErrorLine(error) }; if (!loading && logs.isEmpty()) item { EmptyLine("هنوز جلسه‌ای ثبت نشده است.") }
            items(logs, key = { it.optString("id") }) { r ->
                val status = r.optString("status", "taught"); val statusFa = when (status) { "planned" -> "برنامه‌ریزی"; "cancelled" -> "لغوشده"; else -> "تدریس‌شده" }
                DWCard(Modifier.clickable { edit = r }) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(r.optString("title", r.optString("chapterTitle", "جلسه کلاس")), fontWeight = FontWeight.Black, color = DWText); Text("${r.optString("date")} · زنگ ${r.optInt("periodNo", 0)} · ${r.optString("chapterTitle")}", color = DWMuted, fontSize = 11.sp) }; AssistChip(onClick = {}, label = { Text(statusFa) }) }; if (r.optString("content").isNotBlank()) Text(r.optString("content"), maxLines = 3, overflow = TextOverflow.Ellipsis, color = DWText, fontSize = 12.sp); if (r.optString("homework").isNotBlank()) Text("تکلیف: ${r.optString("homework")}", color = DWWarn, fontSize = 11.sp) }
            }
        }
        if (add || edit != null) ClassJournalDialog(assignments.getOrNull(assignmentIndex), period, edit, onDismiss = { add = false; edit = null }, onSaved = { add = false; edit = null; scope.launch { reload() } })
    }
}

@Composable
private fun ClassJournalDialog(assignment: JSONObject?, period: String, existing: JSONObject?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope(); var date by remember(existing) { mutableStateOf(existing?.optString("date").orEmpty().ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }) }; var periodNo by remember(existing) { mutableStateOf(existing?.optInt("periodNo", 0)?.takeIf { it > 0 }?.toString().orEmpty()) }; var chapter by remember(existing) { mutableStateOf(existing?.optString("chapterTitle").orEmpty()) }; var title by remember(existing) { mutableStateOf(existing?.optString("title").orEmpty()) }; var content by remember(existing) { mutableStateOf(existing?.optString("content").orEmpty()) }; var homework by remember(existing) { mutableStateOf(existing?.optString("homework").orEmpty()) }; var note by remember(existing) { mutableStateOf(existing?.optString("privateNote").orEmpty()) }; var status by remember(existing) { mutableStateOf(existing?.optString("status", "taught") ?: "taught") }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) "ثبت جلسه جدید" else "ویرایش جلسه") }, text = { LazyColumn { item {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("planned" to "برنامه", "taught" to "تدریس شد", "cancelled" to "لغو شد").forEach { (k, l) -> FilterChip(selected = status == k, onClick = { status = k }, label = { Text(l) }) } }
        OutlinedTextField(date, { date = it }, Modifier.fillMaxWidth(), label = { Text("تاریخ YYYY-MM-DD") }, singleLine = true); OutlinedTextField(periodNo, { periodNo = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("شماره زنگ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); OutlinedTextField(chapter, { chapter = it }, Modifier.fillMaxWidth(), label = { Text("فصل/درس") }); OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("عنوان جلسه") }); OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth(), label = { Text("محتوای تدریس") }, minLines = 3); OutlinedTextField(homework, { homework = it }, Modifier.fillMaxWidth(), label = { Text("تکلیف این جلسه") }, minLines = 2); OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("یادداشت خصوصی معلم") }, minLines = 2); ErrorLine(error)
    } } }, confirmButton = { Button(enabled = !busy && assignment != null, onClick = {
        val a = assignment ?: return@Button; if (title.isBlank() && content.isBlank()) { error = "عنوان یا محتوای جلسه را وارد کنید."; return@Button }
        scope.launch { busy = true; error = ""; try {
            val snap = ApiClient.getState(); val db = snap.db; val arr = db.optJSONArray("lessonLogs") ?: JSONArray().also { db.put("lessonLogs", it) }; val id = existing?.optString("id").orEmpty().ifBlank { "lesson_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}" }; val now = System.currentTimeMillis().toString(); val teacherId = a.optString("teacherId").ifBlank { db.optJSONArray("users").dwObjects().firstOrNull { it.optString("role") == "teacher" }?.optString("id").orEmpty() }; val row = (existing?.copyJson() ?: JSONObject()).put("id", id).put("classId", a.optString("classId")).put("subject", a.optString("subject")).put("academicYear", db.optJSONObject("school")?.optString("year").orEmpty()).put("academicPeriod", period).put("teacherId", teacherId).put("date", date).put("periodNo", periodNo.toIntOrNull() ?: 0).put("chapterTitle", chapter.trim()).put("title", title.trim()).put("content", content.trim()).put("homework", homework.trim()).put("privateNote", note.trim()).put("status", status).put("updatedAt", now)
            var replaced = false; for (i in 0 until arr.length()) if (arr.optJSONObject(i)?.optString("id") == id) { arr.put(i, row); replaced = true; break }; if (!replaced) arr.put(row); ApiClient.saveState(snap); onSaved()
        } catch (e: Throwable) { error = e.message ?: "ثبت جلسه ناموفق بود." } finally { busy = false } }
    }, colors = ButtonDefaults.buttonColors(containerColor = DWBrand)) { Text(if (busy) "در حال ثبت…" else "ذخیره جلسه") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

// ---------- 4. Homework center via existing /api/state + dedicated save/grade ----------
private data class DWAttachment(val name: String, val type: String, val dataUrl: String, val size: Int)

private fun enrichHomework(db: JSONObject): List<JSONObject> {
    val classes = db.optJSONObject("school")?.optJSONArray("classes").dwObjects().associateBy { it.optString("id") }; val students = db.optJSONArray("users").dwObjects().filter { it.optString("role") == "student" }; val submissions = db.optJSONArray("homeworkSubmissions").dwObjects()
    return db.optJSONArray("homework").dwObjects().sortedByDescending { it.optString("createdAtIso", it.optString("createdAt")) }.map { h ->
        val row = h.copyJson(); val hs = submissions.filter { it.optString("homeworkId") == h.optString("id") }; val selectedIds = h.optJSONArray("studentIds")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty(); val assigned = if (h.optString("recipientMode") == "selected" && selectedIds.isNotEmpty()) selectedIds.size else students.count { it.optString("classId") == h.optString("classId") }
        val outSubs = JSONArray(); hs.forEach { sub -> val st = students.firstOrNull { it.optString("id") == sub.optString("studentId") }; outSubs.put(sub.copyJson().put("studentName", st?.displayName() ?: "دانش‌آموز").put("studentCode", st?.optString("studentCode") ?: "")) }
        row.put("className", classes[h.optString("classId")]?.optString("name") ?: "").put("assignedCount", assigned).put("submittedCount", hs.size).put("gradedCount", hs.count { it.optString("status") == "graded" }).put("submissions", outSubs)
    }
}

@Composable
fun HomeworkCenterProScreen(workspace: JSONObject?) {
    val effectiveWorkspace by rememberEffectiveWorkspace(workspace); val scope = rememberCoroutineScope(); var homework by remember { mutableStateOf<List<JSONObject>>(emptyList()) }; var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf("") }; var add by remember { mutableStateOf(false) }; var selected by remember { mutableStateOf<JSONObject?>(null) }
    suspend fun reload() { loading = true; error = ""; try { homework = enrichHomework(ApiClient.getState().db); selected?.let { old -> selected = homework.firstOrNull { it.optString("id") == old.optString("id") } } } catch (e: Throwable) { error = e.message ?: "مرکز تکالیف دریافت نشد." } finally { loading = false } }
    LaunchedEffect(Unit) { reload() }; selected?.let { h -> HomeworkDetailView(h, onBack = { selected = null }, onReload = { scope.launch { reload() } }); return }
    Column(Modifier.fillMaxSize().background(DWBg)) {
        DWHeader("مرکز تکالیف", "ساخت، تحویل‌ها، تصحیح و بازخورد") { FilledIconButton(onClick = { add = true }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = DWBrand)) { Icon(Icons.Default.Add, null) } }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DWBrand); ErrorLine(error); if (!loading && homework.isEmpty()) EmptyLine("هنوز تکلیفی ثبت نشده است.")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(homework, key = { it.optString("id") }) { h -> DWCard(Modifier.clickable { selected = h }) {
                Row(verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(45.dp), RoundedCornerShape(13.dp), DWBrand.copy(alpha = .1f)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Assignment, null, tint = DWBrand) } }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(h.optString("title", "تکلیف"), fontWeight = FontWeight.Black, color = DWText); Text("${h.optString("subject")} · ${h.optString("className")}", color = DWMuted, fontSize = 11.sp); Text("مهلت: ${h.optString("dueAtJalali", h.optString("dueAt", "—"))} ${h.optString("dueTime")}", color = DWMuted, fontSize = 10.sp) }; Icon(Icons.Default.ChevronLeft, null, tint = DWMuted) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${h.optInt("assignedCount")} نفر", color = DWMuted, fontSize = 11.sp); Text("${h.optInt("submittedCount")} تحویل", color = DWBrandDark, fontSize = 11.sp); Text("${h.optInt("gradedCount")} تصحیح", color = DWGood, fontSize = 11.sp) }
            } }
        }
        if (add) HomeworkProCreateDialog(effectiveWorkspace, onDismiss = { add = false }, onSaved = { add = false; scope.launch { reload() } })
    }
}

@Composable
private fun HomeworkDetailView(homework: JSONObject, onBack: () -> Unit, onReload: () -> Unit) {
    var grading by remember { mutableStateOf<JSONObject?>(null) }
    Column(Modifier.fillMaxSize().background(DWBg)) {
        DWHeader(homework.optString("title", "تکلیف"), "${homework.optString("subject")} · تحویل‌ها و تصحیح") { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = DWBrand) } }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { DWCard { Text(homework.optString("description"), color = DWText); Text("بارم: ${fmt(homework.optNullableDouble("totalScore"))} · مهلت: ${homework.optString("dueAtJalali", homework.optString("dueAt", "—"))}", color = DWMuted, fontSize = 11.sp); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { DWMiniStat("تخصیص", homework.optInt("assignedCount").toString(), Icons.Default.Groups); DWMiniStat("تحویل", homework.optInt("submittedCount").toString(), Icons.Default.UploadFile); DWMiniStat("تصحیح", homework.optInt("gradedCount").toString(), Icons.Default.DoneAll) } } }
            val subs = homework.optJSONArray("submissions").dwObjects(); if (subs.isEmpty()) item { EmptyLine("هنوز پاسخی تحویل نشده است.") }
            items(subs, key = { it.optString("id") }) { s -> DWCard {
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(s.optString("studentName", "دانش‌آموز"), fontWeight = FontWeight.Black, color = DWText); Text("کد ${s.optString("studentCode", "—")} · ${s.optString("submittedAt", s.optString("submittedAtIso"))}", fontSize = 10.sp, color = DWMuted) }; if (s.optString("status") == "graded") AssistChip(onClick = {}, label = { Text("${fmt(s.optNullableDouble("score"))}/${fmt(s.optNullableDouble("maxScore") ?: homework.optNullableDouble("totalScore"))}") }) }
                if (s.optString("text").isNotBlank()) Text("پاسخ: ${s.optString("text")}", color = DWText, fontSize = 12.sp); val attach = s.optJSONArray("attachmentIds")?.length() ?: 0; if (attach > 0) Text("$attach فایل پیوست دانش‌آموز", color = DWBrandDark, fontSize = 11.sp); if (s.optString("feedback").isNotBlank()) Text("بازخورد: ${s.optString("feedback")}", color = DWMuted, fontSize = 11.sp)
                Button(onClick = { grading = s }, colors = ButtonDefaults.buttonColors(containerColor = DWBrand), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Grading, null); Spacer(Modifier.width(5.dp)); Text(if (s.optString("status") == "graded") "ویرایش نمره" else "تصحیح تکلیف") }
            } }
        }
        grading?.let { s -> HomeworkGradeDialog(homework, s, onDismiss = { grading = null }, onSaved = { grading = null; onReload() }) }
    }
}

@Composable
private fun HomeworkGradeDialog(homework: JSONObject, submission: JSONObject, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope(); var score by remember { mutableStateOf(submission.optNullableDouble("score")?.let(::fmt).orEmpty()) }; var feedback by remember { mutableStateOf(submission.optString("feedback")) }; var error by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تصحیح ${submission.optString("studentName")}") }, text = { Column { Text("بارم: ${fmt(homework.optNullableDouble("totalScore"))}", color = DWMuted); OutlinedTextField(score, { score = it }, Modifier.fillMaxWidth(), label = { Text("نمره") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)); OutlinedTextField(feedback, { feedback = it }, Modifier.fillMaxWidth(), label = { Text("بازخورد معلم") }, minLines = 3); ErrorLine(error) } }, confirmButton = { Button(enabled = !busy, onClick = { val n = score.toDoubleOrNull(); val max = homework.optDouble("totalScore", 20.0); if (n == null || n !in 0.0..max) { error = "نمره باید بین صفر تا ${fmt(max)} باشد."; return@Button }; scope.launch { busy = true; try { ApiClient.post("/api/staff/homework/grade", JSONObject().put("submissionId", submission.optString("id")).put("score", n).put("feedback", feedback)); onSaved() } catch (e: Throwable) { error = e.message ?: "تصحیح ناموفق بود." } finally { busy = false } } }, colors = ButtonDefaults.buttonColors(containerColor = DWBrand)) { Text("ثبت نمره") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

@Composable
private fun HomeworkProCreateDialog(workspace: JSONObject?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val assignments = assignmentRows(workspace); var ai by remember { mutableIntStateOf(0) }; var title by remember { mutableStateOf("") }; var desc by remember { mutableStateOf("") }; var due by remember { mutableStateOf("") }; var dueTime by remember { mutableStateOf("") }; var total by remember { mutableStateOf("20") }; var publish by remember { mutableStateOf(true) }; var allowLate by remember { mutableStateOf(false) }; var selectedOnly by remember { mutableStateOf(false) }; val selectedIds = remember { mutableStateMapOf<String, Boolean>() }; var attachments by remember { mutableStateOf<List<DWAttachment>>(emptyList()) }; var error by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> -> attachments = uris.take(2).mapNotNull { uri -> runCatching { val type = context.contentResolver.getType(uri) ?: return@runCatching null; if (type !in setOf("image/jpeg", "image/png", "image/webp", "application/pdf")) return@runCatching null; val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null; if (bytes.size > 650 * 1024) return@runCatching null; var name = "پیوست"; context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) name = c.getString(0) ?: name }; DWAttachment(name, type, "data:$type;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}", bytes.size) }.getOrNull() } }
    val a = assignments.getOrNull(ai); val students = workspace?.optJSONArray("students").dwObjects().filter { it.optString("classId") == a?.optString("classId") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تکلیف جدید") }, text = { LazyColumn { item {
        assignments.take(10).forEachIndexed { i, x -> FilterChip(selected = ai == i, onClick = { ai = i; selectedIds.clear() }, label = { Text("${x.optString("subject")} · ${workspaceClassName(workspace, x.optString("classId"))}") }) }; OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("عنوان") }); OutlinedTextField(desc, { desc = it }, Modifier.fillMaxWidth(), label = { Text("توضیحات") }, minLines = 3); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedTextField(due, { due = it }, Modifier.weight(1f), label = { Text("تاریخ YYYY-MM-DD") }); OutlinedTextField(dueTime, { dueTime = it }, Modifier.weight(.7f), label = { Text("ساعت") }) }; OutlinedTextField(total, { total = it }, Modifier.fillMaxWidth(), label = { Text("بارم") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)); Row(verticalAlignment = Alignment.CenterVertically) { Switch(selectedOnly, { selectedOnly = it }); Text("فقط دانش‌آموزان انتخابی") }; if (selectedOnly) students.forEach { s -> FilterChip(selected = selectedIds[s.optString("id")] == true, onClick = { selectedIds[s.optString("id")] = !(selectedIds[s.optString("id")] ?: false) }, label = { Text(s.displayName()) }) }; Row(verticalAlignment = Alignment.CenterVertically) { Switch(publish, { publish = it }); Text("انتشار برای دانش‌آموزان") }; Row(verticalAlignment = Alignment.CenterVertically) { Switch(allowLate, { allowLate = it }); Text("اجازه تحویل دیرهنگام") }; OutlinedButton(onClick = { (context as? MainActivity)?.suppressNextUnlock(); picker.launch(arrayOf("image/jpeg", "image/png", "image/webp", "application/pdf")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.AttachFile, null); Spacer(Modifier.width(4.dp)); Text("پیوست تکلیف (${attachments.size}/2)") }; attachments.forEach { Text("• ${it.name} · ${it.size / 1024}KB", fontSize = 10.sp, color = DWMuted) }; ErrorLine(error)
    } } }, confirmButton = { Button(enabled = !busy && a != null, onClick = { val ass = a ?: return@Button; val selected = selectedIds.filterValues { it }.keys; if (title.trim().length < 3) { error = "عنوان تکلیف باید حداقل ۳ نویسه باشد."; return@Button }; if (desc.trim().length < 5) { error = "توضیحات تکلیف باید حداقل ۵ نویسه باشد."; return@Button }; if (due.isBlank() && dueTime.isNotBlank()) { error = "برای ساعت تحویل، تاریخ را هم وارد کنید."; return@Button }; if (selectedOnly && selected.isEmpty()) { error = "حداقل یک دانش‌آموز انتخاب کنید."; return@Button }; scope.launch { busy = true; try { val files = JSONArray(); attachments.forEach { files.put(JSONObject().put("name", it.name).put("type", it.type).put("size", it.size).put("dataUrl", it.dataUrl)) }; ApiClient.post("/api/staff/homework/p652/save", JSONObject().put("classId", ass.optString("classId")).put("subject", ass.optString("subject")).put("title", title.trim()).put("description", desc.trim()).put("dueAt", due).put("dueTime", dueTime).put("totalScore", total.toDoubleOrNull() ?: 20.0).put("allowLate", allowLate).put("publicationState", if (publish) "published" else "draft").put("recipientMode", if (selectedOnly) "selected" else "all").put("studentIds", JSONArray(selected.toList())).put("academicPeriod", "semester2").put("attachments", files)); onSaved() } catch (e: Throwable) { error = e.message ?: "ثبت تکلیف ناموفق بود." } finally { busy = false } } }, colors = ButtonDefaults.buttonColors(containerColor = DWBrand)) { Text(if (busy) "در حال ثبت…" else "ذخیره تکلیف") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

// ---------- 5. Professional grading via existing /api/state + /api/staff/exam-grading/p6722 ----------
private fun buildGradingQueue(db: JSONObject): List<JSONObject> {
    val exams = db.optJSONArray("exams").dwObjects().associateBy { it.optString("id") }; val users = db.optJSONArray("users").dwObjects().associateBy { it.optString("id") }; val grades = db.optJSONArray("grades").dwObjects().associateBy { it.optString("submissionId") }
    return db.optJSONArray("submissions").dwObjects().mapNotNull { sub ->
        val exam = exams[sub.optString("examId")] ?: return@mapNotNull null; if (exam.optString("type") !in setOf("descriptive", "mixed")) return@mapNotNull null; val student = users[sub.optString("studentId")]; val grade = grades[sub.optString("id")]; val total = exam.optJSONArray("questions").dwObjects().sumOf { it.optDouble("score", 0.0) }
        JSONObject().put("submissionId", sub.optString("id")).put("studentId", sub.optString("studentId")).put("studentName", student?.displayName() ?: "دانش‌آموز").put("examTitle", exam.optString("title", "آزمون")).put("subject", exam.optString("subject")).put("submittedAt", sub.optString("at", sub.optString("submittedAt"))).put("status", if (grade != null || sub.optString("status") == "final") "final" else "pending").put("finalScore", grade?.optDouble("finalScore")).put("totalScore", total)
    }.sortedByDescending { it.optString("submittedAt") }
}

@Composable
fun ProfessionalGradingScreen(workspace: JSONObject?) {
    val scope = rememberCoroutineScope(); var queue by remember { mutableStateOf<List<JSONObject>>(emptyList()) }; var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf("") }; var selected by remember { mutableStateOf<String?>(null) }; var filter by remember { mutableStateOf("pending") }
    suspend fun reload() { loading = true; error = ""; try { queue = buildGradingQueue(ApiClient.getState().db) } catch (e: Throwable) { error = e.message ?: "صف تصحیح دریافت نشد." } finally { loading = false } }
    LaunchedEffect(Unit) { reload() }; selected?.let { id -> GradingDetailView(id, onBack = { selected = null }, onSaved = { selected = null; scope.launch { reload() } }); return }
    Column(Modifier.fillMaxSize().background(DWBg)) {
        DWHeader("تصحیح آزمون", "پاسخ سؤال‌به‌سؤال، بارم و بازخورد") { IconButton(onClick = { scope.launch { reload() } }) { Icon(Icons.Default.Refresh, null, tint = DWBrand) } }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = filter == "pending", onClick = { filter = "pending" }, label = { Text("در انتظار (${queue.count { it.optString("status") == "pending" }})") }); FilterChip(selected = filter == "final", onClick = { filter = "final" }, label = { Text("تصحیح‌شده (${queue.count { it.optString("status") == "final" }})") }) }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DWBrand); ErrorLine(error); val rows = queue.filter { it.optString("status") == filter }; if (!loading && rows.isEmpty()) EmptyLine(if (filter == "pending") "برگه‌ای در انتظار تصحیح نیست." else "برگه تصحیح‌شده‌ای وجود ندارد.")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { items(rows, key = { it.optString("submissionId") }) { r -> DWCard(Modifier.clickable { selected = r.optString("submissionId") }) {
            Row(verticalAlignment = Alignment.CenterVertically) { RemoteStudentAvatar(r.optString("studentId"), r.optString("studentName"), 44); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(r.optString("studentName"), fontWeight = FontWeight.Black, color = DWText); Text("${r.optString("examTitle")} · ${r.optString("subject")}", color = DWMuted, fontSize = 11.sp); Text(r.optString("submittedAt"), color = DWMuted, fontSize = 9.sp) }; if (filter == "final") Text("${fmt(r.optNullableDouble("finalScore"))}/${fmt(r.optNullableDouble("totalScore"))}", color = DWGood, fontWeight = FontWeight.Black) else Icon(Icons.Default.ChevronLeft, null, tint = DWMuted) }
        } } }
    }
}

private fun answerText(value: Any?, question: JSONObject): String {
    if (value == null || value == JSONObject.NULL) return "—"
    if (value is JSONArray) return (0 until value.length()).joinToString("، ") { value.optString(it) }
    if (value is Number && question.optJSONArray("choices") != null) return question.optJSONArray("choices")?.optString(value.toInt(), value.toString()) ?: value.toString()
    return value.toString().ifBlank { "—" }
}

private fun buildGradingDetail(db: JSONObject, submissionId: String): JSONObject? {
    val sub = db.optJSONArray("submissions").dwObjects().firstOrNull { it.optString("id") == submissionId } ?: return null; val exam = db.optJSONArray("exams").dwObjects().firstOrNull { it.optString("id") == sub.optString("examId") } ?: return null; val student = db.optJSONArray("users").dwObjects().firstOrNull { it.optString("id") == sub.optString("studentId") } ?: JSONObject(); val grade = db.optJSONArray("grades").dwObjects().firstOrNull { it.optString("submissionId") == submissionId }; val typed = sub.optJSONObject("typedAnswers") ?: JSONObject(); val autoRows = sub.optJSONObject("autoGrading")?.optJSONArray("questions").dwObjects().associateBy { it.optString("questionId") }; val questions = JSONArray()
    exam.optJSONArray("questions").dwObjects().forEachIndexed { index, q -> val id = q.optString("id"); val auto = autoRows[id]; val ref = q.optString("referenceAnswer").ifBlank { q.optString("answerText").ifBlank { val ans = q.opt("answer"); if (ans is Number) q.optJSONArray("choices")?.optString(ans.toInt()).orEmpty() else ans?.toString().orEmpty() } }; questions.put(JSONObject().put("id", id).put("number", index + 1).put("text", q.optString("text")).put("score", q.optDouble("score", 0.0)).put("studentAnswer", answerText(typed.opt(id), q)).put("referenceAnswer", ref).put("autoGraded", auto?.optBoolean("autoGraded", false) == true).put("autoScore", auto?.optDouble("earnedScore"))) }
    return JSONObject().put("student", student).put("exam", exam.copyJson().put("totalScore", exam.optJSONArray("questions").dwObjects().sumOf { it.optDouble("score", 0.0) })).put("submission", sub).put("questions", questions).put("grade", grade ?: JSONObject.NULL)
}

@Composable
private fun GradingDetailView(submissionId: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope(); var data by remember { mutableStateOf<JSONObject?>(null) }; var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf("") }; val scores = remember { mutableStateMapOf<String, String>() }; val comments = remember { mutableStateMapOf<String, String>() }; var overall by remember { mutableStateOf("") }; var editReason by remember { mutableStateOf("") }; var publishNow by remember { mutableStateOf(true) }; var busy by remember { mutableStateOf(false) }
    suspend fun load() { loading = true; error = ""; try { val d = buildGradingDetail(ApiClient.getState().db, submissionId) ?: throw IllegalStateException("برگه در اطلاعات معلم پیدا نشد."); data = d; scores.clear(); comments.clear(); val existing = d.optJSONObject("grade"); val existingRows = existing?.optJSONArray("questionScores").dwObjects().associateBy { it.optString("questionId") }; d.optJSONArray("questions").dwObjects().forEach { q -> val id = q.optString("id"); val er = existingRows[id]; scores[id] = fmt(er?.optNullableDouble("teacherScore") ?: q.optNullableDouble("autoScore") ?: 0.0); comments[id] = er?.optString("feedback").orEmpty() }; overall = existing?.optString("feedback").orEmpty() } catch (e: Throwable) { error = e.message ?: "برگه دریافت نشد." } finally { loading = false } }
    LaunchedEffect(submissionId) { load() }; val d = data; val finalized = d?.optJSONObject("grade") != null
    Column(Modifier.fillMaxSize().background(DWBg)) {
        DWHeader("تصحیح برگه", "ثبت نمره برای هر سؤال") { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = DWBrand) } }; if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DWBrand); ErrorLine(error)
        d?.let { json -> val st = json.optJSONObject("student") ?: JSONObject(); val exam = json.optJSONObject("exam") ?: JSONObject(); LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { DWCard { Text(st.displayName(), fontWeight = FontWeight.Black, fontSize = 18.sp, color = DWText); Text("${exam.optString("title")} · ${exam.optString("subject")} · بارم ${fmt(exam.optNullableDouble("totalScore"))}", color = DWMuted); val files = json.optJSONObject("submission")?.optJSONArray("files")?.length() ?: 0; if (files > 0) Text("$files فایل پاسخ ضمیمه شده", color = DWBrandDark, fontSize = 11.sp); if (finalized) Text("این برگه قبلاً نهایی شده است: ${fmt(json.optJSONObject("grade")?.optNullableDouble("finalScore"))}", color = DWGood, fontWeight = FontWeight.Bold) } }
            items(json.optJSONArray("questions").dwObjects(), key = { it.optString("id") }) { q -> val id = q.optString("id"); DWCard { Text("سؤال ${q.optInt("number")} · ${fmt(q.optNullableDouble("score"))} نمره", color = DWBrandDark, fontWeight = FontWeight.Black); Text(q.optString("text"), color = DWText); Surface(color = Color(0xFFF6FAFB), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) { Text("پاسخ دانش‌آموز", fontSize = 10.sp, color = DWMuted); Text(q.optString("studentAnswer", "—").ifBlank { "—" }, color = DWText) } }; if (q.optString("referenceAnswer").isNotBlank()) Text("پاسخ مرجع: ${q.optString("referenceAnswer")}", fontSize = 11.sp, color = DWMuted); if (q.optBoolean("autoGraded")) Text("نمره خودکار: ${fmt(q.optNullableDouble("autoScore"))}", fontSize = 11.sp, color = DWGood); OutlinedTextField(scores[id].orEmpty(), { scores[id] = it }, Modifier.fillMaxWidth(), label = { Text("نمره این سؤال / ${fmt(q.optNullableDouble("score"))}") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !finalized); OutlinedTextField(comments[id].orEmpty(), { comments[id] = it }, Modifier.fillMaxWidth(), label = { Text("بازخورد این سؤال") }, enabled = !finalized) } }
            if (!finalized) item { DWCard { OutlinedTextField(overall, { overall = it }, Modifier.fillMaxWidth(), label = { Text("بازخورد کلی") }, minLines = 2); val autoChanged = json.optJSONArray("questions").dwObjects().any { q -> q.optBoolean("autoGraded") && kotlin.math.abs((scores[q.optString("id")]?.toDoubleOrNull() ?: 0.0) - (q.optNullableDouble("autoScore") ?: 0.0)) > .001 }; if (autoChanged) OutlinedTextField(editReason, { editReason = it }, Modifier.fillMaxWidth(), label = { Text("دلیل تغییر نمره خودکار") }); Row(verticalAlignment = Alignment.CenterVertically) { Switch(publishNow, { publishNow = it }); Text("انتشار نتیجه برای دانش‌آموز") }; Button(enabled = !busy, onClick = { val questions = json.optJSONArray("questions").dwObjects(); val bad = questions.firstOrNull { q -> val x = scores[q.optString("id")]?.toDoubleOrNull(); x == null || x < 0 || x > (q.optNullableDouble("score") ?: 0.0) }; if (bad != null) { error = "نمره سؤال ${bad.optInt("number")} معتبر نیست."; return@Button }; val changed = questions.any { q -> q.optBoolean("autoGraded") && kotlin.math.abs((scores[q.optString("id")]?.toDoubleOrNull() ?: 0.0) - (q.optNullableDouble("autoScore") ?: 0.0)) > .001 }; if (changed && editReason.trim().length < 3) { error = "برای تغییر نمره خودکار، دلیل بنویسید."; return@Button }; scope.launch { busy = true; try { val arr = JSONArray(); questions.forEach { q -> val qid = q.optString("id"); arr.put(JSONObject().put("questionId", qid).put("teacherScore", scores[qid]?.toDoubleOrNull() ?: 0.0).put("feedback", comments[qid].orEmpty())) }; ApiClient.post("/api/staff/exam-grading/p6722", JSONObject().put("submissionId", submissionId).put("questionScores", arr).put("feedback", overall).put("teacherEditReason", editReason).put("publishNow", publishNow)); onSaved() } catch (e: Throwable) { error = e.message ?: "نهایی‌سازی تصحیح ناموفق بود." } finally { busy = false } } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = DWBrand)) { Icon(Icons.Default.DoneAll, null); Spacer(Modifier.width(6.dp)); Text(if (busy) "در حال ثبت…" else "نهایی کردن تصحیح") } } }
        } }
    }
}
