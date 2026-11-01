package ir.madreseyar.teacher

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

private val BrandDark = Color(0xFF075A63)
private val BrandDeep = Color(0xFF087782)
private val Brand = Color(0xFF0CA6A4)
private val BrandBright = Color(0xFF20C9BE)
private val Sky = Color(0xFF2BA7E8)
private val AppBackground = Color(0xFFF3F9FB)
private val TextPrimary = Color(0xFF12313A)
private val TextSecondary = Color(0xFF71838A)
private val Border = Color(0xFFDDEBED)
private val Success = Color(0xFF16A36A)
private val Warning = Color(0xFFF5A524)

class MainActivity : FragmentActivity() {
    private lateinit var tokenStore: SecureTokenStore
    var onRequireUnlock: (() -> Unit)? = null
    private var stoppedAt: Long = 0L
    private var skipNextUnlock: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        tokenStore = SecureTokenStore(this)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(
                    colorScheme = lightColorScheme(
                        primary = Brand,
                        onPrimary = Color.White,
                        secondary = Sky,
                        background = AppBackground,
                        surface = Color.White,
                        onSurface = TextPrimary,
                    )
                ) { TeacherDirectApiApp(this, tokenStore) }
            }
        }
    }

    override fun onStop() {
        stoppedAt = System.currentTimeMillis()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0L) {
            if (skipNextUnlock) {
                skipNextUnlock = false
            } else {
                onRequireUnlock?.invoke()
            }
        }
    }

    fun suppressNextUnlock() {
        skipNextUnlock = true
    }

    fun biometricAvailable(): Boolean {
        val result = BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticateBiometric(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onError(errString.toString())
                }
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ورود امن به مدرسه‌یار")
            .setSubtitle("اثر انگشت یا قفل دستگاه را تأیید کنید")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }
}

private enum class Screen { Unlock, Login, ChangePassword, Home, Classes, Messages, More, Attendance, Students, Grades, Exams, Schedule, Notifications, Practice, IndependentExamCenter, Library, Account, MyTeaching, Curriculum, QuestionBank, ExamCreate, Grading, Classroom, Reports, Homework, AttendanceDiscipline, LearningPlanner, Communications, GuardianMeetings, ActionCenter, StudentAnalysis, OnlineClass }

private data class MenuFeature(val title: String, val icon: ImageVector, val screen: Screen, val subtitle: String)

@Composable
private fun TeacherDirectApiApp(activity: MainActivity, tokenStore: SecureTokenStore) {
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(if (tokenStore.isBiometricEnabled()) Screen.Unlock else Screen.Login) }
    var offerBiometric by remember { mutableStateOf(false) }
    var unlockPrompted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var mfaRequired by remember { mutableStateOf(false) }
    var recoveryAllowed by remember { mutableStateOf(false) }
    var pendingUser by remember { mutableStateOf("") }
    var pendingPassword by remember { mutableStateOf("") }
    var loginIdentifier by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var dashboard by remember { mutableStateOf<JSONObject?>(null) }
    var workspace by remember { mutableStateOf<JSONObject?>(null) }
    var currentPasswordForChange by remember { mutableStateOf("") }

    suspend fun loadCore() = coroutineScope {
        val dashboardJob = async { runCatching { ApiClient.get("/api/teacher/dashboard") } }
        val workspaceJob = async { runCatching { ApiClient.get("/api/teacher/workspace") } }
        dashboardJob.await().getOrNull()?.let { dashboard = it }
        workspaceJob.await().getOrNull()?.let { workspace = it }
    }

    fun fail(e: Throwable) {
        error = e.message ?: "خطای نامشخص"
        if (e is ApiException && e.status == 401) {
            ApiClient.token = ""
            tokenStore.clear()
            screen = Screen.Login
        }
    }

    fun finishLogin(data: JSONObject, enteredPassword: String) {
        val user = data.optJSONObject("user")
        if (user?.optString("role") != "teacher") {
            error = "این اپ فقط برای حساب معلم قابل استفاده است."
            return
        }
        val token = data.optString("token")
        if (token.isBlank()) {
            error = "سرور توکن ورود برنگرداند."
            return
        }
        ApiClient.token = token
        if (tokenStore.isBiometricEnabled()) tokenStore.save(token) else if (activity.biometricAvailable()) offerBiometric = true
        currentPasswordForChange = enteredPassword
        mfaRequired = false
        recoveryAllowed = false
        pendingUser = ""
        pendingPassword = ""
        loginPassword = ""
        if (data.optBoolean("mustChangePassword", false)) {
            screen = Screen.ChangePassword
        } else {
            // ورود باید فوری تمام شود؛ داده‌های داشبورد در پس‌زمینه و موازی بارگذاری می‌شوند.
            screen = Screen.Home
            scope.launch { loadCore() }
        }
    }

    fun login(identifier: String, password: String) {
        scope.launch {
            busy = true; error = ""
            try {
                val body = JSONObject().put("identifier", identifier).put("password", password)
                val data = ApiClient.post("/api/staff/login", body, auth = false)
                finishLogin(data, password)
            } catch (e: ApiException) {
                if (e.status == 428 || e.code == "MFA_REQUIRED" || e.payload?.optBoolean("mfaRequired") == true) {
                    mfaRequired = true
                    recoveryAllowed = e.payload?.optBoolean("recoveryAllowed", false) == true
                    pendingUser = identifier
                    pendingPassword = password
                    error = ""
                } else fail(e)
            } catch (e: Throwable) { fail(e) } finally { busy = false }
        }
    }

    fun verifyMfa(code: String, useRecovery: Boolean) {
        val identifier = pendingUser
        val password = pendingPassword
        if (identifier.isBlank() || password.isBlank()) {
            mfaRequired = false
            recoveryAllowed = false
            error = "نشست ورود منقضی شده است؛ دوباره وارد شوید."
            return
        }
        scope.launch {
            busy = true; error = ""
            try {
                val body = JSONObject().put("identifier", identifier).put("password", password)
                if (useRecovery) body.put("recoveryCode", code.trim()) else body.put("otp", code.filter(Char::isDigit))
                val data = ApiClient.post("/api/staff/login", body, auth = false)
                finishLogin(data, password)
            } catch (e: ApiException) {
                if (e.status == 401 || e.code == "MFA_INVALID") error = if (useRecovery) "کد بازیابی صحیح نیست." else "کد تأیید صحیح نیست."
                else fail(e)
            } catch (e: Throwable) { fail(e) } finally { busy = false }
        }
    }

    fun cancelMfa() {
        mfaRequired = false
        recoveryAllowed = false
        pendingUser = ""
        pendingPassword = ""
        error = "ورود دومرحله‌ای لغو شد."
    }

    fun biometricLogin() {
        activity.authenticateBiometric(onSuccess = {
            scope.launch {
                error = ""
                try {
                    val token = tokenStore.load() ?: throw IllegalStateException("نشست ذخیره‌شده پیدا نشد.")
                    ApiClient.token = token
                    // قفل دستگاه تأیید شده است؛ UI بلافاصله باز می‌شود و اعتبار نشست در پس‌زمینه چک می‌شود.
                    screen = Screen.Home
                    busy = false
                    scope.launch {
                        try {
                            val me = ApiClient.get("/api/staff/me")
                            if (me.optJSONObject("user")?.optString("role") != "teacher") throw IllegalStateException("نشست معلم معتبر نیست.")
                            loadCore()
                        } catch (e: Throwable) {
                            tokenStore.clear(); ApiClient.token = ""; screen = Screen.Login; fail(e)
                        }
                    }
                } catch (e: Throwable) { tokenStore.clear(); screen = Screen.Login; fail(e) }
            }
        }, onError = { error = it })
    }

    DisposableEffect(activity) {
        activity.onRequireUnlock = {
            if (tokenStore.isBiometricEnabled() && screen !in listOf(Screen.Login, Screen.ChangePassword, Screen.Unlock)) {
                screen = Screen.Unlock
                unlockPrompted = false
            }
        }
        onDispose { activity.onRequireUnlock = null }
    }

    LaunchedEffect(screen) {
        if (screen == Screen.Unlock && !unlockPrompted) {
            unlockPrompted = true
            biometricLogin()
        } else if (screen != Screen.Unlock) unlockPrompted = false
    }

    fun refreshCore() {
        scope.launch { busy = true; error = ""; try { loadCore() } catch (e: Throwable) { fail(e) } finally { busy = false } }
    }

    fun logout() {
        scope.launch {
            try { if (ApiClient.token.isNotBlank()) ApiClient.post("/api/staff/logout") } catch (_: Throwable) {}
            ApiClient.token = ""; tokenStore.clear(); dashboard = null; workspace = null; screen = Screen.Login
        }
    }

    AnimatedContent(targetState = screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { target ->
        when (target) {
            Screen.Unlock -> UnlockScreen(busy = busy, error = error, onUnlock = { unlockPrompted = true; biometricLogin() }, onUsePassword = { screen = Screen.Login })
            Screen.Login -> {
                LoginScreen(
                    username = loginIdentifier,
                    password = loginPassword,
                    onUsernameChange = { loginIdentifier = it },
                    onPasswordChange = { loginPassword = it },
                    hasBiometric = tokenStore.isBiometricEnabled() && activity.biometricAvailable(),
                    busy = busy,
                    error = if (mfaRequired) "" else error,
                    onLogin = { u, p -> login(u, p) },
                    onBiometric = { biometricLogin() }
                )
                if (mfaRequired) {
                    MfaDialog(
                        busy = busy,
                        error = error,
                        recoveryAllowed = recoveryAllowed,
                        onVerify = { code, useRecovery -> verifyMfa(code, useRecovery) },
                        onCancel = { cancelMfa() }
                    )
                }
            }
            Screen.ChangePassword -> ChangePasswordScreen(busy, error) { newPassword ->
                scope.launch {
                    busy = true; error = ""
                    try {
                        val changed = ApiClient.post("/api/staff/change-password", JSONObject().put("currentPassword", currentPasswordForChange).put("newPassword", newPassword))
                        changed.optString("token").takeIf { it.isNotBlank() }?.let { ApiClient.token = it; if (tokenStore.isBiometricEnabled()) tokenStore.save(it) }
                        screen = Screen.Home
                        busy = false
                        scope.launch { loadCore() }
                    } catch (e: Throwable) { fail(e) } finally { busy = false }
                }
            }
            else -> NativeShell(
                screen = target,
                dashboard = dashboard,
                workspace = workspace,
                busy = busy,
                error = error,
                onNavigate = { screen = it },
                onRefresh = { refreshCore() },
                onLogout = { logout() }
            )
        }
    }
    if (offerBiometric && screen == Screen.Home && ApiClient.token.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { offerBiometric = false },
            icon = { Icon(Icons.Default.Fingerprint, null, tint = Brand) },
            title = { Text("فعال‌سازی قفل اثر انگشت", fontWeight = FontWeight.Black) },
            text = { Text("فقط یک‌بار فعالش کنید. از این به بعد هنگام باز شدن مدرسه‌یار، اثر انگشت یا قفل امن دستگاه درخواست می‌شود و داخل بخش‌های برنامه تکرار نمی‌شود.") },
            confirmButton = { Button(onClick = { activity.authenticateBiometric(onSuccess = { tokenStore.enableBiometric(ApiClient.token); offerBiometric = false }, onError = { error = it }) }, colors = ButtonDefaults.buttonColors(containerColor = Brand)) { Text("فعال کن") } },
            dismissButton = { TextButton(onClick = { offerBiometric = false }) { Text("فعلاً نه") } }
        )
    }
}

