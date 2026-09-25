@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.lkcheng.savings

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Observable boolean/int preferences that persist on every change. */
class BoolPref(private val store: Store, private val key: String, def: Boolean) {
    private val st: MutableState<Boolean> = mutableStateOf(store.getB(key, def))
    var value: Boolean
        get() = st.value
        set(v) {
            st.value = v
            store.setB(key, v)
        }
}

class IntPref(private val store: Store, private val key: String, def: Int) {
    private val st: MutableIntState = mutableIntStateOf(store.getI(key, def))
    var value: Int
        get() = st.intValue
        set(v) {
            st.intValue = v
            store.setI(key, v)
        }
}

class AppPrefs(store: Store) {
    val remOn = BoolPref(store, K.REM_ON, false)
    val skipIfRecorded = BoolPref(store, K.SKIP_IF_RECORDED, true)
    val dueOn = BoolPref(store, K.DUE_ON, true)
    val dueH = IntPref(store, K.DUE_H, 9)
    val dueM = IntPref(store, K.DUE_M, 0)
    val dueStyle = IntPref(store, K.DUE_STYLE, 0)
    val emailOn = BoolPref(store, K.EMAIL_ON, false)
    val popupOn = BoolPref(store, K.POPUP_ON, true)
    val subOn = BoolPref(store, K.SUB_ON, true)
    val subH = IntPref(store, K.SUB_H, 10)
    val subM = IntPref(store, K.SUB_M, 0)
    val subStyle = IntPref(store, K.SUB_STYLE, 0)
    val subEmail = BoolPref(store, K.SUB_EMAIL, false)
    val slotOn = SLOTS.associate { it.key to BoolPref(store, K.slotOn(it.key), true) }
    val slotH = SLOTS.associate { it.key to IntPref(store, K.slotH(it.key), it.h) }
    val slotM = SLOTS.associate { it.key to IntPref(store, K.slotM(it.key), it.m) }
}

fun hm(h: Int, m: Int) = String.format(Locale.US, "%02d:%02d", h, m)

fun pickTime(ctx: Context, h: Int, m: Int, onPicked: (Int, Int) -> Unit) {
    TimePickerDialog(ctx, { _, hh, mm -> onPicked(hh, mm) }, h, m, true).show()
}

@Composable
fun SettingsScreen(
    theme: Int,
    onTheme: (Int) -> Unit,
    greenIncome: Boolean,
    onGreen: (Boolean) -> Unit,
    syncUrl: String,
    sheetUrl: String,
    onSyncUrl: (String) -> Unit,
    onSheetFound: (String) -> Unit,
    prefs: AppPrefs,
    entries: List<Entry>,
    goals: List<Goal>,
    subs: List<Sub>,
    onEditSub: (Sub) -> Unit,
    onCurrency: (String) -> Unit,
    onEditGoal: (Goal) -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf("") }
    BackHandler { if (page.isEmpty()) onClose() else page = "" }
    val titles = mapOf(
        "" to "设置",
        "theme" to "主题",
        "color" to "颜色",
        "currency" to "货币",
        "remind" to "提醒与通知",
        "popup" to "打开 app 时提醒",
        "sync" to "Google Sheet 同步",
        "data" to "数据与备份",
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titles[page] ?: "设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { if (page.isEmpty()) onClose() else page = "" }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        ) {
            when (page) {
                "" -> Column {
                    val remTxt = if (prefs.remOn.value) "已开启" else "已关闭"
                    Card(shape = RoundedCornerShape(16.dp)) {
                        MenuItem("🎨", "主题", THEME_NAMES[theme]) { page = "theme" }
                        HorizontalDivider()
                        MenuItem("🟢", "颜色", if (greenIncome) "存入绿 · 取出红" else "存入红 · 取出绿") { page = "color" }
                        HorizontalDivider()
                        MenuItem(
                            "💱", "货币",
                            "结算：" + (CURRENCIES.firstOrNull { it.first == Cur.code }?.third ?: Cur.code),
                        ) { page = "currency" }
                    }
                    Spacer(Modifier.height(12.dp))
                    Card(shape = RoundedCornerShape(16.dp)) {
                        MenuItem("🔔", "提醒与通知", "问候、饭点记账、还款日、订阅续费 · $remTxt") { page = "remind" }
                        HorizontalDivider()
                        MenuItem("📋", "打开 app 时提醒", if (prefs.popupOn.value) "已开启" else "已关闭") { page = "popup" }
                    }
                    Spacer(Modifier.height(12.dp))
                    Card(shape = RoundedCornerShape(16.dp)) {
                        MenuItem("☁", "Google Sheet 同步", if (syncUrl.isBlank()) "未连接" else "已连接") { page = "sync" }
                        HorizontalDivider()
                        MenuItem("💾", "数据与备份", "导出、清空 · 共 ${entries.size} 条") { page = "data" }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "存钱记录 v" + appVersion(LocalContext.current),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                "theme" -> Column { ThemePage(theme, onTheme) }
                "color" -> Column { ColorPage(greenIncome, onGreen) }
                "currency" -> Column { CurrencyPage(onCurrency) }
                "remind" -> Column { RemindPage(prefs, goals, subs, syncUrl, onEditGoal, onEditSub) }
                "popup" -> Column { PopupPage(prefs) }
                "sync" -> Column { SyncPage(syncUrl, sheetUrl, onSyncUrl, onSheetFound) }
                "data" -> Column { DataPage(entries, onClearAll) }
            }
        }
    }
}

