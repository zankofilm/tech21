package ir.madreseyar.teacher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import java.util.UUID

private val EBrand = Color(0xFF0CA6A4)
private val EBrandDark = Color(0xFF075A63)
private val EBg = Color(0xFFF3F9FB)
private val EText = Color(0xFF12313A)
private val EMuted = Color(0xFF71838A)
private val ESuccess = Color(0xFF16A36A)

private fun JSONArray.objects(): List<JSONObject> = buildList { for (i in 0 until length()) optJSONObject(i)?.let(::add) }
private fun JSONObject.prettySummary(): String {
    val preferred = listOf("title","name","subject","className","status","description","body","date","createdAt")
    return preferred.mapNotNull { k -> opt(k)?.takeIf { it != JSONObject.NULL && it.toString().isNotBlank() }?.let { "$k: $it" } }.take(4).joinToString(" · ")
}

@Composable private fun ExtHeader(title: String, subtitle: String = "") {
    Surface(color = EBrandDark) { Column(Modifier.fillMaxWidth().padding(18.dp)) { Text(title, color=Color.White, fontSize=22.sp, fontWeight=FontWeight.Black); if(subtitle.isNotBlank()) Text(subtitle,color=Color.White.copy(.78f),fontSize=12.sp) } }
}
@Composable private fun ExtCard(content: @Composable ColumnScope.() -> Unit) { Card(Modifier.fillMaxWidth().padding(vertical=5.dp), RoundedCornerShape(18.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(1.dp)) { Column(Modifier.padding(14.dp), content=content) } }
@Composable private fun LoadingOrError(loading:Boolean,error:String){ if(loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color=EBrand); if(error.isNotBlank()) Text(error,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error) }

@Composable
fun TeachingBooksScreen(workspace: JSONObject?) {
    val assignments=workspace?.optJSONArray("assignments")?.objects().orEmpty(); val classes=workspace?.optJSONArray("classes")?.objects().orEmpty(); val students=workspace?.optJSONArray("students")?.objects().orEmpty()
    val cn={id:String->classes.firstOrNull{it.optString("id")==id}?.optString("name",id)?:id}
    Column(Modifier.fillMaxSize().background(EBg)){ ExtHeader("کتاب‌ها و کلاس‌های من","تخصیص‌های واقعی تدریس از سرور"); LazyColumn(contentPadding=PaddingValues(16.dp)){ items(assignments){a-> val count=students.count{it.optString("classId")==a.optString("classId")}; ExtCard{Text(a.optString("subject","درس"),fontWeight=FontWeight.Black,fontSize=17.sp,color=EText);Text(cn(a.optString("classId")),color=EMuted);Text("$count دانش‌آموز · کتاب ${a.optString("bookId","—")}",color=EMuted,fontSize=12.sp); val fs=a.optJSONArray("features")?.let{arr->(0 until arr.length()).joinToString("، "){arr.optString(it)}}.orEmpty(); if(fs.isNotBlank()) Text("مجوزها: $fs",color=EBrand,fontSize=11.sp)} }; if(assignments.isEmpty()) item{Text("تخصیص تدریسی ثبت نشده است.",Modifier.padding(24.dp),color=EMuted)} } }
}

@Composable
fun CurriculumScreenNative() {
    var data by remember{ mutableStateOf<JSONObject?>(null)}; var loading by remember{mutableStateOf(true)}; var error by remember{mutableStateOf("")}
    LaunchedEffect(Unit){try{data=ApiClient.get("/api/staff/teacher-approved-questions/p67")}catch(e:Throwable){error=e.message?:"خطا"}finally{loading=false}}
    Column(Modifier.fillMaxSize().background(EBg)){ExtHeader("آموزش و فصل‌ها","کتاب‌ها و فصل‌های مجاز برای تدریس");LoadingOrError(loading,error);LazyColumn(contentPadding=PaddingValues(16.dp)){items(data?.optJSONArray("books")?.objects().orEmpty()){b->ExtCard{Text(b.optString("title",b.optString("subject","کتاب")),fontWeight=FontWeight.Black,color=EText);Text("پایه ${b.optString("grade","—")} · ${b.optString("subject")}",color=EMuted);val ch=b.optJSONArray("chapters")?.objects().orEmpty(); if(ch.isNotEmpty()){Spacer(Modifier.height(8.dp));ch.forEachIndexed{i,c->Text("${i+1}. ${c.optString("title",c.optString("name","فصل"))}",fontSize=12.sp,color=EBrandDark)}}}}}}
}

@Composable
fun QuestionBankNativeScreen() {
    val scope=rememberCoroutineScope(); var questions by remember{mutableStateOf<List<JSONObject>>(emptyList())};var assignments by remember{mutableStateOf<List<JSONObject>>(emptyList())};var loading by remember{mutableStateOf(true)};var error by remember{mutableStateOf("")};var adding by remember{mutableStateOf(false)}
    suspend fun reload(){loading=true;error="";try{questions=ApiClient.get("/api/p635/questions").optJSONArray("questions")?.objects().orEmpty();assignments=ApiClient.get("/api/p635/assignments").optJSONArray("assignments")?.objects().orEmpty()}catch(e:Throwable){error=e.message?:"خطا"}finally{loading=false}}
    LaunchedEffect(Unit){reload()}
    Column(Modifier.fillMaxSize().background(EBg)){ExtHeader("مخزن سؤال","بانک سؤال مستقل و مستقیم API");LoadingOrError(loading,error);Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={adding=true},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=EBrand)){Icon(Icons.Default.Add,null);Text("سؤال جدید")};OutlinedButton(onClick={scope.launch{reload()}},modifier=Modifier.weight(1f)){Icon(Icons.Default.Refresh,null);Text("تازه‌سازی")}};LazyColumn(contentPadding=PaddingValues(horizontal=16.dp,vertical=4.dp)){items(questions){q->ExtCard{Text(q.optString("text","سؤال"),fontWeight=FontWeight.Black);Text("${q.optString("subject")} · ${q.optString("chapter")}",color=EMuted,fontSize=12.sp);q.optJSONArray("options")?.let{a->for(i in 0 until a.length())Text("${if(i==q.optInt("correctIndex"))"✓" else "•"} ${a.optString(i)}",fontSize=12.sp,color=if(i==q.optInt("correctIndex")) ESuccess else EText)}}}}}
    if(adding) QuestionCreateDialog(assignments,onDismiss={adding=false},onSaved={adding=false;scope.launch{reload()}})
}