@Composable
private fun UnlockScreen(busy: Boolean, error: String, onUnlock: () -> Unit, onUsePassword: () -> Unit) {
    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(Modifier.size(96.dp), RoundedCornerShape(28.dp), Color.White, shadowElevation = 7.dp) { Image(painterResource(R.mipmap.ic_launcher), null, Modifier.padding(12.dp)) }
        Spacer(Modifier.height(22.dp)); Text("مدرسه‌یار قفل است", fontSize = 25.sp, fontWeight = FontWeight.Black, color = BrandDark); Spacer(Modifier.height(6.dp)); Text("برای ورود اثر انگشت یا قفل امن دستگاه را تأیید کنید.", color = TextSecondary, textAlign = TextAlign.Center)
        if (error.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(error, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(22.dp)); Button(onClick=onUnlock, enabled=!busy, modifier=Modifier.fillMaxWidth().height(54.dp), colors=ButtonDefaults.buttonColors(containerColor=Brand), shape=RoundedCornerShape(16.dp)) { Icon(Icons.Default.Fingerprint,null); Spacer(Modifier.width(8.dp)); Text("باز کردن با اثر انگشت", fontWeight=FontWeight.Bold) }
        TextButton(onClick=onUsePassword) { Text("ورود با نام کاربری و رمز عبور") }
    }
}

