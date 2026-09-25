package com.lkcheng.savings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

private val GREEN = Color(0xFF16A34A)
private val RED = Color(0xFFDC2626)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crash = java.io.File(filesDir, "crash.txt")
        if (crash.exists()) {
            showCrash(crash)
            return
        }
        enableEdgeToEdge()
        val store = Store(applicationContext)
        setContent {
            val dark = isSystemInDarkTheme()
            val scheme = if (dark) {
                darkColorScheme(primary = Color(0xFF60A5FA))
            } else {
                lightColorScheme(
                    primary = Color(0xFF2563EB),
                    primaryContainer = Color(0xFFDBEAFE),
                    onPrimaryContainer = Color(0xFF1E3A8A),
                )
            }
            MaterialTheme(colorScheme = scheme) {
                SavingsApp(store)
            }
        }
    }

    private fun showCrash(file: java.io.File) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsApp(store: Store) {
    val entries = remember { mutableStateListOf<Entry>().apply { addAll(store.loadEntries()) } }
    var goal by remember { mutableDoubleStateOf(store.goal) }
    var greenIncome by remember { mutableStateOf(store.greenForIncome) }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("存钱记录", fontWeight = FontWeight.Bold) },
                actions = {
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
                EntryRow(e, inc, exp) { editing = e }
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
                if (i >= 0) entries[i] = e
                persist()
                editing = null
            },
            onDelete = { e ->
                entries.removeAll { it.id == e.id }
                persist()
                editing = null
            },
        )
    }

    if (showSettings) {
        SettingsDialog(
            goal = goal,
            greenIncome = greenIncome,
            entries = entries.toList(),
            onDismiss = { showSettings = false },
            onSave = { g, green ->
                goal = g
                greenIncome = green
                store.goal = g
                store.greenForIncome = green
                showSettings = false
            },
            onClearAll = {
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
                    trackColor = Color.White.copy(alpha = 0.6f),
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
fun EntryRow(e: Entry, inc: Color, exp: Color, onClick: () -> Unit) {
    val isIn = e.amount >= 0
    val color = if (isIn) inc else exp
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (isIn) "＋" else "－", color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    e.note.ifBlank { if (isIn) "存入" else "取出" },
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (isIn) "存入" else "取出",
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
fun EntryDialog(
    initial: Entry?,
    defaultDate: LocalDate,
    inc: Color,
    exp: Color,
    onDismiss: () -> Unit,
    onSave: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
) {
    var isIn by remember { mutableStateOf(initial?.let { it.amount >= 0 } ?: true) }
    var amountText by remember { mutableStateOf(initial?.let { plainNumber(kotlin.math.abs(it.amount)) } ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val amount = amountText.toDoubleOrNull()
    val valid = amount != null && amount > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "记一笔" else "编辑记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        onSave(
                            Entry(
                                id = initial?.id ?: System.currentTimeMillis(),
                                date = defaultDate,
                                amount = if (isIn) a else -a,
                                note = note.trim(),
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
}

@Composable
fun SettingsDialog(
    goal: Double,
    greenIncome: Boolean,
    entries: List<Entry>,
    onDismiss: () -> Unit,
    onSave: (Double, Boolean) -> Unit,
    onClearAll: () -> Unit,
) {
    val context = LocalContext.current
    var goalText by remember { mutableStateOf(if (goal > 0) plainNumber(goal) else "") }
    var green by remember { mutableStateOf(greenIncome) }
    var confirmClear by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                        Text("清空全部记录？无法恢复", modifier = Modifier.weight(1f))
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
            TextButton(onClick = { onSave(goalText.toDoubleOrNull() ?: 0.0, green) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