@Composable private fun QuestionCreateDialog(assignments:List<JSONObject>,onDismiss:()->Unit,onSaved:()->Unit){val scope=rememberCoroutineScope();var idx by remember{mutableIntStateOf(0)};var chapter by remember{mutableStateOf("")};var text by remember{mutableStateOf("")};val opts=remember{mutableStateListOf("","","","")};var correct by remember{mutableIntStateOf(0)};var error by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("سؤال چهارگزینه‌ای جدید")},text={LazyColumn{item{if(assignments.isEmpty())Text("ابتدا باید تخصیص تدریس داشته باشید.",color=MaterialTheme.colorScheme.error) else {Text("کلاس/درس",fontWeight=FontWeight.Bold);assignments.forEachIndexed{i,a->FilterChip(selected=idx==i,onClick={idx=i},label={Text("${a.optString("className")} · ${a.optString("subject")}")})};OutlinedTextField(chapter,{chapter=it},Modifier.fillMaxWidth(),label={Text("فصل")});OutlinedTextField(text,{text=it},Modifier.fillMaxWidth(),label={Text("متن سؤال")});opts.indices.forEach{i->OutlinedTextField(opts[i],{opts[i]=it},Modifier.fillMaxWidth(),label={Text("گزینه ${i+1}")},trailingIcon={RadioButton(selected=correct==i,onClick={correct=i})})};if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)}}}},confirmButton={Button(enabled=!busy&&assignments.isNotEmpty(),onClick={val a=assignments.getOrNull(idx)?:return@Button;scope.launch{busy=true;try{ApiClient.post("/api/p635/questions",JSONObject().put("classId",a.optString("classId")).put("subject",a.optString("subject")).put("chapter",chapter).put("text",text).put("options",JSONArray(opts)).put("correctIndex",correct));onSaved()}catch(e:Throwable){error=e.message?:"خطا"}finally{busy=false}}}){Text("ثبت سؤال")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})}