@Composable
private fun LoginScreen(
    username: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    hasBiometric: Boolean,
    busy: Boolean,
    error: String,
    onLogin: (String, String) -> Unit,
    onBiometric: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Box(
                Modifier.fillMaxWidth().height(235.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFFE8FAFC), Color(0xFFF6FCFD))))
            ) {
                Surface(
                    Modifier.size(96.dp).align(Alignment.Center),
                    RoundedCornerShape(28.dp),
                    Color.White,
                    shadowElevation = 8.dp
                ) { Image(painterResource(R.mipmap.ic_launcher), null, Modifier.padding(12.dp)) }
                Surface(
                    Modifier.align(Alignment.BottomCenter).offset(y = 20.dp),
                    RoundedCornerShape(20.dp), Color.White, shadowElevation = 5.dp
                ) {
                    Text("مدرسه‌یار معلم", Modifier.padding(horizontal = 24.dp, vertical = 10.dp), color = BrandDark, fontWeight = FontWeight.Black, fontSize = 22.sp)
                }
            }
            Spacer(Modifier.height(42.dp))
            Text("ورود به حساب کاربری", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 25.sp)
            Text("مدیریت کلاس، نمره، آزمون و ارتباط آموزشی", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(22.dp))
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                RoundedCornerShape(26.dp),
                CardDefaults.cardColors(Color.White),
                CardDefaults.cardElevation(3.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    OutlinedTextField(
                        username, onUsernameChange, Modifier.fillMaxWidth(),
                        label = { Text("نام کاربری") },
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = Brand) },
                        singleLine = true, shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand, focusedLabelColor = Brand)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        password, onPasswordChange, Modifier.fillMaxWidth(),
                        label = { Text("رمز عبور") },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = Brand) },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand, focusedLabelColor = Brand)
                    )
                    if (error.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { if (username.isNotBlank() && password.isNotBlank()) onLogin(username.trim(), password) },
                        enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand)
                    ) { if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White) else Text("ورود", fontWeight = FontWeight.Black, fontSize = 16.sp) }
                    if (hasBiometric) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onBiometric, enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Brand)
                        ) { Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("ورود با اثر انگشت", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("مدرسه‌یار • نسخه بومی Android", color = TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun MfaDialog(
    busy: Boolean,
    error: String,
    recoveryAllowed: Boolean,
    onVerify: (String, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var otp by remember { mutableStateOf("") }
    var recoveryCode by remember { mutableStateOf("") }
    var useRecovery by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        icon = { Icon(Icons.Default.Security, null, tint = Brand) },
        title = { Text("تأیید دومرحله‌ای", fontWeight = FontWeight.Black, color = BrandDark) },
        text = {
            Column {
                Text(
                    if (useRecovery) "کد بازیابی را وارد کنید." else "کد ۶ رقمی برنامه Authenticator را وارد کنید.",
                    color = TextSecondary
                )
                Spacer(Modifier.height(14.dp))
                if (!useRecovery) {
                    OutlinedTextField(
                        value = otp,
                        onValueChange = { otp = it.filter(Char::isDigit).take(8) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("کد Authenticator") },
                        placeholder = { Text("000000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = recoveryCode,
                        onValueChange = { recoveryCode = it.take(64) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("کد بازیابی") },
                        placeholder = { Text("ABCD-EFGH") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
                if (recoveryAllowed) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = { useRecovery = !useRecovery },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (useRecovery) "استفاده از کد Authenticator" else "استفاده از کد بازیابی")
                    }
                }
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !busy) { Text("انصراف") }
        },
        confirmButton = {
            Button(
                onClick = { onVerify(if (useRecovery) recoveryCode.trim() else otp.trim(), useRecovery) },
                enabled = !busy && (if (useRecovery) recoveryCode.isNotBlank() else otp.isNotBlank()),
                colors = ButtonDefaults.buttonColors(containerColor = Brand)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("تأیید و ورود")
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

@Composable
private fun ChangePasswordScreen(busy: Boolean, error: String, onSubmit: (String) -> Unit) {
    var p1 by remember { mutableStateOf("") }; var p2 by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("تغییر رمز اجباری", fontSize = 28.sp, fontWeight = FontWeight.Black, color = BrandDark)
        Spacer(Modifier.height(8.dp)); Text("سرور درخواست کرده قبل از ادامه رمز عبور را تغییر دهید.", color = TextSecondary)
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(p1, { p1 = it }, Modifier.fillMaxWidth(), label = { Text("رمز جدید") }, visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(p2, { p2 = it }, Modifier.fillMaxWidth(), label = { Text("تکرار رمز جدید") }, visualTransformation = PasswordVisualTransformation())
        if (error.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(error, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(18.dp))
        Button(onClick = { if (p1.length >= 8 && p1 == p2) onSubmit(p1) }, enabled = !busy && p1.length >= 8 && p1 == p2, modifier = Modifier.fillMaxWidth()) { Text("ثبت رمز و ادامه") }
    }
}

@Composable
private fun NativeShell(
    screen: Screen,
    dashboard: JSONObject?,
    workspace: JSONObject?,
    busy: Boolean,
    error: String,
    onNavigate: (Screen) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    val rootTab = when (screen) {
        Screen.Home, Screen.Attendance, Screen.Notifications -> Screen.Home
        Screen.Classes, Screen.Students, Screen.Grades, Screen.Exams, Screen.Schedule, Screen.MyTeaching, Screen.Curriculum, Screen.Classroom, Screen.OnlineClass -> Screen.Classes
        Screen.Messages, Screen.Communications, Screen.GuardianMeetings -> Screen.Messages
        else -> Screen.More
    }
    Scaffold(
        containerColor = AppBackground,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 6.dp) {
                listOf(
                    Triple(Screen.Home, "خانه", Icons.Outlined.Home),
                    Triple(Screen.Classes, "کلاس‌ها", Icons.Outlined.Groups),
                    Triple(Screen.Messages, "پیام‌ها", Icons.Outlined.ChatBubbleOutline),
                    Triple(Screen.More, "بیشتر", Icons.Outlined.MoreHoriz),
                ).forEach { (s, label, icon) ->
                    NavigationBarItem(
                        selected = rootTab == s,
                        onClick = { onNavigate(s) },
                        icon = { Icon(icon, null) },
                        label = { Text(label, fontWeight = if (rootTab == s) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = BrandDark, selectedTextColor = BrandDark, indicatorColor = Brand.copy(alpha = .12f), unselectedIconColor = TextSecondary, unselectedTextColor = TextSecondary)
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (screen) {
                Screen.Home -> HomeScreen(dashboard, workspace, onNavigate, onRefresh)
                Screen.Classes -> ClassesScreen(workspace, onNavigate)
                Screen.Messages -> MessagesScreen()
                Screen.More -> MoreScreen(onNavigate, onLogout)
                Screen.Attendance -> AttendanceScreen(dashboard, onRefresh)
                Screen.Students -> StudentsProScreen(workspace)
                Screen.Grades -> ProfessionalGradebookScreen(workspace)
                Screen.Exams -> ExamsScreen(workspace, onNavigate)
                Screen.Schedule -> ScheduleScreen(dashboard)
                Screen.Notifications -> NotificationsScreen(dashboard)
                Screen.Practice -> PracticeTeacherScreen()
                Screen.IndependentExamCenter -> IndependentExamCenterScreen()
                Screen.Library -> LibraryTeacherScreen(workspace)
                Screen.Account -> AccountScreen(onLogout)
                Screen.MyTeaching -> TeachingBooksScreen(workspace)
                Screen.Curriculum -> CurriculumScreenNative()
                Screen.QuestionBank -> QuestionBankNativeScreen()
                Screen.ExamCreate -> ExamStudioScreen(workspace)
                Screen.Grading -> ProfessionalGradingScreen(workspace)
                Screen.Classroom -> ClassJournalScreen(workspace)
                Screen.Reports -> ContinuousReportsNativeScreen(workspace)
                Screen.Homework -> HomeworkCenterProScreen(workspace)
                Screen.AttendanceDiscipline -> AttendanceDisciplineInteractiveScreen()
                Screen.LearningPlanner -> LearningPlannerScreenNative(workspace)
                Screen.Communications -> CommunicationsInteractiveScreen()
                Screen.GuardianMeetings -> GuardianMeetingsNativeScreen(workspace)
                Screen.ActionCenter -> ActionCenterInteractiveScreen()
                Screen.StudentAnalysis -> StudentAnalysisNativeScreen(workspace)
                Screen.OnlineClass -> OnlineClassScreenNative(dashboard)
                else -> Unit
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            if (error.isNotBlank() && screen != Screen.Login) {
                Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp), color = Color(0xFFFFEEEE), shape = RoundedCornerShape(14.dp), shadowElevation = 4.dp) {
                    Text(error, Modifier.padding(14.dp), color = Color(0xFFA52929), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(dashboard: JSONObject?, workspace: JSONObject?, onNavigate: (Screen) -> Unit, onRefresh: () -> Unit) {
    val teacher = dashboard?.optJSONObject("teacher")
    val school = dashboard?.optJSONObject("school")
    val clock = dashboard?.optJSONObject("clock")
    val current = dashboard?.optJSONObject("currentClass")
    val notifications = dashboard?.optJSONArray("notifications") ?: JSONArray()
    val students = workspace?.optJSONArray("students") ?: JSONArray()
    val exams = workspace?.optJSONArray("exams") ?: JSONArray()
    val assignments = workspace?.optJSONArray("assignments") ?: JSONArray()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(BrandDeep, Brand)))
                    .statusBarsPadding().padding(20.dp)
            ) {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(48.dp), CircleShape, Color.White.copy(alpha = .18f)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("سلام ${teacher?.optString("firstName").orEmpty()} ${teacher?.optString("lastName").orEmpty()}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            Text(school?.optString("name", "مدرسه‌یار") ?: "مدرسه‌یار", color = Color.White.copy(alpha = .78f), fontSize = 12.sp)
                        }
                        BadgedBox(badge = { if (notifications.length() > 0) Badge { Text(notifications.length().toString()) } }) {
                            IconButton(onClick = { onNavigate(Screen.Notifications) }) { Icon(Icons.Default.NotificationsNone, null, tint = Color.White) }
                        }
                        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, null, tint = Color.White) }
                    }
                    Spacer(Modifier.height(14.dp))
                    Surface(color = Color.White.copy(alpha = .14f), shape = RoundedCornerShape(17.dp)) {
                        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("${clock?.optString("dayName").orEmpty()} ${clock?.optString("dateFa").orEmpty()}", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(clock?.optString("time", "--:--") ?: "--:--", color = Color.White)
                        }
                    }
                }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).offset(y = (-10).dp),
                RoundedCornerShape(24.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(3.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    HomeStat("دانش‌آموز", students.length().toString(), Icons.Default.Groups, Color(0xFF3AA8E8))
                    HomeStat("کلاس/درس", assignments.length().toString(), Icons.Default.School, Brand)
                    HomeStat("آزمون", exams.length().toString(), Icons.Default.AssignmentTurnedIn, Color(0xFFF1A33A))
                }
            }
        }
        if (current != null) item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                RoundedCornerShape(22.dp), CardDefaults.cardColors(Color(0xFFE9FAF5))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(44.dp), CircleShape, Color.White) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Class, null, tint = Success) } }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("کلاس در حال برگزاری", color = Success, fontWeight = FontWeight.Black)
                            Text("${current.optString("subject")} · ${current.optString("className")}", fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Text("${current.optString("startTime")}–${current.optString("endTime")}", color = TextSecondary, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onNavigate(Screen.Attendance) },
                        enabled = current.optBoolean("attendanceOpen", false),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Success)
                    ) { Icon(Icons.Default.FactCheck, null); Spacer(Modifier.width(7.dp)); Text("ثبت حضور و غیاب", fontWeight = FontWeight.Bold) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("دسترسی سریع", fontWeight = FontWeight.Black, fontSize = 19.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                Icon(Icons.Default.Bolt, null, tint = Warning)
            }
            val features = listOf(
                MenuFeature("حضور و غیاب", Icons.Default.FactCheck, Screen.Attendance, "ثبت روزانه"),
                MenuFeature("آزمون‌ها", Icons.Default.AssignmentTurnedIn, Screen.Exams, "مدیریت آزمون"),
                MenuFeature("کلاس‌ها", Icons.Default.Groups, Screen.Classes, "دانش‌آموزان"),
                MenuFeature("پیام‌ها", Icons.Default.ChatBubble, Screen.Messages, "ارتباط آموزشی"),
                MenuFeature("بانک تمرین", Icons.Default.Layers, Screen.Practice, "تمرین‌های آموزشی"),
                MenuFeature("کتابخانه", Icons.Default.MenuBook, Screen.Library, "منابع دیجیتال"),
            )
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxWidth().height(250.dp).padding(horizontal = 10.dp), userScrollEnabled = false) {
                gridItems(features) { f -> QuickFeature(f) { onNavigate(f.screen) } }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable { onNavigate(Screen.Library) },
                RoundedCornerShape(22.dp), CardDefaults.cardColors(Color(0xFFDDF7FB))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(58.dp), RoundedCornerShape(18.dp), Color.White) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.AutoStories, null, tint = Brand, modifier = Modifier.size(32.dp)) } }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("منابع و ابزارهای آموزشی ویژه معلمان", fontWeight = FontWeight.Black, color = BrandDark)
                        Text("کتابخانه دیجیتال و محتوای آموزشی", color = TextSecondary, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronLeft, null, tint = Brand)
                }
            }
        }
        val today = dashboard?.optJSONArray("todaySchedule") ?: JSONArray()
        if (today.length() > 0) item { Text("برنامه امروز", Modifier.padding(horizontal = 18.dp, vertical = 12.dp), fontWeight = FontWeight.Black, fontSize = 19.sp, color = TextPrimary) }
        items(today.toObjects()) { row ->
            SimpleCard(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(42.dp), RoundedCornerShape(14.dp), Brand.copy(alpha = .10f)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Schedule, null, tint = Brand) } }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${row.optString("subject")} · ${row.optString("className")}", fontWeight = FontWeight.Bold)
                        Text("زنگ ${row.optInt("periodNo")}", color = TextSecondary, fontSize = 12.sp)
                    }
                    Text("${row.optString("startTime")}–${row.optString("endTime")}", color = BrandDeep, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun HomeStat(label: String, value: String, icon: ImageVector, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 82.dp)) {
        Surface(Modifier.size(38.dp), CircleShape, tint.copy(alpha = .12f)) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp)) } }
        Spacer(Modifier.height(5.dp))
        Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextPrimary)
        Text(label, color = TextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun QuickFeature(feature: MenuFeature, onClick: () -> Unit) {
    Card(
        Modifier.padding(6.dp).fillMaxWidth().height(108.dp).clickable(onClick = onClick),
        RoundedCornerShape(20.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(Modifier.size(40.dp), RoundedCornerShape(13.dp), Brand.copy(alpha = .10f)) {
                Box(contentAlignment = Alignment.Center) { Icon(feature.icon, null, tint = Brand, modifier = Modifier.size(22.dp)) }
            }
            Spacer(Modifier.height(6.dp))
            Text(feature.title, fontWeight = FontWeight.Black, fontSize = 12.sp, textAlign = TextAlign.Center, color = TextPrimary, maxLines = 1)
            Text(feature.subtitle, color = TextSecondary, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ClassesScreen(workspace: JSONObject?, onNavigate: (Screen) -> Unit) {
    val assignments = workspace?.optJSONArray("assignments") ?: JSONArray()
    val classes = workspace?.optJSONArray("classes") ?: JSONArray()
    val students = workspace?.optJSONArray("students") ?: JSONArray()
    val classMap = classes.toObjects().associateBy { it.optString("id") }
    val unique = assignments.toObjects().distinctBy { "${it.optString("classId")}|${it.optString("subject")}" }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("کلاس‌ها")
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
            item {
                Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), Color(0xFFEAF9FB)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Groups, null, tint = Brand)
                        Spacer(Modifier.width(8.dp))
                        Column { Text("کلاس‌های تدریس من", fontWeight = FontWeight.Black, color = BrandDark); Text("مدیریت دانش‌آموزان و دسترسی سریع به امور کلاس", color = TextSecondary, fontSize = 11.sp) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            items(unique) { a ->
                val cid = a.optString("classId")
                val classStudents = students.toObjects().filter { it.optString("classId") == cid }
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), RoundedCornerShape(22.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(15.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(50.dp), RoundedCornerShape(16.dp), Brand.copy(.1f)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.School, null, tint = Brand, modifier = Modifier.size(26.dp)) } }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(classMap[cid]?.optString("name", cid) ?: cid, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                Text(a.optString("subject"), color = BrandDeep, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFEAF9FB)) { Text("${classStudents.size} دانش‌آموز", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = BrandDark, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                        }
                        if (classStudents.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            classStudents.take(4).forEach { st ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(Modifier.size(30.dp), CircleShape, Color(0xFFEFF6F7)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = TextSecondary, modifier = Modifier.size(17.dp)) } }
                                    Spacer(Modifier.width(8.dp))
                                    Text("${st.optString("firstName")} ${st.optString("lastName")}", modifier = Modifier.weight(1f), fontSize = 13.sp)
                                    Icon(Icons.Default.ChatBubbleOutline, null, tint = Brand, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { onNavigate(Screen.Students) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Brand), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Groups, null); Spacer(Modifier.width(5.dp)); Text("دانش‌آموزان") }
                    OutlinedButton(onClick = { onNavigate(Screen.Grades) }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Brand)) { Icon(Icons.Default.AutoGraph, null); Spacer(Modifier.width(5.dp)); Text("دفتر نمره") }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { onNavigate(Screen.Classroom) }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Brand)) { Icon(Icons.Default.Class, null); Spacer(Modifier.width(5.dp)); Text("دفتر کلاسی") }
                    OutlinedButton(onClick = { onNavigate(Screen.Schedule) }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Brand)) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(5.dp)); Text("برنامه") }
                }
            }
        }
    }
}

