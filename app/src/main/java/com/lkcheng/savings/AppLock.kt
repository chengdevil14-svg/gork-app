package com.lkcheng.savings

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest

object AppLock {
    const val ON = "lock_on"
    const val METHOD = "lock_method" // 0 系统（指纹/脸/手机密码）, 1 App PIN
    const val AFTER = "lock_after" // 秒：0 每次, 60, 300
    const val PIN = "lock_pin"
    const val SECURE = "lock_secure"

    fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest(("cz-" + pin).toByteArray()).joinToString("") { "%02x".format(it) }

    fun canSystem(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(activity: FragmentActivity, title: String, onOk: () -> Unit, onFail: (String) -> Unit = {}) {
        try {
            val p = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail(errString.toString())
                },
            )
            p.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle("用指纹、人脸或手机锁屏密码")
                    .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                    .build()
            )
        } catch (e: Exception) {
            onFail(e.message ?: "无法使用")
        }
    }
}

@Composable
fun PinPad(title: String, sub: String, error: String, onDone: (String) -> Unit, extra: @Composable () -> Unit = {}) {
    var pin by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (i in 0 until 6) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "✓"))
        for (row in keys) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                for (k in row) {
                    Box(
                        Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                when (k) {
                                    "⌫" -> pin = pin.dropLast(1)
                                    "✓" -> if (pin.length >= 4) {
                                        onDone(pin)
                                        pin = ""
                                    }
                                    else -> if (pin.length < 6) pin += k
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(k, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        extra()
    }
}

@Composable
fun LockScreen(store: Store, onUnlock: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    val method = store.getI(AppLock.METHOD, 0)
    val sysOk = remember { AppLock.canSystem(activity) }
    var error by remember { mutableStateOf("") }
    fun sys() = AppLock.prompt(activity, "解锁成长账本", onUnlock) { msg -> error = msg }
    LaunchedEffect(Unit) { if (method == 0 && sysOk) sys() }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        if (method == 1 || !sysOk) {
            PinPad("🔒 成长账本", "输入 App 密码", error, onDone = { p ->
                if (AppLock.hash(p) == store.getS(AppLock.PIN, "")) onUnlock() else error = "密码不对，再试一次"
            }) {
                if (sysOk) TextButton(onClick = { sys() }) { Text("用指纹 / 人脸 / 手机密码") }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔒", fontSize = 56.sp)
                Spacer(Modifier.height(12.dp))
                Text("成长账本已锁定", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { sys() }, modifier = Modifier.padding(horizontal = 32.dp)) { Text("解锁", fontSize = 16.sp) }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AppLockPage(store: Store, onSecureChange: (Boolean) -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    val sysOk = remember { AppLock.canSystem(activity) }
    var on by remember { mutableStateOf(store.getB(AppLock.ON, false)) }
    var method by remember { mutableStateOf(store.getI(AppLock.METHOD, if (sysOk) 0 else 1)) }
    var after by remember { mutableStateOf(store.getI(AppLock.AFTER, 0)) }
    var secure by remember { mutableStateOf(store.getB(AppLock.SECURE, false)) }
    var settingPin by remember { mutableStateOf(false) }
    var firstPin by remember { mutableStateOf("") }
    var pinErr by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    val hasPin = store.getS(AppLock.PIN, "").isNotEmpty()

    if (settingPin) {
        Box(Modifier.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            PinPad(
                if (firstPin.isEmpty()) "设置 App 密码" else "再输入一次",
                "4–6 位数字",
                pinErr,
                onDone = { p ->
                    if (firstPin.isEmpty()) {
                        firstPin = p
                        pinErr = ""
                    } else if (p == firstPin) {
                        store.setS(AppLock.PIN, AppLock.hash(p))
                        store.setI(AppLock.METHOD, 1)
                        store.setB(AppLock.ON, true)
                        method = 1
                        on = true
                        settingPin = false
                        firstPin = ""
                        msg = "✅ App 密码已设置，应用锁已开启"
                    } else {
                        firstPin = ""
                        pinErr = "两次不一样，请重新设置"
                    }
                },
            ) { TextButton(onClick = { settingPin = false; firstPin = "" }) { Text("取消") } }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        androidx.compose.material3.Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
            SwitchRow("开启应用锁", "打开 app 时需要解锁", on) { v ->
                if (!v) {
                    // turning off needs verification
                    if (sysOk) {
                        AppLock.prompt(activity, "关闭应用锁", {
                            on = false
                            store.setB(AppLock.ON, false)
                            msg = "应用锁已关闭"
                        })
                    } else {
                        on = false
                        store.setB(AppLock.ON, false)
                    }
                } else if (method == 0 && sysOk) {
                    AppLock.prompt(activity, "开启应用锁", {
                        on = true
                        store.setB(AppLock.ON, true)
                        store.setI(AppLock.METHOD, 0)
                        msg = "✅ 应用锁已开启"
                    })
                } else if (hasPin) {
                    on = true
                    store.setB(AppLock.ON, true)
                } else {
                    settingPin = true
                }
            }
        }
        SectionTitle("解锁方式")
        androidx.compose.material3.Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
            OptionRow(
                "指纹 / 人脸 / 手机锁屏密码",
                if (sysOk) "用手机自带的解锁方式" else "这台手机还没设置锁屏或指纹，暂时不能用",
                method == 0,
            ) {
                if (sysOk) {
                    method = 0
                    store.setI(AppLock.METHOD, 0)
                }
            }
            androidx.compose.material3.HorizontalDivider()
            OptionRow("App 专用密码（PIN）", if (hasPin) "已设置 · 点这里修改" else "4–6 位数字，和手机密码分开", method == 1) {
                settingPin = true
                firstPin = ""
                pinErr = ""
            }
        }
        SectionTitle("什么时候锁")
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((label, sec) in listOf(
                "每次离开就锁" to 0,
                "离开 30 秒" to 30,
                "离开 1 分钟" to 60,
                "离开 5 分钟" to 300,
                "离开 15 分钟" to 900,
                "离开 30 分钟" to 1800,
                "离开 1 小时" to 3600,
                "只在重新打开 app 时" to -1,
            )) {
                androidx.compose.material3.FilterChip(
                    selected = after == sec,
                    onClick = {
                        after = sec
                        store.setI(AppLock.AFTER, sec)
                    },
                    label = { Text(label, fontSize = 13.sp) },
                )
            }
        }
        Text(
            when (after) {
                0 -> "切到别的 app 或锁屏，回来就要解锁（最安全）"
                -1 -> "只有 app 被完全关闭 / 手机重启后再打开才要解锁（最方便）"
                else -> "离开超过这个时间才要解锁，短时间切出去回来不用"
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionTitle("隐私")
        androidx.compose.material3.Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
            SwitchRow("在「最近任务」隐藏画面", "多任务界面看不到金额。开启后也不能在 app 内截图。", secure) { v ->
                secure = v
                store.setB(AppLock.SECURE, v)
                onSecureChange(v)
            }
        }
        if (msg.isNotEmpty()) Text(msg, fontSize = 13.sp)
        Text(
            "忘记 App 密码：可以在锁屏页点「用指纹 / 人脸 / 手机密码」解锁，再来这里重设。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Suppress("unused")
private val unusedColor = Color.Unspecified