@Composable
fun OfficialExamCreateScreen(workspace:JSONObject?) {
    val scope=rememberCoroutineScope();var books by remember{mutableStateOf<List<JSONObject>>(emptyList())};var error by remember{mutableStateOf("")};var message by remember{mutableStateOf("")};var title by remember{mutableStateOf("")};var question by remember{mutableStateOf("")};val choices=remember{mutableStateListOf("","","","")};var correct by remember{mutableIntStateOf(0)};var assignmentIndex by remember{mutableIntStateOf(0)};var bookIndex by remember{mutableIntStateOf(0)};var chapterIndex by remember{mutableIntStateOf(0)};var busy by remember{mutableStateOf(false)};val assignments=workspace?.optJSONArray("assignments")?.objects().orEmpty()
    LaunchedEffect(Unit){try{books=ApiClient.get("/api/staff/teacher-approved-questions/p67").optJSONArray("books")?.objects().orEmpty()}catch(e:Throwable){error=e.message?:"خطا"}}
    val a=assignments.getOrNull(assignmentIndex); val subject=a?.optString("subject").orEmpty(); val compatible=books.filter{it.optString("subject")==subject}; val b=compatible.getOrNull(bookIndex); val chapters=b?.optJSONArray("chapters")?.objects().orEmpty(); val ch=chapters.getOrNull(chapterIndex)
    Column(Modifier.fillMaxSize().background(EBg)){ExtHeader("ساخت آزمون","ثبت پیش‌نویس رسمی روی سرور");LazyColumn(contentPadding=PaddingValues(16.dp)){item{ExtCard{Text("کلاس و درس",fontWeight=FontWeight.Black);assignments.forEachIndexed{i,x->FilterChip(selected=i==assignmentIndex,onClick={assignmentIndex=i;bookIndex=0;chapterIndex=0},label={Text("${x.optString("subject")} · ${x.optString("classId")}")})};Spacer(Modifier.height(8.dp));Text("کتاب",fontWeight=FontWeight.Bold);compatible.forEachIndexed{i,x->FilterChip(selected=i==bookIndex,onClick={bookIndex=i;chapterIndex=0},label={Text(x.optString("title",x.optString("subject")))})};Text("فصل",fontWeight=FontWeight.Bold);chapters.forEachIndexed{i,x->FilterChip(selected=i==chapterIndex,onClick={chapterIndex=i},label={Text(x.optString("title",x.optString("name","فصل")))})};OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text("عنوان آزمون")});OutlinedTextField(question,{question=it},Modifier.fillMaxWidth(),label={Text("متن سؤال")});choices.indices.forEach{i->OutlinedTextField(choices[i],{choices[i]=it},Modifier.fillMaxWidth(),label={Text("گزینه ${i+1}")},trailingIcon={RadioButton(selected=correct==i,onClick={correct=i})})};if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error);if(message.isNotBlank())Text(message,color=ESuccess);Button(enabled=!busy&&a!=null&&b!=null&&ch!=null&&title.length>=3&&question.isNotBlank()&&choices.all{it.isNotBlank()},onClick={scope.launch{busy=true;error="";message="";try{val id="ex_android_"+UUID.randomUUID().toString().replace("-","").take(14);val qid="q_android_"+UUID.randomUUID().toString().replace("-","").take(14);val exam=JSONObject().put("id",id).put("title",title).put("classId",a!!.optString("classId")).put("bookId",b!!.optString("id")).put("subject",subject).put("duration",20).put("examCategory","ongoing").put("academicPeriod","semester1").put("selectedChapterIds",JSONArray().put(ch!!.optString("id"))).put("status","draft").put("questions",JSONArray().put(JSONObject().put("id",qid).put("type","mcq").put("text",question).put("choices",JSONArray(choices)).put("answer",correct).put("score",20).put("chapterId",ch!!.optString("id"))));ApiClient.post("/api/staff/teacher-exams/p673",JSONObject().put("exam",exam));message="آزمون به‌صورت پیش‌نویس ثبت شد ✓";title="";question="";for(i in choices.indices)choices[i]=""}catch(e:Throwable){error=e.message?:"خطا"}finally{busy=false}}},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=EBrand)){Text(if(busy)"در حال ثبت…" else "ذخیره پیش‌نویس")}}}}}
}

@Composable
fun GradingCenterNativeScreen(workspace: JSONObject?) {
    val exams = workspace?.optJSONArray("exams")?.objects().orEmpty()
    val submissions = workspace?.optJSONArray("submissions")?.objects().orEmpty()
    val students = workspace?.optJSONArray("students")?.objects().orEmpty()
    val scope = rememberCoroutineScope()
    var statusById by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    Column(Modifier.fillMaxSize().background(EBg)) {
        ExtHeader("تصحیح و نمرات", "برگه‌های تحویلی آزمون‌ها")
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(submissions) { submission ->
                val exam = exams.firstOrNull { it.optString("id") == submission.optString("examId") }
                val student = students.firstOrNull { it.optString("id") == submission.optString("studentId") }
                val submissionId = submission.optString("id")
                val status = statusById[submissionId].orEmpty()

                ExtCard {
                    Text(exam?.optString("title", "آزمون") ?: "آزمون", fontWeight = FontWeight.Black)
                    Text(
                        "${student?.optString("firstName").orEmpty()} ${student?.optString("lastName").orEmpty()}",
                        color = EMuted
                    )
                    Text(submission.optString("submittedAt"), fontSize = 11.sp, color = EMuted)
                    OutlinedButton(onClick = {
                        scope.launch {
                            val next = try {
                                val encoded = URLEncoder.encode(submissionId, "UTF-8")
                                val detail = ApiClient.get("/api/staff/exam-grading/p6722?submissionId=$encoded")
                                if (detail.optBoolean("finalized")) "نهایی شده" else "آماده تصحیح"
                            } catch (e: Throwable) {
                                e.message ?: "خطا"
                            }
                            statusById = statusById + (submissionId to next)
                        }
                    }) {
                        Text("بررسی وضعیت برگه")
                    }
                    if (status.isNotBlank()) {
                        Text(status, color = if (status.contains("نهایی")) ESuccess else EBrand)
                    }
                }
            }
            if (submissions.isEmpty()) {
                item {
                    Text(
                        "برگه تشریحی تحویل‌شده‌ای وجود ندارد.",
                        Modifier.padding(24.dp),
                        color = EMuted
                    )
                }
            }
        }
    }
}