@Composable
private fun AttendanceScreen(dashboard: JSONObject?, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    val current = dashboard?.optJSONObject("currentClass")
    val date = dashboard?.optJSONObject("clock")?.optString("date").orEmpty()
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    if (current == null) {
        Column { ScreenHeader("حضور و غیاب"); EmptyState("در حال حاضر کلاس فعالی وجود ندارد.") }
        return
    }

    val students = current.optJSONArray("students")?.toObjects().orEmpty()
    val savedRecords = current.optJSONObject("attendance")?.optJSONArray("records")?.toObjects().orEmpty()
        .associateBy { it.optString("studentId") }
    val statuses = remember(current.toString()) {
        mutableStateMapOf<String, String>().apply {
            students.forEach { st ->
                val saved = savedRecords[st.optString("id")]?.optString("status").orEmpty()
                put(st.optString("id"), when (saved) {
                    "present" -> "present"
                    "absent_excused" -> "absent_excused"
                    "absent_unexcused" -> "absent_unexcused"
                    "absent_pending", "absent" -> "absent_pending"
                    else -> "present"
                })
            }
        }
    }
    val presentCount = statuses.values.count { it == "present" }
    val excusedCount = statuses.values.count { it == "absent_excused" }
    val unexcusedCount = statuses.values.count { it == "absent_unexcused" }
    val pendingCount = statuses.values.count { it == "absent_pending" }
    val absentCount = excusedCount + unexcusedCount + pendingCount
    val allPresent = students.isNotEmpty() && absentCount == 0

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("حضور و غیاب")
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${current.optString("subject")} · ${current.optString("className")}", fontWeight = FontWeight.Black, fontSize = 17.sp, color = TextPrimary)
                        Text("${current.optString("startTime")} تا ${current.optString("endTime")}", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(Modifier.weight(1f), RoundedCornerShape(15.dp), Color(0xFFE8F8F0)) { Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(presentCount.toString(), color = Success, fontWeight = FontWeight.Black, fontSize = 20.sp); Text("حاضر", color = Success, fontSize = 11.sp) } }
                            Surface(Modifier.weight(1f), RoundedCornerShape(15.dp), Color(0xFFFFF5E8)) { Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(excusedCount.toString(), color = Color(0xFFB7791F), fontWeight = FontWeight.Black, fontSize = 20.sp); Text("موجه", color = Color(0xFFB7791F), fontSize = 11.sp) } }
                            Surface(Modifier.weight(1f), RoundedCornerShape(15.dp), Color(0xFFFFEEEE)) { Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(unexcusedCount.toString(), color = Color(0xFFD64545), fontWeight = FontWeight.Black, fontSize = 20.sp); Text("غیرموجه", color = Color(0xFFD64545), fontSize = 11.sp) } }
                        }
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = { students.forEach { statuses[it.optString("id")] = "present" }; message = "" },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (allPresent) Color(0xFFDDF7ED) else Brand.copy(alpha = .10f), contentColor = if (allPresent) Success else BrandDark)
                        ) { Icon(Icons.Default.DoneAll, null); Spacer(Modifier.width(7.dp)); Text(if (allPresent) "همه دانش‌آموزان حاضر هستند" else "انتخاب همه = حاضر", fontWeight = FontWeight.Bold) }
                        if (absentCount > 0) { Spacer(Modifier.height(8.dp)); Text("فقط دانش‌آموزان غایب را انتخاب کنید و نوع غیبت را مشخص کنید.", color = TextSecondary, fontSize = 11.sp) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            items(students) { st ->
                val id = st.optString("id")
                val status = statuses[id] ?: "present"
                val isPresent = status == "present"
                val isExcused = status == "absent_excused"
                val isUnexcused = status == "absent_unexcused"
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), RoundedCornerShape(17.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(40.dp).clickable { statuses[id] = if (isPresent) "absent_pending" else "present"; message = "" }, CircleShape, if (isPresent) Color(0xFFE8F8F0) else Color(0xFFFFEEEE)) {
                                Box(contentAlignment = Alignment.Center) { Icon(if (isPresent) Icons.Default.Check else Icons.Default.PersonOff, null, tint = if (isPresent) Success else Color(0xFFD64545), modifier = Modifier.size(21.dp)) }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text("${st.optString("firstName")} ${st.optString("lastName")}", fontWeight = FontWeight.Bold); Text(st.optString("studentCode"), color = TextSecondary, fontSize = 11.sp) }
                            FilledTonalButton(
                                onClick = { statuses[id] = if (isPresent) "absent_pending" else "present"; message = "" }, shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (isPresent) Color(0xFFE8F8F0) else Color(0xFFFFEEEE), contentColor = if (isPresent) Success else Color(0xFFD64545)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) { Text(if (isPresent) "حاضر" else "غایب", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                        }
                        if (!isPresent) {
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = isExcused, onClick = { statuses[id] = "absent_excused"; message = "" }, label = { Text("غایب موجه") }, modifier = Modifier.weight(1f))
                                FilterChip(selected = isUnexcused, onClick = { statuses[id] = "absent_unexcused"; message = "" }, label = { Text("غایب غیرموجه") }, modifier = Modifier.weight(1f))
                            }
                            if (status == "absent_pending") Text("نوع غیبت را مشخص کنید.", color = Warning, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            item {
                if (message.isNotBlank()) Text(message, Modifier.padding(vertical = 8.dp), color = if (message.startsWith("ثبت شد")) Success else MaterialTheme.colorScheme.error)
                Button(
                    onClick = {
                        if (pendingCount > 0) { message = "برای $pendingCount دانش‌آموز غایب، موجه یا غیرموجه را مشخص کنید."; return@Button }
                        scope.launch {
                            saving = true; message = ""
                            try {
                                val records = JSONArray()
                                students.forEach { st -> records.put(JSONObject().put("studentId", st.optString("id")).put("status", statuses[st.optString("id")] ?: "present").put("note", "")) }
                                val result = ApiClient.post("/api/staff/attendance/program-save", JSONObject().put("scheduleEntryId", current.optString("id")).put("date", date).put("records", records))
                                val summary = result.optJSONObject("summary")
                                message = "ثبت شد: ${summary?.optInt("present", presentCount) ?: presentCount} حاضر · ${summary?.optInt("excused", excusedCount) ?: excusedCount} موجه · ${summary?.optInt("unexcused", unexcusedCount) ?: unexcusedCount} غیرموجه"
                                onRefresh()
                            } catch (e: Throwable) { message = e.message ?: "خطا در ثبت حضور و غیاب" }
                            finally { saving = false }
                        }
                    },
                    enabled = !saving && current.optBoolean("attendanceOpen", false) && date.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(54.dp).padding(top = 8.dp), shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand)
                ) { Icon(Icons.Default.DoneAll, null); Spacer(Modifier.width(6.dp)); Text(if (saving) "در حال ثبت…" else "ثبت نهایی حضور و غیاب", fontWeight = FontWeight.Black) }
            }
        }
    }
}

