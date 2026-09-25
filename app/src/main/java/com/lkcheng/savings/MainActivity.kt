package com.lkcheng.savings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

private val GREEN = Color(0xFF16A34A)
private val RED = Color(0xFFDC2626)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crash = File(filesDir, "crash.txt")
        if (crash.exists()) {
            showCrash(crash)
            return
        }
        enableEdgeToEdge()
        val store = Store(applicationContext)
        setContent {
            var theme by remember { mutableIntStateOf(store.theme) }
            val dark = theme == 1
            LaunchedEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t),
                )
            }
            MaterialTheme(colorScheme = schemeFor(theme)) {
                SavingsApp(store, theme) { t ->
                    theme = t
                    store.theme = t
                }
            }
        }
    }

    private fun showCrash(file: File) {
        val text = try { file.readText() } catch (e: Exception) { "无法读取错误信息" }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
            setBackgroundColor(android.graphics.Color.WHITE)
        }
        val title = android.widget.TextView(this).apply {
            this.text = "App 上次闪退了，请把这页截图发给 Claude"
            textSize = 18f
            setTextColor(android.graphics.Color.RED)
        }
        val retry = android.widget.Button(this).apply {
            this.text = "清除并重新打开"
            setOnClickListener {
                file.delete()
                recreate()
            }
        }
        val body = android.widget.TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(android.graphics.Color.BLACK)
            setTextIsSelectable(true)
        }
        val scroll = android.widget.ScrollView(this).apply { addView(body) }
        layout.addView(title)
        layout.addView(retry)
        layout.addView(scroll)
        setContentView(layout)
    }
}

val THEME_NAMES = listOf("白蓝", "深夜", "暖金")
val THEME_SWATCH = listOf(Color(0xFF6610F2), Color(0xFF1F2937), Color(0xFFD97706))