@Composable
fun ClassroomNativeScreen(workspace:JSONObject?) { val classes=workspace?.optJSONArray("classes")?.objects().orEmpty();val students=workspace?.optJSONArray("students")?.objects().orEmpty();Column(Modifier.fillMaxSize().background(EBg)){ExtHeader("کلاس","فهرست کلاس‌ها و دانش‌آموزان");LazyColumn(contentPadding=PaddingValues(16.dp)){items(classes){c->val ss=students.filter{it.optString("classId")==c.optString("id")};ExtCard{Text(c.optString("name","کلاس"),fontWeight=FontWeight.Black,fontSize=17.sp);Text("${ss.size} دانش‌آموز",color=EBrand);ss.take(12).forEach{Text("• ${it.optString("firstName")} ${it.optString("lastName")}",fontSize=12.sp,color=EText)};if(ss.size>12)Text("و ${ss.size-12} نفر دیگر…",color=EMuted,fontSize=11.sp)}}}}}

@Composable
private fun AssignedClassDropdown(
    workspace: JSONObject?,
    selectedClassId: String,
    onSelected: (String) -> Unit
) {
    val assignments = workspace?.optJSONArray("assignments")?.objects().orEmpty()
    val allClasses = workspace?.optJSONArray("classes")?.objects().orEmpty()
    val assignedIds = assignments.map { it.optString("classId") }.filter { it.isNotBlank() }.toSet()
    val classes = allClasses.filter { it.optString("id") in assignedIds }
    val selected = classes.firstOrNull { it.optString("id") == selectedClassId } ?: classes.firstOrNull()
    var expanded by remember { mutableStateOf(false) }

    fun classLabel(c: JSONObject): String {
        val name = c.optString("name", "کلاس")
        val grade = c.optString("grade").trim()
        return if (grade.isNotBlank() && !name.contains("پایه")) "$name · پایه $grade" else name
    }

    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.School, null)
            Spacer(Modifier.width(8.dp))
            Text(selected?.let(::classLabel) ?: "کلاس و پایه را انتخاب کنید", Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(.86f)
        ) {
            classes.forEach { c ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(classLabel(c), fontWeight = FontWeight.Bold)
                            val classAssignments = assignments.filter { it.optString("classId") == c.optString("id") }
                            val subjects = classAssignments.map { it.optString("subject") }.filter { it.isNotBlank() }.distinct()
                            if (subjects.isNotEmpty()) Text(subjects.joinToString("، "), fontSize = 11.sp, color = EMuted)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelected(c.optString("id"))
                    }
                )
            }
        }
    }
}

private fun assignedClassIds(workspace: JSONObject?): List<String> =
    workspace?.optJSONArray("assignments")?.objects().orEmpty()
        .map { it.optString("classId") }
        .filter { it.isNotBlank() }
        .distinct()

private fun classMeta(workspace: JSONObject?, classId: String): JSONObject? =
    workspace?.optJSONArray("classes")?.objects().orEmpty().firstOrNull { it.optString("id") == classId }

private fun classDisplay(workspace: JSONObject?, classId: String): String {
    val c = classMeta(workspace, classId) ?: return "کلاس"
    val name = c.optString("name", "کلاس")
    val grade = c.optString("grade").trim()
    return if (grade.isNotBlank() && !name.contains("پایه")) "$name · پایه $grade" else name
}