@Composable
private fun GradesScreen(workspace: JSONObject?) {
    val scope = rememberCoroutineScope(); val assignments = workspace?.optJSONArray("assignments")?.toObjects().orEmpty().distinctBy { "${it.optString("classId")}|${it.optString("subject")}" }
    val classes = workspace?.optJSONArray("classes")?.toObjects().orEmpty().associateBy { it.optString("id") }
    var selected by remember { mutableStateOf<JSONObject?>(null) }; var period by remember { mutableStateOf("semester2") }; var gradebook by remember { mutableStateOf<JSONObject?>(null) }; var loading by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
    fun load(a: JSONObject) { selected = a; scope.launch { loading = true; error = ""; try { val c = URLEncoder.encode(a.optString("classId"), "UTF-8"); val s = URLEncoder.encode(a.optString("subject"), "UTF-8"); gradebook = ApiClient.get("/api/staff/gradebook?classId=$c&subject=$s&academicPeriod=$period") } catch (e: Throwable) { error = e.message ?: "خطا" } finally { loading = false } } }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("دفتر نمره")
        if (selected == null) LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item { Text("کلاس و درس را انتخاب کنید", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }
            items(assignments) { a -> SimpleCard(Modifier.clickable { load(a) }) { Text("${a.optString("subject")} · ${classes[a.optString("classId")]?.optString("name", a.optString("classId"))}", fontWeight = FontWeight.Black); Text("ورود به دفتر نمره", color = Brand, fontSize = 12.sp) } }
        } else {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { period = "semester1"; selected?.let { load(it) } }, label = { Text("نیم‌سال اول") }, colors = AssistChipDefaults.assistChipColors(containerColor = if (period == "semester1") Brand.copy(.12f) else Color.White))
                AssistChip(onClick = { period = "semester2"; selected?.let { load(it) } }, label = { Text("نیم‌سال دوم") }, colors = AssistChipDefaults.assistChipColors(containerColor = if (period == "semester2") Brand.copy(.12f) else Color.White))
                TextButton(onClick = { selected = null; gradebook = null }) { Text("تغییر کلاس") }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            gradebook?.let { GradebookEditor(it, selected!!, period) }
        }
    }
}