fun schemeFor(theme: Int) = when (theme) {
    1 -> darkColorScheme(
        primary = Color(0xFF8AB4FF),
        onPrimary = Color(0xFF0B1B3A),
        primaryContainer = Color(0xFF1E2A44),
        onPrimaryContainer = Color(0xFFDCE6FF),
        background = Color(0xFF0E1117),
        surface = Color(0xFF0E1117),
        surfaceContainerHighest = Color(0xFF1A1F29),
        surfaceContainerHigh = Color(0xFF1F2530),
    )
    2 -> lightColorScheme(
        primary = Color(0xFFB45309),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFDEBC8),
        onPrimaryContainer = Color(0xFF6B2E05),
        background = Color(0xFFFFFAF0),
        surface = Color(0xFFFFFAF0),
        surfaceContainerHighest = Color(0xFFF7EEDD),
        surfaceContainerHigh = Color(0xFFFFF4E0),
    )
    else -> lightColorScheme(
        primary = Color(0xFF6610F2),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEDE3FF),
        onPrimaryContainer = Color(0xFF32077A),
        background = Color.White,
        surface = Color.White,
        surfaceContainerHighest = Color(0xFFF4F1FC),
        surfaceContainerHigh = Color(0xFFFAF8FF),
    )
}

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        // no browser
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsApp(store: Store, theme: Int, onThemeChange: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries = remember { mutableStateListOf<Entry>().apply { addAll(store.loadEntries()) } }
    var goal by remember { mutableDoubleStateOf(store.goal) }
    var greenIncome by remember { mutableStateOf(store.greenForIncome) }
    var syncUrl by remember { mutableStateOf(store.syncUrl) }
    var sheetUrl by remember { mutableStateOf(store.sheetUrl) }
    var syncing by remember { mutableStateOf(false) }
    var syncMsg by remember { mutableStateOf("") }
    var monthStr by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selStr by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    val month = YearMonth.parse(monthStr)
    val selected = LocalDate.parse(selStr)
    val inc = if (greenIncome) GREEN else RED
    val exp = if (greenIncome) RED else GREEN

    fun persist() = store.saveEntries(entries.toList())

    fun syncNow() {
        val url = syncUrl
        if (syncing || url.isBlank()) return
        syncing = true
        syncMsg = "同步中…"
        scope.launch {
            var err: String? = null
            try {
                for (id in store.pendingDeletes()) {
                    withContext(Dispatchers.IO) { Sync.delete(url, id) }
                    store.removePendingDelete(id)
                }
                for (e in entries.filter { !it.synced }.toList()) {
                    withContext(Dispatchers.IO) { Sync.upsert(context, url, e) }
                    val i = entries.indexOfFirst { it.id == e.id }
                    if (i >= 0 && entries[i] == e) {
                        entries[i] = e.copy(synced = true, photoSent = e.photo.isNotEmpty())
                        persist()
                    }
                }
            } catch (ex: Exception) {
                err = ex.message ?: ex.toString()
            }
            syncMsg = if (err == null) "已同步到 Google Sheet" else "同步失败：$err"
            syncing = false
            // edits made while syncing get picked up
            if (err == null && entries.any { !it.synced }) syncNow()
        }
    }

    LaunchedEffect(Unit) { syncNow() }

    fun goToMonth(m: YearMonth) {
        monthStr = m.toString()
        val now = LocalDate.now()
        selStr = (if (YearMonth.from(now) == m) now else m.atDay(1)).toString()
    }

    val total = entries.sumOf { it.amount }
    val monthEntries = entries.filter { YearMonth.from(it.date) == month }
    val monthIn = monthEntries.filter { it.amount > 0 }.sumOf { it.amount }
    val monthOut = -monthEntries.filter { it.amount < 0 }.sumOf { it.amount }
    val daily = monthEntries.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.amount } }
    val dayEntries = entries.filter { it.date == selected }.sortedByDescending { it.id }
    val dayNet = dayEntries.sumOf { it.amount }
    val unsynced = entries.count { !it.synced } + store.pendingDeletes().size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("存钱记录", fontWeight = FontWeight.Bold) },
                actions = {
                    if (syncUrl.isNotBlank()) {
                        if (syncing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(onClick = { syncNow() }) {
                                Text(
                                    if (unsynced > 0) "☁ 待同步 $unsynced" else "☁ 已同步",
                                    color = if (syncMsg.startsWith("同步失败")) exp else MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("记一笔") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (syncMsg.startsWith("同步失败")) {
                item {
                    Text(
                        syncMsg + "（点右上角重试）",
                        color = exp,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item {
                SummaryCard(total, goal, month, monthIn, monthOut, inc, exp)
            }
            item {
                MonthHeader(
                    month = month,
                    onPrev = { goToMonth(month.minusMonths(1)) },
                    onNext = { goToMonth(month.plusMonths(1)) },
                    onToday = { goToMonth(YearMonth.now()) },
                )
            }
            item {
                CalendarGrid(month, selected, daily, inc, exp) { selStr = it.toString() }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${selected.monthValue}月${selected.dayOfMonth}日 ${weekdayCn(selected)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (dayEntries.isNotEmpty()) {
                        Text(
                            signedMoney(dayNet),
                            color = if (dayNet >= 0) inc else exp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            if (dayEntries.isEmpty()) {
                item {
                    Text(
                        "这天还没有记录，点右下角「记一笔」添加",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(dayEntries, key = { it.id }) { e ->
                EntryRow(e, inc, exp, showSync = syncUrl.isNotBlank()) { editing = e }
            }
        }
    }

    if (showAdd) {
        EntryDialog(
            initial = null,
            defaultDate = selected,
            inc = inc,
            exp = exp,
            onDismiss = { showAdd = false },
            onSave = { e ->
                entries.add(e)
                persist()
                showAdd = false
                syncNow()
            },
            onDelete = {},
        )
    }

    val current = editing
    if (current != null) {
        EntryDialog(
            initial = current,
            defaultDate = current.date,
            inc = inc,
            exp = exp,
            onDismiss = { editing = null },
            onSave = { e ->
                val i = entries.indexOfFirst { it.id == e.id }
                if (i >= 0) {
                    val old = entries[i]
                    if (old.photo.isNotEmpty() && old.photo != e.photo) Photos.delete(context, old.photo)
                    entries[i] = e
                }
                persist()
                editing = null
                syncNow()
            },
            onDelete = { e ->
                entries.removeAll { it.id == e.id }
                Photos.delete(context, e.photo)
                persist()
                if (syncUrl.isNotBlank()) store.addPendingDelete(e.id)
                editing = null
                syncNow()
            },
        )
    }

    if (showSettings) {
        SettingsDialog(
            goal = goal,
            greenIncome = greenIncome,
            syncUrl = syncUrl,
            sheetUrl = sheetUrl,
            theme = theme,
            entries = entries.toList(),
            onDismiss = { showSettings = false },
            onSheetFound = { u ->
                sheetUrl = u
                store.sheetUrl = u
            },
            onSave = { g, green, url, t ->
                onThemeChange(t)
                goal = g
                greenIncome = green
                store.goal = g
                store.greenForIncome = green
                if (url != syncUrl) {
                    syncUrl = url
                    store.syncUrl = url
                    // new sheet: upload everything again
                    for (i in entries.indices) entries[i] = entries[i].copy(synced = false, photoSent = false)
                    persist()
                    syncMsg = ""
                }
                showSettings = false
                syncNow()
            },
            onClearAll = {
                entries.forEach { Photos.delete(context, it.photo) }
                entries.clear()
                persist()
                showSettings = false
            },
        )
    }
}

@Composable
fun SummaryCard(
    total: Double,
    goal: Double,
    month: YearMonth,
    monthIn: Double,
    monthOut: Double,
    inc: Color,
    exp: Color,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text("总存款", style = MaterialTheme.typography.labelLarge)
            Text(
                (if (total < 0) "-" else "") + money(total),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = if (total < 0) exp else MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (goal > 0) {
                val p = (total / goal).coerceIn(0.0, 1.0)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { p.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = inc,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(6.dp))
                val remain = goal - total
                Text(
                    "目标 ${money(goal)} · 已完成 ${(p * 100).toInt()}%" +
                        if (remain > 0) " · 还差 ${money(remain)}" else " · 已达成 🎉",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("${month.monthValue}月存入", "+" + money(monthIn), inc, Modifier.weight(1f))
                Stat("${month.monthValue}月取出", "-" + money(monthOut), exp, Modifier.weight(1f))
                val net = monthIn - monthOut
                Stat("本月净额", signedMoney(net), if (net >= 0) inc else exp, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(
            value,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
        }
        Text(
            "${month.year}年${month.monthValue}月",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        if (month != YearMonth.now()) {
            TextButton(onClick = onToday) { Text("回到本月") }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
        }
    }
}

@Composable
fun CalendarGrid(
    month: YearMonth,
    selected: LocalDate,
    daily: Map<LocalDate, Double>,
    inc: Color,
    exp: Color,
    onSelect: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val offset = month.atDay(1).dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val rows = (offset + days + 6) / 7
    val shape = RoundedCornerShape(10.dp)

    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                for (h in weekHeaders()) {
                    Text(
                        h,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val d = r * 7 + c - offset + 1
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(0.82f)
                                .padding(2.dp)
                        ) {
                            if (d in 1..days) {
                                val date = month.atDay(d)
                                val net = daily[date]
                                val isSel = date == selected
                                val isToday = date == today
                                val bg = when {
                                    isSel -> MaterialTheme.colorScheme.primary
                                    net != null && net > 0 -> inc.copy(alpha = 0.14f)
                                    net != null && net < 0 -> exp.copy(alpha = 0.14f)
                                    else -> Color.Transparent
                                }
                                var m = Modifier
                                    .fillMaxSize()
                                    .clip(shape)
                                    .background(bg)
                                if (isToday && !isSel) {
                                    m = m.border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
                                }
                                Column(
                                    modifier = m.clickable { onSelect(date) },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        "$d",
                                        fontSize = 15.sp,
                                        fontWeight = if (isToday || isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (net != null && net != 0.0) {
                                        Text(
                                            compact(net),
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) Color.White else if (net > 0) inc else exp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoThumb(name: String, sizeDp: Int, sample: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bmp = remember(name, sample) { Photos.load(context, name, sample)?.asImageBitmap() }
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = "照片",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
    }
}

@Composable
fun EntryRow(e: Entry, inc: Color, exp: Color, showSync: Boolean, onClick: () -> Unit) {
    val isIn = e.amount >= 0
    val color = if (isIn) inc else exp
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (e.photo.isNotEmpty()) {
                PhotoThumb(e.photo, 44, 8)
            } else {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (isIn) "＋" else "－", color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    e.note.ifBlank { if (isIn) "存入" else "取出" },
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val parts = mutableListOf(if (isIn) "存入" else "取出")
                if (e.photo.isNotEmpty()) parts.add("📷")
                if (showSync) parts.add(if (e.synced) "☁ 已同步" else "☁ 待同步")
                Text(
                    parts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(signedMoney(e.amount), color = color, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}

@Composable
fun TypeButton(text: String, active: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    if (active) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        ) { Text(text, fontWeight = FontWeight.Bold) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(text, color = color)
        }
    }
}

@Composable
fun PhotoViewer(name: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val bmp = remember(name) { Photos.load(context, name, 1)?.asImageBitmap() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = "照片",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                "点任意处关闭",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp),
            )
        }
    }
}

@Composable
fun EntryDialog(
    initial: Entry?,
    defaultDate: LocalDate,
    inc: Color,
    exp: Color,
    onDismiss: () -> Unit,
    onSave: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
) {
    val context = LocalContext.current
    var isIn by remember { mutableStateOf(initial?.let { it.amount >= 0 } ?: true) }
    var amountText by remember { mutableStateOf(initial?.let { plainNumber(kotlin.math.abs(it.amount)) } ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var photo by remember { mutableStateOf(initial?.photo ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewPhoto by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf(false) }
    val amount = amountText.toDoubleOrNull()
    val valid = amount != null && amount > 0

    val camFile = remember { File(context.cacheDir, "camera.jpg") }
    val camUri = remember { FileProvider.getUriForFile(context, context.packageName + ".files", camFile) }
    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            val name = Photos.import(context, camUri)
            if (name != null) photo = name else photoError = true
        }
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val name = Photos.import(context, uri)
            if (name != null) photo = name else photoError = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "记一笔" else "编辑记录") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "${defaultDate.year}年${defaultDate.monthValue}月${defaultDate.dayOfMonth}日 ${weekdayCn(defaultDate)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeButton("＋ 存入", isIn, inc, Modifier.weight(1f)) { isIn = true }
                    TypeButton("－ 取出", !isIn, exp, Modifier.weight(1f)) { isIn = false }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { s -> amountText = s.filter { it.isDigit() || it == '.' } },
                    label = { Text("金额") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (q in listOf(10, 50, 100, 500)) {
                        OutlinedButton(
                            onClick = { amountText = plainNumber((amountText.toDoubleOrNull() ?: 0.0) + q) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp),
                        ) { Text("+$q", fontSize = 12.sp) }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (photo.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PhotoThumb(photo, 72, 4, Modifier.clickable { viewPhoto = true })
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            TextButton(onClick = { viewPhoto = true }) { Text("查看大图") }
                            TextButton(onClick = { photo = "" }) {
                                Text("移除照片", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                photoError = false
                                try {
                                    camLauncher.launch(camUri)
                                } catch (e: Exception) {
                                    photoError = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("📷 拍照") }
                        OutlinedButton(
                            onClick = {
                                photoError = false
                                pickLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("🖼 相册") }
                    }
                }
                if (photoError) {
                    Text("照片读取失败，请再试一次", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                if (initial != null) {
                    HorizontalDivider()
                    if (confirmDelete) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("确定删除？", modifier = Modifier.weight(1f))
                            TextButton(onClick = { confirmDelete = false }) { Text("不删") }
                            TextButton(onClick = { onDelete(initial) }) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("删除这条记录", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val a = amountText.toDoubleOrNull()
                    if (a != null && a > 0) {
                        val samePhoto = initial != null && initial.photo == photo
                        onSave(
                            Entry(
                                id = initial?.id ?: System.currentTimeMillis(),
                                date = defaultDate,
                                amount = if (isIn) a else -a,
                                note = note.trim(),
                                photo = photo,
                                synced = false,
                                photoSent = samePhoto && initial?.photoSent == true,
                            )
                        )
                    }
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )

    if (viewPhoto && photo.isNotEmpty()) {
        PhotoViewer(photo) { viewPhoto = false }
    }
}

@Composable
fun SettingsDialog(
    goal: Double,
    greenIncome: Boolean,
    syncUrl: String,
    sheetUrl: String,
    theme: Int,
    entries: List<Entry>,
    onDismiss: () -> Unit,
    onSheetFound: (String) -> Unit,
    onSave: (Double, Boolean, String, Int) -> Unit,
    onClearAll: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var goalText by remember { mutableStateOf(if (goal > 0) plainNumber(goal) else "") }
    var green by remember { mutableStateOf(greenIncome) }
    var themeSel by remember { mutableIntStateOf(theme) }
    var url by remember { mutableStateOf(syncUrl) }
    var sheet by remember { mutableStateOf(sheetUrl) }
    var testMsg by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("主题", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in THEME_NAMES.indices) {
                        val sel = themeSel == i
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    if (sel) 2.dp else 1.dp,
                                    if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { themeSel = i }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(THEME_SWATCH[i])
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                THEME_NAMES[i],
                                fontSize = 13.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = goalText,
                    onValueChange = { s -> goalText = s.filter { it.isDigit() || it == '.' } },
                    label = { Text("存钱目标（留空=不设）") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("颜色习惯")
                        Text(
                            if (green) "存入=绿色，取出=红色" else "存入=红色，取出=绿色",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = green, onCheckedChange = { green = it })
                }
                HorizontalDivider()
                Text("Google Sheet 同步", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.trim(); testMsg = "" },
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
                            testMsg = "连接中…"
                            val u = url
                            scope.launch {
                                testMsg = try {
                                    val res = withContext(Dispatchers.IO) { Sync.ping(u) }
                                    val s = res.optString("sheet", "")
                                    if (s.isNotEmpty()) {
                                        sheet = s
                                        onSheetFound(s)
                                    }
                                    "✅ 连接成功"
                                } catch (e: Exception) {
                                    "❌ " + (e.message ?: "连接失败")
                                }
                                testing = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("测试连接") }
                    OutlinedButton(
                        enabled = sheet.isNotEmpty(),
                        onClick = { openUrl(context, sheet) },
                        modifier = Modifier.weight(1f),
                    ) { Text("打开表格") }
                }
                if (testMsg.isNotEmpty()) Text(testMsg, fontSize = 13.sp)
                Text(
                    "保存后会自动把全部记录（含照片）上传到表格，之后每记一笔都会自动同步。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                OutlinedButton(
                    onClick = {
                        val csv = buildString {
                            append("日期,类型,金额,备注\n")
                            for (e in entries.sortedWith(compareBy({ it.date }, { it.id }))) {
                                append(e.date.toString()).append(',')
                                append(if (e.amount >= 0) "存入" else "取出").append(',')
                                append(plainNumber(kotlin.math.abs(e.amount))).append(',')
                                append(e.note.replace(",", "，").replace("\n", " ")).append('\n')
                            }
                        }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "存钱记录导出")
                            putExtra(Intent.EXTRA_TEXT, csv)
                        }
                        context.startActivity(Intent.createChooser(send, "导出记录"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("导出全部记录（${entries.size} 条）") }
                if (confirmClear) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("清空 app 里的全部记录？无法恢复（表格不受影响）", modifier = Modifier.weight(1f))
                        TextButton(onClick = onClearAll) {
                            Text("清空", color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    TextButton(onClick = { confirmClear = true }) {
                        Text("清空全部记录", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(goalText.toDoubleOrNull() ?: 0.0, green, url.trim(), themeSel) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