@Composable
private fun StudentModuleHeader(title: String, subtitle: String, onBack: (() -> Unit)? = null) {
    Surface(color = EBrandDark) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                if (subtitle.isNotBlank()) Text(subtitle, color = Color.White.copy(.78f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun ContinuousReportsNativeScreen(workspace: JSONObject?) {
    val allStudents = workspace?.optJSONArray("students")?.objects().orEmpty()
    val assignedIds = assignedClassIds(workspace)
    var selectedClassId by remember(workspace) { mutableStateOf(assignedIds.firstOrNull().orEmpty()) }
    var selectedStudent by remember { mutableStateOf<JSONObject?>(null) }
    var report by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(workspace, assignedIds.joinToString("|")) {
        if (selectedClassId !in assignedIds) selectedClassId = assignedIds.firstOrNull().orEmpty()
    }

    if (selectedStudent != null) {
        val student = selectedStudent!!
        Column(Modifier.fillMaxSize().background(EBg)) {
            StudentModuleHeader(
                "گزارش ${student.optString("firstName")} ${student.optString("lastName")}",
                classDisplay(workspace, student.optString("classId")),
                onBack = { selectedStudent = null; report = null; error = "" }
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = EBrand)
            if (error.isNotBlank()) Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                val subjects = report?.optJSONArray("subjects")?.objects().orEmpty()
                if (!loading && error.isBlank() && subjects.isEmpty()) {
                    item { ExtCard { Text("هنوز نتیجه منتشرشده‌ای برای این دانش‌آموز وجود ندارد.", color = EMuted) } }
                }
                items(subjects) { subject ->
                    ExtCard {
                        Text(subject.optString("subject", "درس"), fontWeight = FontWeight.Black, fontSize = 17.sp, color = EText)
                        Text("میانگین: ${subject.optString("average", "—")}", color = EBrand, fontWeight = FontWeight.Bold)
                        val exams = subject.optJSONArray("exams")?.objects().orEmpty()
                        Text("${exams.size} آزمون", color = EMuted, fontSize = 12.sp)
                        exams.take(6).forEach { exam ->
                            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color(0xFFE9EEF0))
                            Text(exam.optString("title", "آزمون"), fontWeight = FontWeight.SemiBold)
                            Text("نمره از ۲۰: ${exam.optString("score20", "—")}", color = EText, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        return
    }

    val students = allStudents.filter { it.optString("classId") == selectedClassId }
    Column(Modifier.fillMaxSize().background(EBg)) {
        ExtHeader("گزارش‌ها", "ابتدا کلاس و پایه را انتخاب کنید")
        Column(Modifier.padding(16.dp)) {
            AssignedClassDropdown(workspace, selectedClassId) { selectedClassId = it }
            Spacer(Modifier.height(8.dp))
            Text("${students.size} دانش‌آموز در ${classDisplay(workspace, selectedClassId)}", color = EMuted, fontSize = 12.sp)
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(students, key = { it.optString("id") }) { s ->
                ExtCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${s.optString("firstName")} ${s.optString("lastName")}", fontWeight = FontWeight.Bold, color = EText)
                            Text("${classDisplay(workspace, s.optString("classId"))} · کد ${s.optString("studentCode", "—")}", color = EMuted, fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                selectedStudent = s
                                report = null
                                loading = true
                                error = ""
                                scope.launch {
                                    try {
                                        report = ApiClient.get("/api/teacher/continuous-p11?studentId=${URLEncoder.encode(s.optString("id"), "UTF-8")}")
                                    } catch (e: Throwable) {
                                        error = e.message ?: "خطا در دریافت گزارش"
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EBrand)
                        ) { Text("گزارش") }
                    }
                }
            }
            if (students.isEmpty()) item { ExtCard { Text("برای این کلاس دانش‌آموزی پیدا نشد.", color = EMuted) } }
        }
    }
}

@Composable
fun HomeworkNativeScreen(workspace:JSONObject?) {val scope=rememberCoroutineScope();var data by remember{mutableStateOf<JSONObject?>(null)};var error by remember{mutableStateOf("")};var add by remember{mutableStateOf(false)};suspend fun reload(){try{data=ApiClient.get("/api/staff/learning-planner")}catch(e:Throwable){error=e.message?:"خطا"}};LaunchedEffect(Unit){reload()};Column(Modifier.fillMaxSize().background(EBg)){ExtHeader("مرکز تکالیف","تکلیف‌های منتشرشده و پیش‌نویس‌ها");Row(Modifier.padding(16.dp)){Button(onClick={add=true},colors=ButtonDefaults.buttonColors(containerColor=EBrand)){Icon(Icons.Default.Add,null);Text("تکلیف جدید")}};LazyColumn(contentPadding=PaddingValues(horizontal=16.dp)){items(data?.optJSONArray("homework")?.objects().orEmpty()){h->ExtCard{Text(h.optString("title","تکلیف"),fontWeight=FontWeight.Black);Text("${h.optString("subject")} · ${h.optString("className",h.optString("classId"))}",color=EMuted);Text("مهلت: ${h.optString("dueAtJalali",h.optString("dueAt","—"))}",fontSize=11.sp,color=EMuted)}}};if(add) HomeworkCreateDialog(workspace,onDismiss={add=false},onSaved={add=false;scope.launch{reload()}})}
}
@Composable private fun HomeworkCreateDialog(workspace:JSONObject?,onDismiss:()->Unit,onSaved:()->Unit){val assignments=workspace?.optJSONArray("assignments")?.objects().orEmpty();var idx by remember{mutableIntStateOf(0)};var title by remember{mutableStateOf("")};var desc by remember{mutableStateOf("")};var due by remember{mutableStateOf("")};var publish by remember{mutableStateOf(true)};var err by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope();AlertDialog(onDismissRequest=onDismiss,title={Text("تکلیف جدید")},text={LazyColumn{item{assignments.forEachIndexed{i,a->FilterChip(selected=i==idx,onClick={idx=i},label={Text(a.optString("subject"))})};OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text("عنوان")});OutlinedTextField(desc,{desc=it},Modifier.fillMaxWidth(),label={Text("توضیحات")},minLines=3);OutlinedTextField(due,{due=it},Modifier.fillMaxWidth(),label={Text("تاریخ میلادی مهلت (YYYY-MM-DD)")},placeholder={Text("2026-11-10")});Row(verticalAlignment=Alignment.CenterVertically){Switch(publish,{publish=it});Text("انتشار برای دانش‌آموزان")};if(err.isNotBlank())Text(err,color=MaterialTheme.colorScheme.error)}}},confirmButton={Button(enabled=!busy&&assignments.isNotEmpty(),onClick={val a=assignments.getOrNull(idx)?:return@Button;scope.launch{busy=true;try{ApiClient.post("/api/staff/homework/p652/save",JSONObject().put("classId",a.optString("classId")).put("subject",a.optString("subject")).put("title",title).put("description",desc).put("totalScore",20).put("publicationState",if(publish)"published" else "draft").put("recipientMode","all").put("studentIds",JSONArray()).put("dueAt",due).put("dueTime",if(due.isBlank())"" else "23:59").put("academicPeriod","semester2").put("attachments",JSONArray()));onSaved()}catch(e:Throwable){err=e.message?:"خطا"}finally{busy=false}}}){Text("ذخیره")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})}

@Composable
fun AttendanceDisciplineScreenNative(){ GenericModuleScreen("حضور و انضباط","/api/staff/attendance-discipline",listOf("students","recentBehavior"),"غیبت، تأخیر و پرونده‌های انضباطی") }
@Composable
fun ActionCenterScreenNative(){ GenericModuleScreen("مرکز اقدام","/api/staff/action-center",listOf("tasks","cases","signals"),"کارها، پرونده‌های پیگیری و هشدارها") }
@Composable
fun CommunicationsCenterScreenNative(){ GenericModuleScreen("ارتباطات و پیام‌ها","/api/staff/communications-center",listOf("inbox","announcements"),"صندوق پیام و اطلاعیه‌های مدرسه") }

@Composable
fun LearningPlannerScreenNative(workspace:JSONObject?){val scope=rememberCoroutineScope();var data by remember{mutableStateOf<JSONObject?>(null)};var add by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};suspend fun load(){try{data=ApiClient.get("/api/staff/learning-planner")}catch(e:Throwable){error=e.message?:"خطا"}};LaunchedEffect(Unit){load()};Column(Modifier.fillMaxSize().background(EBg)){ExtHeader("برنامه آموزشی","تقویم آموزشی، رویدادها و تعارض‌ها");Row(Modifier.padding(16.dp)){Button(onClick={add=true},colors=ButtonDefaults.buttonColors(containerColor=EBrand)){Icon(Icons.Default.Event,null);Text("رویداد جدید")}};LazyColumn(contentPadding=PaddingValues(horizontal=16.dp)){item{val s=data?.optJSONObject("summary");if(s!=null)ExtCard{Text("${s.optInt("scheduleEntries")} برنامه · ${s.optInt("homework")} تکلیف · ${s.optInt("exams")} آزمون",fontWeight=FontWeight.Black);Text("${s.optInt("events")} رویداد · ${s.optInt("conflicts")} تعارض",color=EMuted)}};items(data?.optJSONArray("upcoming")?.objects().orEmpty()){x->ExtCard{Text(x.optString("title",x.optString("subject","رویداد")),fontWeight=FontWeight.Black);Text(x.prettySummary(),color=EMuted,fontSize=11.sp)}};if(error.isNotBlank())item{Text(error,color=MaterialTheme.colorScheme.error)}};if(add)PlannerEventDialog(workspace,onDismiss={add=false},onSaved={add=false;scope.launch{load()}})}}
@Composable private fun PlannerEventDialog(workspace:JSONObject?,onDismiss:()->Unit,onSaved:()->Unit){val classes=workspace?.optJSONArray("classes")?.objects().orEmpty();var idx by remember{mutableIntStateOf(0)};var title by remember{mutableStateOf("")};var start by remember{mutableStateOf("")};var end by remember{mutableStateOf("")};var desc by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};val scope=rememberCoroutineScope();AlertDialog(onDismissRequest=onDismiss,title={Text("رویداد آموزشی")},text={Column{classes.forEachIndexed{i,c->FilterChip(selected=i==idx,onClick={idx=i},label={Text(c.optString("name"))})};OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text("عنوان")});OutlinedTextField(start,{start=it},Modifier.fillMaxWidth(),label={Text("شروع YYYY-MM-DD")});OutlinedTextField(end,{end=it},Modifier.fillMaxWidth(),label={Text("پایان YYYY-MM-DD")});OutlinedTextField(desc,{desc=it},Modifier.fillMaxWidth(),label={Text("توضیح")});if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)}},confirmButton={Button(onClick={scope.launch{try{ApiClient.post("/api/staff/learning-planner/event",JSONObject().put("classId",classes.getOrNull(idx)?.optString("id").orEmpty()).put("title",title).put("startDate",start).put("endDate",end).put("description",desc));onSaved()}catch(e:Throwable){error=e.message?:"خطا"}}}){Text("ثبت")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})}

@Composable
fun GuardianMeetingsNativeScreen(workspace:JSONObject?){val scope=rememberCoroutineScope();var data by remember{mutableStateOf<JSONObject?>(null)};var error by remember{mutableStateOf("")};var add by remember{mutableStateOf(false)};suspend fun load(){try{data=ApiClient.get("/api/staff/guardian-meeting-invitations")}catch(e:Throwable){error=e.message?:"خطا"}};LaunchedEffect(Unit){load()};Column(Modifier.fillMaxSize().background(EBg)){ExtHeader("ملاقات اولیا","دعوت‌ها و پاسخ خانواده‌ها");Row(Modifier.padding(16.dp)){Button(onClick={add=true},colors=ButtonDefaults.buttonColors(containerColor=EBrand)){Icon(Icons.Default.GroupAdd,null);Text("دعوت جدید")}};LazyColumn(contentPadding=PaddingValues(horizontal=16.dp)){items(data?.optJSONArray("meetings")?.objects().orEmpty()){m->ExtCard{Text(m.optString("studentName","دانش‌آموز"),fontWeight=FontWeight.Black);Text("${m.optString("meetingDate")} · ${m.optString("meetingTime")} · ${m.optString("location")}",color=EMuted);Text(m.optString("description"),fontSize=12.sp);Text(m.optString("status","pending_guardian"),color=EBrand,fontSize=11.sp)}};if(error.isNotBlank())item{Text(error,color=MaterialTheme.colorScheme.error)}};if(add)MeetingCreateDialog(workspace,onDismiss={add=false},onSaved={add=false;scope.launch{load()}})}}
@Composable private fun MeetingCreateDialog(workspace:JSONObject?,onDismiss:()->Unit,onSaved:()->Unit){val students=workspace?.optJSONArray("students")?.objects().orEmpty();var idx by remember{mutableIntStateOf(0)};var date by remember{mutableStateOf("")};var time by remember{mutableStateOf("")};var location by remember{mutableStateOf("")};var desc by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};val scope=rememberCoroutineScope();AlertDialog(onDismissRequest=onDismiss,title={Text("دعوت ملاقات")},text={LazyColumn{item{students.take(50).forEachIndexed{i,s->FilterChip(selected=i==idx,onClick={idx=i},label={Text("${s.optString("firstName")} ${s.optString("lastName")}")})};OutlinedTextField(date,{date=it},Modifier.fillMaxWidth(),label={Text("تاریخ شمسی YYYY/MM/DD")});OutlinedTextField(time,{time=it},Modifier.fillMaxWidth(),label={Text("ساعت HH:MM")});OutlinedTextField(location,{location=it},Modifier.fillMaxWidth(),label={Text("محل ملاقات")});OutlinedTextField(desc,{desc=it},Modifier.fillMaxWidth(),label={Text("علت/توضیح")});if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)}}},confirmButton={Button(onClick={val s=students.getOrNull(idx)?:return@Button;scope.launch{try{ApiClient.post("/api/staff/guardian-meeting-invitations",JSONObject().put("studentId",s.optString("id")).put("meetingDate",date).put("meetingTime",time).put("location",location).put("description",desc).put("clientRequestId","android_"+UUID.randomUUID().toString().replace("-","").take(20)));onSaved()}catch(e:Throwable){error=e.message?:"خطا"}}}){Text("ارسال دعوت")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})}