@Composable
private fun GradebookEditor(book: JSONObject, assignment: JSONObject, period: String) {
    val scope = rememberCoroutineScope(); val categories = book.optJSONObject("config")?.optJSONArray("categories")?.toObjects().orEmpty().filter { it.optString("source") != "exam" }
    val students = book.optJSONArray("students")?.toObjects().orEmpty(); val category = categories.firstOrNull()
    val scores = remember(book.toString()) { mutableStateMapOf<String, String>() }; val notes = remember(book.toString()) { mutableStateMapOf<String, String>() }; var message by remember { mutableStateOf("") }
    if (category == null) { EmptyState("برای این درس مؤلفه دستی دفتر نمره تعریف نشده است."); return }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(students) { st ->
            val id = st.optString("id"); val current = st.optJSONObject("result")?.opt("score")?.toString()?.takeIf { it != "null" }.orEmpty()
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), RoundedCornerShape(18.dp), CardDefaults.cardColors(Color.White)) {
                Column(Modifier.padding(14.dp)) {
                    Text("${st.optString("firstName")} ${st.optString("lastName")}", fontWeight = FontWeight.Black)
                    Text("نمره مستمر محاسبه‌شده: ${current.ifBlank { "—" }}", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(scores[id].orEmpty(), { scores[id] = it }, Modifier.weight(.34f), label = { Text("از ۲۰") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        OutlinedTextField(notes[id].orEmpty(), { notes[id] = it }, Modifier.weight(.66f), label = { Text("توضیح") }, singleLine = true)
                    }
                    TextButton(onClick = {
                        val score = scores[id]?.toDoubleOrNull(); if (score == null || score !in 0.0..20.0) { message = "نمره ${st.optString("firstName")} معتبر نیست."; return@TextButton }
                        scope.launch { try { ApiClient.post("/api/staff/gradebook/entry", JSONObject().put("studentId", id).put("classId", assignment.optString("classId")).put("subject", assignment.optString("subject")).put("categoryId", category.optString("id")).put("academicPeriod", period).put("score", score).put("maxScore", 20).put("note", notes[id].orEmpty())); message = "نمره ${st.optString("firstName")} ثبت شد ✓" } catch (e: Throwable) { message = e.message ?: "خطا" } }
                    }) { Text("ثبت نمره") }
                }
            }
        }
        if (message.isNotBlank()) item { Text(message, Modifier.padding(8.dp), color = if (message.contains("✓")) Success else MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ExamsScreen(workspace: JSONObject?, onNavigate: (Screen) -> Unit) {
    val exams = workspace?.optJSONArray("exams")?.toObjects().orEmpty()
    val attempts = workspace?.optJSONArray("attempts")?.toObjects().orEmpty()
    val submissions = workspace?.optJSONArray("submissions")?.toObjects().orEmpty()
    val classes = workspace?.optJSONArray("classes")?.toObjects().orEmpty().associateBy { it.optString("id") }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("آزمون‌های من")
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), CardDefaults.cardColors(Color(0xFFE8F8FA))) {
                    Column(Modifier.padding(15.dp)) {
                        Text("طراحی آزمون جدید", fontWeight = FontWeight.Black, color = BrandDark, fontSize = 18.sp)
                        Text("مستمر یا پایانی؛ چندسؤالی با بارم‌بندی و انتشار مستقیم", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { onNavigate(Screen.ExamCreate) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Brand)) {
                            Icon(Icons.Default.EditNote, null); Spacer(Modifier.width(6.dp)); Text("ورود به استودیو طراحی آزمون")
                        }
                    }
                }
            }
            items(exams) { e ->
                val done = attempts.count { it.optString("examId") == e.optString("id") } + submissions.count { it.optString("examId") == e.optString("id") }
                val finalExam = e.optString("examCategory", "ongoing") == "final"
                SimpleCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = if (finalExam) Color(0xFFFFF2DC) else Color(0xFFE8F8FA), shape = RoundedCornerShape(10.dp)) {
                            Text(if (finalExam) "پایانی" else "مستمر", Modifier.padding(horizontal = 9.dp, vertical = 4.dp), color = if (finalExam) Color(0xFFB46A00) else BrandDark, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp)); Text(e.optString("title", "آزمون"), fontWeight = FontWeight.Black)
                    }
                    Text("${e.optString("subject")} · ${classes[e.optString("classId")]?.optString("name", e.optString("classId"))}", color = TextSecondary)
                    Text("وضعیت: ${e.optString("publicationState", e.optString("status"))} · تحویل: $done · بارم: ${e.optDouble("targetScore", e.optDouble("totalScore", 0.0))}", fontSize = 12.sp, color = TextSecondary)
                }
            }
            if (exams.isEmpty()) item { EmptyState("هنوز آزمونی نساخته‌اید؛ از استودیو آزمون شروع کنید.") }
        }
    }
}