fun appVersion(ctx: Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
} catch (e: Exception) {
    "?"
}

@Composable
fun MenuItem(icon: String, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
fun SectionTitle(t: String) {
    Text(
        t,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
fun SwitchRow(title: String, sub: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun OptionRow(title: String, sub: String?, selected: Boolean, leading: @Composable () -> Unit = {}, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                .padding(5.dp),
        ) {
            if (selected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
fun ThemePage(theme: Int, onTheme: (Int) -> Unit) {
    val subs = listOf("白底 + 靛蓝 #6610F2，清爽明亮", "深色背景，晚上看不刺眼", "米白底 + 暖金色，温暖柔和")
    Card(shape = RoundedCornerShape(16.dp)) {
        for (i in THEME_NAMES.indices) {
            if (i > 0) HorizontalDivider()
            OptionRow(
                THEME_NAMES[i], subs[i], theme == i,
                leading = {
                    Box(
                        Modifier
                            .padding(end = 14.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(THEME_SWATCH[i])
                    )
                },
            ) { onTheme(i) }
        }
    }
}

@Composable
fun ColorPage(greenIncome: Boolean, onGreen: (Boolean) -> Unit) {
    val green = Color(0xFF16A34A)
    val red = Color(0xFFDC2626)
    Card(shape = RoundedCornerShape(16.dp)) {
        OptionRow(
            "存入绿色 · 取出红色", "国际常用：绿色=钱进来", greenIncome,
            leading = { ColorPair(green, red) },
        ) { onGreen(true) }
        HorizontalDivider()
        OptionRow(
            "存入红色 · 取出绿色", "股市习惯：红涨绿跌", !greenIncome,
            leading = { ColorPair(red, green) },
        ) { onGreen(false) }
    }
    Text(
        "日历、记录、目标卡片的颜色都会跟着改。",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
fun ColorPair(a: Color, b: Color) {
    Row(Modifier.padding(end = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("+", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(a).padding(horizontal = 8.dp, vertical = 2.dp))
        Text("−", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(b).padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
fun RemindPage(
    prefs: AppPrefs,
    goals: List<Goal>,
    subs: List<Sub>,
    syncUrl: String,
    onEditGoal: (Goal) -> Unit,
    onEditSub: (Sub) -> Unit,
) {
    val ctx = LocalContext.current
    var permOk by remember { mutableStateOf(Reminders.hasPermission(ctx)) }
    var toast by remember { mutableStateOf("") }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permOk = granted
        if (granted) {
            prefs.remOn.value = true
            Reminders.scheduleAll(ctx)
        } else {
            toast = "没有通知权限，提醒无法显示。可以点下面按钮去系统设置开启。"
        }
    }
    fun resched() = Reminders.scheduleAll(ctx)
    val master = prefs.remOn.value

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(shape = RoundedCornerShape(16.dp)) {
            SwitchRow("开启提醒", "总开关：关掉后所有提醒都不会出现", master) { on ->
                if (on && Build.VERSION.SDK_INT >= 33 && !Reminders.hasPermission(ctx)) {
                    permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    prefs.remOn.value = on
                    resched()
                }
            }
        }
        if (master && !permOk) {
            Text("⚠ 通知权限没开，提醒不会显示", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            OutlinedButton(onClick = { Reminders.openNotificationSettings(ctx) }, modifier = Modifier.fillMaxWidth()) {
                Text("去系统设置开启通知")
            }
        }
        if (toast.isNotEmpty()) Text(toast, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)

        SectionTitle("每日问候与记账提醒")
        Card(shape = RoundedCornerShape(16.dp)) {
            for ((i, s) in SLOTS.withIndex()) {
                if (i > 0) HorizontalDivider()
                val on = prefs.slotOn.getValue(s.key)
                val h = prefs.slotH.getValue(s.key)
                val m = prefs.slotM.getValue(s.key)
                Row(
                    Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.title, color = if (master) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    pickTime(ctx, h.value, m.value) { hh, mm ->
                                        h.value = hh
                                        m.value = mm
                                        resched()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 0.dp),
                            ) { Text("⏰ " + hm(h.value, m.value), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    if (!Reminders.addPhoneAlarm(ctx, h.value, m.value, "存钱记录 · " + s.title)) {
                                        toast = "这台手机的时钟 app 不支持自动添加闹钟"
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) { Text("＋手机闹钟", fontSize = 12.sp) }
                        }
                    }
                    Switch(
                        checked = on.value,
                        enabled = master,
                        onCheckedChange = {
                            on.value = it
                            resched()
                        },
                    )
                }
            }
            HorizontalDivider()
            SwitchRow("今天记过账就不再提醒记账", "午餐、晚餐提醒会自动跳过", prefs.skipIfRecorded.value, enabled = master) {
                prefs.skipIfRecorded.value = it
            }
        }

        SectionTitle("还款 / 存款日提醒")
        Card(shape = RoundedCornerShape(16.dp)) {
            SwitchRow("开启还款日提醒", "在目标/欠款里设置「每月几号」就会提醒", prefs.dueOn.value, enabled = master) {
                prefs.dueOn.value = it
                resched()
            }
            HorizontalDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        pickTime(ctx, prefs.dueH.value, prefs.dueM.value) { hh, mm ->
                            prefs.dueH.value = hh
                            prefs.dueM.value = mm
                            resched()
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("提醒时间", modifier = Modifier.weight(1f))
                Text("⏰ " + hm(prefs.dueH.value, prefs.dueM.value), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("提醒方式")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = prefs.dueStyle.value == 0,
                        onClick = { prefs.dueStyle.value = 0 },
                        label = { Text("🔔 通知") },
                    )
                    FilterChip(
                        selected = prefs.dueStyle.value == 1,
                        onClick = { prefs.dueStyle.value = 1 },
                        label = { Text("⏰ 闹钟式响铃") },
                    )
                }
                Text(
                    if (prefs.dueStyle.value == 1) "会用闹钟铃声一直响，直到你点开通知" else "普通通知，有提示音",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            SwitchRow(
                "同时发 Email 提醒",
                if (syncUrl.isBlank()) "需要先连接 Google Sheet 同步" else "由你的 Google 脚本每天 9 点检查，发到你的 Gmail",
                prefs.emailOn.value && syncUrl.isNotBlank(),
                enabled = syncUrl.isNotBlank(),
            ) { prefs.emailOn.value = it }
        }
        val dueGoals = goals.filter { it.dueDay > 0 }
        if (dueGoals.isEmpty()) {
            Text(
                "还没有设置还款日。到主页「目标与欠款」，编辑一个欠款，填「每月几号」即可。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Card(shape = RoundedCornerShape(16.dp)) {
                for ((i, g) in dueGoals.withIndex()) {
                    if (i > 0) HorizontalDivider()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(g.name, fontWeight = FontWeight.Bold)
                        Text(
                            (dueLine(g) ?: "") + if (g.remindBefore > 0) " · 提前 ${g.remindBefore} 天提醒" else " · 当天提醒",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row {
                            TextButton(
                                onClick = {
                                    if (!Reminders.addToCalendar(ctx, g, prefs.dueH.value, prefs.dueM.value)) {
                                        toast = "没找到日历 app"
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 0.dp),
                            ) { Text("📅 加入手机日历（每月重复）", fontSize = 13.sp) }
                            Spacer(Modifier.width(12.dp))
                            TextButton(onClick = { onEditGoal(g) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                                Text("修改", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        SectionTitle("订阅续费提醒")
        Card(shape = RoundedCornerShape(16.dp)) {
            SwitchRow("开启订阅续费提醒", "扣款前提醒你，想取消的会提醒你去取消", prefs.subOn.value, enabled = master) {
                prefs.subOn.value = it
                resched()
            }
            HorizontalDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        pickTime(ctx, prefs.subH.value, prefs.subM.value) { hh, mm ->
                            prefs.subH.value = hh
                            prefs.subM.value = mm
                            resched()
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("提醒时间", modifier = Modifier.weight(1f))
                Text("⏰ " + hm(prefs.subH.value, prefs.subM.value), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("提醒方式")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = prefs.subStyle.value == 0, onClick = { prefs.subStyle.value = 0 }, label = { Text("🔔 通知") })
                    FilterChip(selected = prefs.subStyle.value == 1, onClick = { prefs.subStyle.value = 1 }, label = { Text("⏰ 闹钟式响铃") })
                }
            }
            HorizontalDivider()
            SwitchRow(
                "同时发 Email 提醒",
                if (syncUrl.isBlank()) "需要先连接 Google Sheet 同步" else "由你的 Google 脚本每天 9 点检查，发到你的 Gmail",
                prefs.subEmail.value && syncUrl.isNotBlank(),
                enabled = syncUrl.isNotBlank(),
            ) { prefs.subEmail.value = it }
        }
        val activeSubs = subs.filter { it.active }
        if (activeSubs.isEmpty()) {
            Text("还没有订阅。到主页「订阅支出」新增。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Card(shape = RoundedCornerShape(16.dp)) {
                for ((i, x) in activeSubs.withIndex()) {
                    if (i > 0) HorizontalDivider()
                    val next = nextBilling(x)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(x.name + if (x.wantCancel) " ✂️" else "", fontWeight = FontWeight.Medium)
                            Text(
                                (if (next != null) "下次 ${dateCn(next)} · " else "") +
                                    if (x.remindBefore > 0) "提前 ${x.remindBefore} 天提醒" else "当天提醒",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = {
                            if (!Reminders.addSubToCalendar(ctx, x, prefs.subH.value, prefs.subM.value)) toast = "没找到日历 app"
                        }) { Text("📅", fontSize = 16.sp) }
                        TextButton(onClick = { onEditSub(x) }) { Text("修改", fontSize = 13.sp) }
                    }
                }
            }
        }

        SectionTitle("其他")
        OutlinedButton(
            onClick = {
                if (!Reminders.hasPermission(ctx)) {
                    toast = "请先开启通知权限"
                } else {
                    Reminders.test(ctx)
                    toast = "已发送测试通知，看看通知栏"
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("发送测试通知") }
        OutlinedButton(onClick = { Reminders.openAppSettings(ctx) }, modifier = Modifier.fillMaxWidth()) {
            Text("提醒不准时？打开系统设置")
        }
        Text(
            "小米 / 红米 / OPPO / vivo 等手机：请在系统设置里给「存钱记录」开启「自启动」，省电策略选「无限制」，否则提醒可能被系统拦截。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CurrencyPage(onCurrency: (String) -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("结算货币")
        Card(shape = RoundedCornerShape(16.dp)) {
            for ((i, c) in CURRENCIES.withIndex()) {
                if (i > 0) HorizontalDivider()
                OptionRow(
                    c.third, "金额显示为：" + c.second + " 1,234.00", Cur.code == c.first,
                ) { onCurrency(c.first) }
            }
        }
        Text(
            "总存款、每月统计、目标和欠款进度都按结算货币计算。每笔记录可以用不同货币记，会按下面的汇率自动换算。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionTitle("汇率（1 外币 = 多少 ${Cur.code}）")
        Card(shape = RoundedCornerShape(16.dp)) {
            val others = CURRENCIES.filter { it.first != Cur.code }
            for ((i, c) in others.withIndex()) {
                if (i > 0) HorizontalDivider()
                var text by remember(Cur.code, c.first, Fx.updatedAt) {
                    mutableStateOf(String.format(Locale.US, "%.4f", Fx.perBase(c.first)).trimEnd('0').trimEnd('.'))
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("1 ${c.first} =", modifier = Modifier.width(80.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v ->
                            text = v.filter { it.isDigit() || it == '.' }
                            text.toDoubleOrNull()?.let { if (it > 0) Fx.setPerBase(store, c.first, it) }
                        },
                        singleLine = true,
                        suffix = { Text(Cur.code) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        OutlinedButton(
            enabled = !loading,
            onClick = {
                loading = true
                msg = "更新中…"
                scope.launch {
                    msg = try {
                        val (myr, usd) = withContext(Dispatchers.IO) { fetchRates() }
                        val time = java.time.LocalDateTime.now().withSecond(0).withNano(0).toString().replace('T', ' ')
                        Fx.saveSgdRates(store, myr, usd, time)
                        "✅ 已更新（$time）"
                    } catch (e: Exception) {
                        "❌ 更新失败，请检查网络，或手动填写汇率"
                    }
                    loading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("🌐 在线更新汇率") }
        if (msg.isNotEmpty()) Text(msg, fontSize = 13.sp)
        if (Fx.updatedAt.isNotEmpty()) {
            Text("上次更新：${Fx.updatedAt}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Returns (SGD per 1 MYR, SGD per 1 USD). */
fun fetchRates(): Pair<Double, Double> {
    val conn = java.net.URL("https://open.er-api.com/v6/latest/SGD").openConnection() as java.net.HttpURLConnection
    conn.connectTimeout = 15000
    conn.readTimeout = 15000
    val text = conn.inputStream.bufferedReader().use { it.readText() }
    conn.disconnect()
    val rates = org.json.JSONObject(text).getJSONObject("rates")
    val myrPerSgd = rates.getDouble("MYR")
    val usdPerSgd = rates.getDouble("USD")
    return (1.0 / myrPerSgd) to (1.0 / usdPerSgd)
}

@Composable
fun PopupPage(prefs: AppPrefs) {
    Card(shape = RoundedCornerShape(16.dp)) {
        SwitchRow("打开 app 时显示今日提醒", null, prefs.popupOn.value) { prefs.popupOn.value = it }
    }
    Text(
        "开启后，每次打开 app 会先弹出一个提醒页面：\n· 按时间问候（早上好 / 晚上好…）\n· 今天记了几笔、存入取出多少\n· 最近 7 天内的还款日\n· 本月各目标还差多少\n可以直接点「记一笔」开始记账。",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
fun SyncPage(syncUrl: String, sheetUrl: String, onSyncUrl: (String) -> Unit, onSheetFound: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(syncUrl) }
    var sheet by remember { mutableStateOf(sheetUrl) }
    var msg by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (syncUrl.isBlank()) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("第一次设置（约 3 分钟，只做一次）", fontWeight = FontWeight.Bold)
                    GuideStep("1", "复制脚本代码") {
                        OutlinedButton(onClick = {
                            val code = try {
                                ctx.assets.open("Code.gs").bufferedReader().use { it.readText() }
                            } catch (e: Exception) {
                                ""
                            }
                            val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                            if (code.isNotEmpty() && cm != null) {
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("Code.gs", code))
                                copied = "✅ 已复制（${code.length} 字）"
                            } else {
                                copied = "❌ 复制失败"
                            }
                        }) { Text("📋 复制脚本代码") }
                        if (copied.isNotEmpty()) Text(copied, fontSize = 12.sp)
                    }
                    GuideStep("2", "打开 Google 脚本，新建项目") {
                        OutlinedButton(onClick = { openUrl(ctx, "https://script.google.com/home/projects/create") }) {
                            Text("🌐 打开 script.google.com")
                        }
                        Text(
                            "用你的 Google 账号登录。手机上建议在 Chrome 里点右上角「⋮ → 桌面版网站」。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    GuideStep("3", "全选删掉原来的代码，粘贴，保存 💾") {}
                    GuideStep("4", "上方函数选「setup」，点「运行」") {
                        Text(
                            "会要求授权：选你的账号 →「高级」→「前往（不安全）」→「允许」。这是你自己的脚本，只能访问你自己的表格。运行完会自动建好「存钱记录」表格。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    GuideStep("5", "部署 → 新建部署 → 类型选「Web 应用」") {
                        Text(
                            "执行身份：我　·　谁可以访问：任何人\n点「部署」，复制出现的网址（https://script.google.com/macros/s/…/exec）。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    GuideStep("6", "把网址贴到下面，点「连接并保存」") {}
                }
            }
        }
        OutlinedTextField(
            value = url,
            onValueChange = { url = it.trim(); msg = "" },
            label = { Text("脚本网址（https://script.google.com/…）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = url.isNotBlank() && !testing,
                onClick = {
                    testing = true
                    msg = "连接中…"
                    val u = url
                    scope.launch {
                        msg = try {
                            val res = withContext(Dispatchers.IO) { Sync.ping(u) }
                            val s = res.optString("sheet", "")
                            if (s.isNotEmpty()) {
                                sheet = s
                                onSheetFound(s)
                            }
                            onSyncUrl(u)
                            "✅ 连接成功，已保存。记录会自动上传。"
                        } catch (e: Exception) {
                            "❌ " + (e.message ?: "连接失败")
                        }
                        testing = false
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("连接并保存")
            }
            OutlinedButton(
                enabled = sheet.isNotEmpty(),
                onClick = { openUrl(ctx, sheet) },
                modifier = Modifier.weight(1f),
            ) { Text("打开表格") }
        }
        if (msg.isNotEmpty()) Text(msg, fontSize = 13.sp)
        if (syncUrl.isNotBlank()) {
            TextButton(onClick = {
                url = ""
                onSyncUrl("")
                msg = "已断开同步"
            }) { Text("断开同步", color = MaterialTheme.colorScheme.error) }
        }
        Text(
            "表格列：日期 | 进出 | 金额 | 货币 | 结算金额 | 分类 | 照片 | 备注 | 目标。照片直接显示在格子里。另有「目标」「订阅」两页。\n\n以后 app 更新了脚本：重新复制代码贴上 → 部署 → 管理部署 → 编辑 → 版本选「新版本」→ 部署，网址不变。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun GuideStep(num: String, title: String, content: @Composable () -> Unit) {
    Row {
        Text(
            num,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            content()
        }
    }
}

@Composable
fun DataPage(entries: List<Entry>, onClearAll: () -> Unit) {
    val ctx = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = {
                val csv = buildString {
                    append("日期,类型,金额,货币,结算金额(${Cur.code}),分类,备注\n")
                    for (e in entries.sortedWith(compareBy({ it.date }, { it.id }))) {
                        append(e.date.toString()).append(',')
                        append(if (e.amount >= 0) "存入" else "取出").append(',')
                        append(plainNumber(kotlin.math.abs(e.amount))).append(',')
                        append(e.currency).append(',')
                        append(String.format(java.util.Locale.US, "%.2f", kotlin.math.abs(e.base))).append(',')
                        append(e.tag.replace(",", "，")).append(',')
                        append(e.note.replace(",", "，").replace("\n", " ")).append('\n')
                    }
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "存钱记录导出")
                    putExtra(Intent.EXTRA_TEXT, csv)
                }
                ctx.startActivity(Intent.createChooser(send, "导出记录"))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("导出全部记录（${entries.size} 条，CSV）") }
        if (confirmClear) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("清空 app 里的全部记录？无法恢复（表格不受影响）", modifier = Modifier.weight(1f), fontSize = 14.sp)
                TextButton(onClick = {
                    onClearAll()
                    confirmClear = false
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            }
        } else {
            TextButton(onClick = { confirmClear = true }) {
                Text("清空全部记录", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Popup shown when the app opens. */
@Composable
fun WelcomeDialog(entries: List<Entry>, goals: List<Goal>, subs: List<Sub>, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val today = java.time.LocalDate.now()
    val todayEntries = entries.filter { it.date == today }
    val total = entries.sumOf { it.base }
    val upcoming = goals.mapNotNull { g ->
        val d = nextDueDate(g) ?: return@mapNotNull null
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, d)
        if (days <= 7) Triple(g, d, days) else null
    }.sortedBy { it.third }
    val plans = goals.filter { it.monthly > 0 }.mapNotNull { g ->
        val s = calcGoal(g, entries, total)
        if (s.remaining <= 0) null else g to (g.monthly - s.thisMonth)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(greeting(), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (todayEntries.isEmpty()) {
                    Text("今天还没有记账 ✏️")
                } else {
                    val inSum = todayEntries.filter { it.amount > 0 && !it.isTransfer }.sumOf { it.base }
                    val outSum = -todayEntries.filter { it.amount < 0 && !it.isTransfer }.sumOf { it.base }
                    Text("今天记了 ${todayEntries.size} 笔：存入 ${money(inSum)} · 取出 ${money(outSum)}")
                }
                Text("总存款 ${(if (total < 0) "-" else "") + money(total)}", fontWeight = FontWeight.Bold)
                if (upcoming.isNotEmpty()) {
                    HorizontalDivider()
                    Text("📅 近期还款 / 存款日", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    for ((g, d, days) in upcoming) {
                        val w = when (days) {
                            0L -> "今天"
                            1L -> "明天"
                            else -> "$days 天后"
                        }
                        Text("· ${g.name}：${d.monthValue}月${d.dayOfMonth}日（$w）", fontSize = 14.sp)
                    }
                }
                val renewals = subs.filter { it.active }.mapNotNull { x ->
                    val d = nextBilling(x) ?: return@mapNotNull null
                    val days = java.time.temporal.ChronoUnit.DAYS.between(today, d)
                    if (days <= 7) Triple(x, d, days) else null
                }.sortedBy { it.third }
                if (renewals.isNotEmpty()) {
                    HorizontalDivider()
                    Text("🔁 7 天内续费的订阅", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    for ((x, d, days) in renewals) {
                        Text(
                            "· ${x.name} ${moneyC(x.amount, x.currency)}：${dateCn(d)}（${daysWord(days)}）" +
                                if (x.wantCancel) " ✂️ 记得取消" else "",
                            fontSize = 14.sp,
                        )
                    }
                }
                if (plans.isNotEmpty()) {
                    HorizontalDivider()
                    Text("🎯 本月计划", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    for ((g, left) in plans) {
                        val v = if (g.debt) "还" else "存"
                        Text(
                            if (left > 0) "· ${g.name}：本月还需$v ${money(left)}" else "· ${g.name}：本月已完成 ✅",
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onAdd) { Text("记一笔") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}