@Composable
fun StudentAnalysisNativeScreen(workspace: JSONObject?) {
    val allStudents = workspace?.optJSONArray("students")?.objects().orEmpty()
    val assignedIds = assignedClassIds(workspace)
    var selectedClassId by remember(workspace) { mutableStateOf(assignedIds.firstOrNull().orEmpty()) }
    var selectedStudent by remember { mutableStateOf<JSONObject?>(null) }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(workspace, assignedIds.joinToString("|")) {
        if (selectedClassId !in assignedIds) selectedClassId = assignedIds.firstOrNull().orEmpty()
    }

    if (selectedStudent != null) {
        val student = selectedStudent!!
        val analysis = data?.optJSONObject("analysis") ?: data
        Column(Modifier.fillMaxSize().background(EBg)) {
            StudentModuleHeader(
                "تحلیل ${student.optString("firstName")} ${student.optString("lastName")}",
                classDisplay(workspace, student.optString("classId")),
                onBack = { selectedStudent = null; data = null; error = "" }
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = EBrand)
            if (error.isNotBlank()) Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                if (!loading && error.isBlank() && analysis == null) {
                    item { ExtCard { Text("داده تحلیلی برای این دانش‌آموز موجود نیست.", color = EMuted) } }
                }
                analysis?.let { a ->
                    item {
                        ExtCard {
                            Text("خلاصه آموزشی", fontWeight = FontWeight.Black, fontSize = 17.sp)
                            val current = a.optJSONObject("current")
                            val attendance = a.optJSONObject("attendance")
                            val trend = a.optJSONObject("trend")
                            val status = a.optJSONObject("status")
                            if (current != null) {
                                Text("میانگین عملکرد: ${current.optString("averagePercent", "—")}%", color = EBrand, fontWeight = FontWeight.Bold)
                                Text("تعداد آزمون: ${current.optInt("examCount", 0)}", color = EText)
                            }
                            if (attendance != null) {
                                Text("غیبت: ${attendance.optInt("absent", 0)} · تأخیر: ${attendance.optInt("late", 0)}", color = EText)
                            }
                            if (status != null) Text("وضعیت: ${status.optString("label", status.optString("key", "—"))}", color = EText)
                            if (trend != null) Text("روند: ${trend.optString("label", trend.optString("key", "—"))}", color = EMuted)
                        }
                    }
                    val subjects = a.optJSONArray("subjects")?.objects().orEmpty()
                    if (subjects.isNotEmpty()) {
                        item { Text("درس‌ها", fontWeight = FontWeight.Black, fontSize = 18.sp, color = EText, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(subjects) { subject ->
                            ExtCard {
                                Text(subject.optString("subject", subject.optString("name", "درس")), fontWeight = FontWeight.Bold)
                                subject.keys().forEach { key ->
                                    val v = subject.opt(key)
                                    if (key !in setOf("subject", "name") && (v is Number || v is String)) Text("$key: $v", color = EMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    val students = allStudents.filter { it.optString("classId") == selectedClassId }
    Column(Modifier.fillMaxSize().background(EBg)) {
        ExtHeader("تحلیل دانش‌آموز", "ابتدا کلاس و پایه را انتخاب کنید")
        Column(Modifier.padding(16.dp)) {
            AssignedClassDropdown(workspace, selectedClassId) { selectedClassId = it }
            Spacer(Modifier.height(8.dp))
            Text("${students.size} دانش‌آموز در ${classDisplay(workspace, selectedClassId)}", color = EMuted, fontSize = 12.sp)
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(students, key = { it.optString("id") }) { s ->
                ExtCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${s.optString("firstName")} ${s.optString("lastName")}", fontWeight = FontWeight.Bold, color = EText)
                            Text("${classDisplay(workspace, s.optString("classId"))} · کد ${s.optString("studentCode", "—")}", color = EMuted, fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                selectedStudent = s
                                data = null
                                loading = true
                                error = ""
                                scope.launch {
                                    try {
                                        data = ApiClient.get("/api/staff/student-analysis?studentId=${URLEncoder.encode(s.optString("id"), "UTF-8")}")
                                    } catch (e: Throwable) {
                                        error = e.message ?: "خطا در دریافت تحلیل"
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EBrand)
                        ) { Text("تحلیل") }
                    }
                }
            }
            if (students.isEmpty()) item { ExtCard { Text("برای این کلاس دانش‌آموزی پیدا نشد.", color = EMuted) } }
        }
    }
}

@Composable
fun OnlineClassScreenNative(dashboard:JSONObject?){val scope=rememberCoroutineScope();val rows=dashboard?.optJSONArray("weekSchedule")?.objects().orEmpty();var message by remember{mutableStateOf("")};Column(Modifier.fillMaxSize().background(EBg)){ExtHeader("کلاس آنلاین","شروع/پایان نشست برای زنگ‌های آنلاین");LazyColumn(contentPadding=PaddingValues(16.dp)){items(rows){r->ExtCard{Text("${r.optString("subject")} · ${r.optString("className")}",fontWeight=FontWeight.Black);Text("${r.optString("day")} ${r.optString("startTime")}–${r.optString("endTime")}",color=EMuted);if(listOf("online","hybrid").contains(r.optString("effectiveMode"))){Button(onClick={scope.launch{try{ApiClient.post("/api/teacher/online-class/session/start",JSONObject().put("scheduleEntryId",r.optString("id")));message="کلاس آنلاین شروع شد ✓"}catch(e:Throwable){message=e.message?:"خطا"}}},colors=ButtonDefaults.buttonColors(containerColor=EBrand)){Text("شروع کلاس آنلاین")}}else Text("حالت این زنگ حضوری است.",fontSize=11.sp,color=EMuted)}};if(message.isNotBlank())item{Text(message,Modifier.padding(12.dp),color=if(message.contains("✓")) ESuccess else MaterialTheme.colorScheme.error)}}}}

@Composable
private fun GenericModuleScreen(title:String,path:String,arrays:List<String>,subtitle:String){var data by remember{mutableStateOf<JSONObject?>(null)};var loading by remember{mutableStateOf(true)};var error by remember{mutableStateOf("")};LaunchedEffect(path){try{data=ApiClient.get(path)}catch(e:Throwable){error=e.message?:"خطا"}finally{loading=false}};Column(Modifier.fillMaxSize().background(EBg)){ExtHeader(title,subtitle);LoadingOrError(loading,error);LazyColumn(contentPadding=PaddingValues(16.dp)){item{data?.optJSONObject("summary")?.let{s->ExtCard{Text("خلاصه",fontWeight=FontWeight.Black);s.keys().forEach{k->Text("$k: ${s.opt(k)}",fontSize=12.sp,color=EText)}}}};arrays.forEach{key->items(data?.optJSONArray(key)?.objects().orEmpty()){r->ExtCard{Text(r.optString("title",r.optString("name",r.optString("studentName",key))),fontWeight=FontWeight.Black);val summary=r.prettySummary();if(summary.isNotBlank())Text(summary,color=EMuted,fontSize=11.sp)}}}}}}