@Composable
private fun ScheduleScreen(dashboard: JSONObject?) {
    val rows = dashboard?.optJSONArray("weekSchedule")?.toObjects().orEmpty(); val days = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")
    Column(Modifier.fillMaxSize()) { ScreenHeader("برنامه هفتگی"); LazyColumn(contentPadding = PaddingValues(16.dp)) { days.forEach { day -> val ds = rows.filter { it.optString("day") == day }; if (ds.isNotEmpty()) item { SimpleCard { Text(day, fontWeight = FontWeight.Black, color = BrandDeep); ds.forEach { x -> Text("${x.optString("startTime")}–${x.optString("endTime")}  ${x.optString("subject")} · ${x.optString("className")}", Modifier.padding(top = 6.dp)) } } } } } }
}

@Composable
private fun NotificationsScreen(dashboard: JSONObject?) {
    val rows = dashboard?.optJSONArray("notifications")?.toObjects().orEmpty()
    Column(Modifier.fillMaxSize()) { ScreenHeader("اعلان‌ها"); LazyColumn(contentPadding = PaddingValues(16.dp)) { items(rows) { n -> SimpleCard { Text(n.optString("title", "اعلان"), fontWeight = FontWeight.Black); Text(n.optString("body"), color = TextSecondary); Text(n.optString("createdAt"), color = TextSecondary, fontSize = 10.sp) } }; if (rows.isEmpty()) item { EmptyState("اعلان جدیدی ندارید.") } } }
}

@Composable
private fun MessagesScreen() {
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var messages by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    suspend fun reload() {
        val c = ApiClient.get("/api/academic-messages/p58/contacts")
        val t = ApiClient.get("/api/academic-messages/p58/threads")
        contacts = c.optJSONArray("contacts")?.toObjects().orEmpty()
        messages = t.optJSONArray("messages")?.toObjects().orEmpty()
    }
    LaunchedEffect(Unit) { try { reload() } catch (e: Throwable) { error = e.message ?: "خطا" } finally { loading = false } }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (selected == null) "پیام‌ها و ارتباط آموزشی" else selected!!.optString("name", "گفتگو"))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Brand)
        if (error.isNotBlank()) Text(error, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
        if (selected == null) {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                item {
                    Surface(Modifier.fillMaxWidth().padding(bottom = 10.dp), RoundedCornerShape(18.dp), Color(0xFFE8F8FA)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Forum, null, tint = Brand); Spacer(Modifier.width(8.dp)); Text("ارتباط سریع و امن با دانش‌آموزان", color = BrandDark, fontWeight = FontWeight.Bold) }
                    }
                }
                items(contacts.distinctBy { "${it.optString("studentId")}|${it.optString("subject")}" }) { c ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { selected = c }, RoundedCornerShape(19.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(1.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(44.dp), CircleShape, Brand.copy(.1f)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Brand) } }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text(c.optString("name", "دانش‌آموز"), fontWeight = FontWeight.Black); Text(c.optString("subject"), color = TextSecondary, fontSize = 11.sp) }
                            Surface(Modifier.size(36.dp), CircleShape, Color(0xFFE8F8FA)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.ChatBubbleOutline, null, tint = Brand, modifier = Modifier.size(19.dp)) } }
                        }
                    }
                }
                if (contacts.isEmpty() && !loading) item { EmptyState("مخاطبی در محدوده تدریس شما نیست.") }
            }
        } else {
            val c = selected!!; val sid = c.optString("studentId"); val subject = c.optString("subject")
            val thread = messages.filter { it.optString("studentId") == sid && it.optString("subject") == subject }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                items(thread) { m ->
                    val mine = m.optString("fromRole") == "teacher"
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End) {
                        Surface(color = if (mine) Brand else Color.White, shape = RoundedCornerShape(17.dp), shadowElevation = 1.dp) {
                            Column(Modifier.padding(12.dp).widthIn(max = 280.dp)) { Text(m.optString("message"), color = if (mine) Color.White else TextPrimary); Text(m.optString("at"), color = if (mine) Color.White.copy(.7f) else TextSecondary, fontSize = 9.sp) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            Row(Modifier.fillMaxWidth().background(Color.White).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(draft, { draft = it.take(2000) }, Modifier.weight(1f), placeholder = { Text("پیام…") }, maxLines = 4, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand))
                Spacer(Modifier.width(6.dp))
                FilledIconButton(onClick = { if (draft.isNotBlank()) scope.launch { try { ApiClient.post("/api/academic-messages/p58/send", JSONObject().put("studentId", sid).put("subject", subject).put("message", draft.trim())); draft = ""; reload() } catch (e: Throwable) { error = e.message ?: "خطا" } } }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Brand)) { Icon(Icons.Default.Send, null) }
            }
            TextButton(onClick = { selected = null }, Modifier.fillMaxWidth()) { Text("بازگشت به مخاطبان", color = Brand) }
        }
    }
}

@Composable
private fun MoreScreen(onNavigate: (Screen) -> Unit, onLogout: () -> Unit) {
    val groups = listOf(
        "آموزش و کلاس" to listOf(
            MenuFeature("کتاب‌ها و کلاس‌های من", Icons.Default.School, Screen.MyTeaching, "تخصیص‌های تدریس و دانش‌آموزان"),
            MenuFeature("آموزش و فصل‌ها", Icons.Default.AutoStories, Screen.Curriculum, "کتاب‌ها و فصل‌های درسی"),
            MenuFeature("دانش‌آموزان من", Icons.Default.Groups, Screen.Students, "عکس، پروفایل و وضعیت آموزشی"),
            MenuFeature("دفتر کلاسی", Icons.Default.Class, Screen.Classroom, "ثبت جلسه، تدریس و تکلیف"),
            MenuFeature("برنامه هفتگی", Icons.Default.CalendarMonth, Screen.Schedule, "زنگ‌ها و برنامه تدریس"),
            MenuFeature("کلاس آنلاین", Icons.Default.VideoCall, Screen.OnlineClass, "شروع نشست زنگ آنلاین")
        ),
        "حضور و ارزیابی" to listOf(
            MenuFeature("حضور و غیاب", Icons.Default.FactCheck, Screen.Attendance, "ثبت روزانه حضور"),
            MenuFeature("حضور و انضباط", Icons.Default.AccessTime, Screen.AttendanceDiscipline, "غیبت، تأخیر و پیگیری"),
            MenuFeature("دفتر نمره", Icons.Default.AutoGraph, Screen.Grades, "ثبت و مدیریت نمرات"),
            MenuFeature("مخزن سؤال", Icons.Default.Quiz, Screen.QuestionBank, "ثبت و مدیریت سؤال‌ها"),
            MenuFeature("بانک سؤال و آزمون مستقل", Icons.Default.Psychology, Screen.IndependentExamCenter, "انتشار آزمون تمرینی و مشاهده نتایج"),
            MenuFeature("طراحی آزمون", Icons.Default.PostAdd, Screen.ExamCreate, "ساخت آزمون مستمر و پایانی"),
            MenuFeature("آزمون‌ها", Icons.Default.AssignmentTurnedIn, Screen.Exams, "آزمون‌های واقعی سرور"),
            MenuFeature("تصحیح و نمرات", Icons.Default.Grading, Screen.Grading, "برگه‌های تحویلی و وضعیت تصحیح")
        ),
        "تکلیف و محتوای آموزشی" to listOf(
            MenuFeature("مرکز تکالیف", Icons.Default.Assignment, Screen.Homework, "ساخت و انتشار تکلیف"),
            MenuFeature("بانک تمرین", Icons.Default.Layers, Screen.Practice, "تمرین و محتوای آموزشی"),
            MenuFeature("کتابخانه دیجیتال", Icons.Default.MenuBook, Screen.Library, "منابع و فایل‌های آموزشی"),
            MenuFeature("برنامه آموزشی", Icons.Default.EventNote, Screen.LearningPlanner, "رویدادها و تقویم آموزشی")
        ),
        "ارتباط با دانش‌آموز و خانواده" to listOf(
            MenuFeature("پیام مستقیم دانش‌آموزان", Icons.Default.ChatBubble, Screen.Messages, "گفت‌وگوی آموزشی مستقیم"),
            MenuFeature("ارتباطات و پیام‌ها", Icons.Default.Campaign, Screen.Communications, "صندوق پیام و اطلاعیه‌ها"),
            MenuFeature("ملاقات اولیا", Icons.Default.GroupAdd, Screen.GuardianMeetings, "دعوت و پاسخ خانواده‌ها")
        ),
        "گزارش و پیگیری" to listOf(
            MenuFeature("گزارش‌ها", Icons.Default.Insights, Screen.Reports, "گزارش مستمر دانش‌آموز"),
            MenuFeature("تحلیل دانش‌آموز", Icons.Default.Analytics, Screen.StudentAnalysis, "پرونده تحلیلی آموزشی"),
            MenuFeature("مرکز اقدام", Icons.Default.TrackChanges, Screen.ActionCenter, "کارها و پرونده‌های پیگیری"),
            MenuFeature("اعلان‌ها", Icons.Default.NotificationsNone, Screen.Notifications, "اعلان‌های حساب")
        ),
        "حساب و امنیت" to listOf(
            MenuFeature("حساب و امنیت", Icons.Default.Security, Screen.Account, "نشست‌ها و امنیت ورود")
        )
    )

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("همه امکانات")
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    RoundedCornerShape(22.dp),
                    CardDefaults.cardColors(Color(0xFFE6F9F8))
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(54.dp), RoundedCornerShape(17.dp), Color.White) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.DashboardCustomize, null, tint = Brand, modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("ابزارهای کامل معلم", fontWeight = FontWeight.Black, color = BrandDark, fontSize = 17.sp)
                            Text("همه بخش‌های پنل معلم، مرتب‌شده برای استفاده سریع", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            groups.forEach { (groupTitle, features) ->
                item {
                    Text(
                        groupTitle,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
                        color = BrandDark,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                }
                items(features) { f ->
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onNavigate(f.screen) },
                        RoundedCornerShape(19.dp),
                        CardDefaults.cardColors(Color.White),
                        CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(44.dp), RoundedCornerShape(14.dp), Brand.copy(.1f)) {
                                Box(contentAlignment = Alignment.Center) { Icon(f.icon, null, tint = Brand) }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.title, fontWeight = FontWeight.Black, color = TextPrimary)
                                Text(f.subtitle, color = TextSecondary, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.ChevronLeft, null, tint = Brand)
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Icon(Icons.Default.Logout, null)
                    Spacer(Modifier.width(6.dp))
                    Text("خروج از حساب")
                }
            }
        }
    }
}

@Composable
private fun SimpleApiListScreen(title: String, path: String, arrayKey: String) {
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val isLibrary = title.contains("کتابخانه")
    LaunchedEffect(path) { try { rows = ApiClient.get(path).optJSONArray(arrayKey)?.toObjects().orEmpty() } catch (e: Throwable) { error = e.message ?: "خطا" } finally { loading = false } }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Brand)
        if (error.isNotBlank()) Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Surface(Modifier.fillMaxWidth().padding(bottom = 10.dp), RoundedCornerShape(20.dp), if (isLibrary) Color(0xFFE4F6FF) else Color(0xFFE8F9F4)) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(50.dp), RoundedCornerShape(16.dp), Color.White) { Box(contentAlignment = Alignment.Center) { Icon(if (isLibrary) Icons.Default.MenuBook else Icons.Default.Layers, null, tint = if (isLibrary) Sky else Brand, modifier = Modifier.size(26.dp)) } }
                        Spacer(Modifier.width(10.dp)); Column { Text(if (isLibrary) "منابع آموزشی مدرسه" else "تمرین‌های آموزشی", fontWeight = FontWeight.Black, color = TextPrimary); Text(if (isLibrary) "دسترسی سریع به فایل‌ها و محتوا" else "تمرین‌های مرتبط با درس‌های شما", color = TextSecondary, fontSize = 11.sp) }
                    }
                }
            }
            items(rows) { r ->
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), RoundedCornerShape(19.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(1.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(42.dp), RoundedCornerShape(13.dp), (if (isLibrary) Sky else Brand).copy(.10f)) { Box(contentAlignment = Alignment.Center) { Icon(if (isLibrary) Icons.Default.Description else Icons.Default.Assignment, null, tint = if (isLibrary) Sky else Brand) } }
                        Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(r.optString("title", r.optString("subject", "مورد")), fontWeight = FontWeight.Black); val sub = listOf(r.optString("subject"), r.optString("teacherName"), r.optString("createdAt")).filter { it.isNotBlank() }.joinToString(" · "); if (sub.isNotBlank()) Text(sub, color = TextSecondary, fontSize = 11.sp) }; Icon(Icons.Default.ChevronLeft, null, tint = TextSecondary)
                    }
                }
            }
            if (rows.isEmpty() && !loading && error.isBlank()) item { EmptyState("موردی برای نمایش وجود ندارد.") }
        }
    }
}

@Composable
private fun AccountScreen(onLogout: () -> Unit) {
    var me by remember { mutableStateOf<JSONObject?>(null) }; var sessions by remember { mutableStateOf<List<JSONObject>>(emptyList()) }; var error by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { try { me = ApiClient.get("/api/staff/me").optJSONObject("user"); sessions = ApiClient.get("/api/staff/sessions").optJSONArray("sessions")?.toObjects().orEmpty() } catch (e: Throwable) { error = e.message ?: "خطا" } }
    Column(Modifier.fillMaxSize()) { ScreenHeader("حساب و امنیت"); LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item { SimpleCard { Text("${me?.optString("firstName").orEmpty()} ${me?.optString("lastName").orEmpty()}", fontWeight = FontWeight.Black, fontSize = 18.sp); Text("نقش: ${me?.optString("role", "teacher")}", color = TextSecondary) } }
        item { Text("نشست‌های فعال", Modifier.padding(vertical = 10.dp), fontWeight = FontWeight.Black) }
        items(sessions) { s -> SimpleCard { Text(s.optString("deviceLabel", s.optString("userAgent", "دستگاه")), fontWeight = FontWeight.Bold); Text(s.optString("lastSeenAt", s.optString("createdAt")), color = TextSecondary, fontSize = 11.sp) } }
        if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
        item { Button(onClick = onLogout, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB42318))) { Text("خروج از حساب") } }
    } }
}

@Composable private fun ScreenHeader(title: String) {
    Box(
        Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(BrandDeep, Brand))).statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)
    ) { Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black) }
}
@Composable private fun SimpleCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth().padding(vertical = 5.dp), RoundedCornerShape(19.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(1.dp)) { Column(Modifier.padding(15.dp), content = content) }
}
@Composable private fun SectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp), RoundedCornerShape(22.dp), CardDefaults.cardColors(Color.White), CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(36.dp), RoundedCornerShape(12.dp), Brand.copy(.1f)) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = Brand, modifier = Modifier.size(20.dp)) } }; Spacer(Modifier.width(8.dp)); Text(title, fontWeight = FontWeight.Black, color = BrandDark) }; Spacer(Modifier.height(12.dp)); content() }
    }
}
@Composable private fun EmptyState(text: String) { Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { Text(text, color = TextSecondary, textAlign = TextAlign.Center) } }

private fun JSONArray.toObjects(): List<JSONObject> = buildList { for (i in 0 until length()) optJSONObject(i)?.let { add(it) } }
