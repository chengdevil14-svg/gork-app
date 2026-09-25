@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.lkcheng.savings

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

val CYCLE_NAMES = listOf("每月", "每年", "每周")
val SUB_PRESETS = listOf(
    "Netflix", "Spotify", "YouTube Premium", "iCloud", "Google One", "ChatGPT", "Claude",
    "Disney+", "手机月费", "网络费", "健身房", "会员",
)

fun parseDate(s: String): LocalDate? = try {
    LocalDate.parse(s.trim())
} catch (e: Exception) {
    null
}

fun advance(d: LocalDate, cycle: Int): LocalDate = when (cycle) {
    1 -> d.plusYears(1)
    2 -> d.plusWeeks(1)
    else -> d.plusMonths(1)
}

/** Next billing date on/after [from]. */
fun nextBilling(s: Sub, from: LocalDate = LocalDate.now()): LocalDate? {
    var d = parseDate(s.nextDate) ?: return null
    var guard = 0
    while (d.isBefore(from) && guard < 1000) {
        d = advance(d, s.cycle)
        guard++
    }
    return d
}

/** Monthly cost in the settlement currency. */
fun monthlyCost(s: Sub): Double = Fx.toBase(
    when (s.cycle) {
        1 -> s.amount / 12
        2 -> s.amount * 52 / 12
        else -> s.amount
    },
    s.currency,
)

fun dateCn(d: LocalDate) = "${d.monthValue}月${d.dayOfMonth}日"

fun daysWord(days: Long) = when (days) {
    0L -> "今天"
    1L -> "明天"
    else -> "还有 $days 天"
}

/**
 * Rolls stored billing dates forward and, for subs with auto-record on,
 * creates a 取出 entry for every billing date up to today that isn't recorded yet.
 * Returns updated subs and new entries.
 */
fun processSubs(subs: List<Sub>, today: LocalDate = LocalDate.now()): Pair<List<Sub>, List<Entry>> {
    val newEntries = ArrayList<Entry>()
    var idSeed = System.currentTimeMillis()
    val out = subs.map { s ->
        if (!s.active) return@map s
        val start = parseDate(s.nextDate) ?: return@map s
        var d = start
        var recorded = parseDate(s.recordedUntil)
        var guard = 0
        while (!d.isAfter(today) && guard < 1000) {
            if (s.autoRecord && (recorded == null || d.isAfter(recorded))) {
                newEntries.add(Entry(id = idSeed++, date = d, amount = -s.amount, note = s.name, tag = "订阅", currency = s.currency))
                recorded = d
            }
            if (d.isBefore(today)) d = advance(d, s.cycle) else break
            guard++
        }
        s.copy(nextDate = d.toString(), recordedUntil = recorded?.toString() ?: s.recordedUntil)
    }
    return out to newEntries
}

@Composable
fun SubsSummary(subs: List<Sub>, exp: Color, onAdd: () -> Unit) {
    val active = subs.filter { it.active }
    val perMonth = active.sumOf { monthlyCost(it) }
    val wantCancel = active.filter { it.wantCancel }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (active.isEmpty()) {
                Text(
                    "还没有订阅。把 Netflix、手机月费、健身房等每月自动扣的钱加进来，续费前会提醒你，不想要的可以及时取消。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = onAdd),
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("每月约 ", fontSize = 13.sp)
                    Text(money(perMonth), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = exp)
                    Spacer(Modifier.width(12.dp))
                    Text("每年约 ${money(perMonth * 12)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${active.size} 个订阅进行中", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (wantCancel.isNotEmpty()) {
                    val save = wantCancel.sumOf { monthlyCost(it) }
                    Text(
                        "✂️ 标记想取消 ${wantCancel.size} 个，取消后每月可省 ${money(save)}",
                        fontSize = 13.sp,
                        color = exp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
fun SubRow(s: Sub, exp: Color, onClick: () -> Unit) {
    val next = nextBilling(s)
    val days = next?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.name,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (s.active) null else TextDecoration.LineThrough,
                    )
                    if (s.wantCancel && s.active) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "想取消",
                            color = exp,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(exp.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(
                    if (!s.active) {
                        "已取消"
                    } else if (next != null && days != null) {
                        "下次扣款 ${dateCn(next)}（${daysWord(days)}）" + if (s.autoRecord) " · 自动记账" else ""
                    } else {
                        ""
                    },
                    fontSize = 12.sp,
                    color = if (s.active && days != null && days <= s.remindBefore) exp else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "-" + moneyC(s.amount, s.currency),
                    color = if (s.active) exp else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                Text(CYCLE_NAMES.getOrElse(s.cycle) { "每月" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SubDialog(
    initial: Sub?,
    exp: Color,
    remindH: Int,
    remindM: Int,
    onDismiss: () -> Unit,
    onSave: (Sub) -> Unit,
    onDelete: (Sub) -> Unit,
) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var amountText by remember { mutableStateOf(initial?.let { plainNumber(it.amount) } ?: "") }
    var cycle by remember { mutableIntStateOf(initial?.cycle ?: 0) }
    var cur by remember { mutableStateOf(initial?.currency ?: Cur.code) }
    var dateText by remember {
        mutableStateOf(initial?.let { (nextBilling(it) ?: LocalDate.now()).toString() } ?: LocalDate.now().plusMonths(1).toString())
    }
    var before by remember { mutableIntStateOf(initial?.remindBefore ?: 1) }
    var wantCancel by remember { mutableStateOf(initial?.wantCancel ?: false) }
    var autoRecord by remember { mutableStateOf(initial?.autoRecord ?: false) }
    var active by remember { mutableStateOf(initial?.active ?: true) }
    var confirmDelete by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    val amount = amountText.toDoubleOrNull() ?: 0.0
    val date = parseDate(dateText)
    val valid = name.isNotBlank() && amount > 0 && date != null

    fun build(): Sub? {
        val d = parseDate(dateText) ?: return null
        val changedDate = initial == null || initial.nextDate != d.toString()
        return Sub(
            id = initial?.id ?: System.currentTimeMillis(),
            name = name.trim(),
            amount = amount,
            cycle = cycle,
            nextDate = d.toString(),
            remindBefore = before,
            wantCancel = wantCancel,
            autoRecord = autoRecord,
            active = active,
            // don't back-fill charges before the date the user entered
            currency = cur,
            recordedUntil = if (changedDate) d.minusDays(1).toString() else initial?.recordedUntil ?: "",
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增订阅" else "编辑订阅") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (initial == null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (n in SUB_PRESETS) {
                            FilterChip(selected = name == n, onClick = { name = n }, label = { Text(n, fontSize = 12.sp) })
                        }
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { v -> amountText = v.filter { it.isDigit() || it == '.' } },
                    label = { Text("每次扣款金额") },
                    prefix = { Text(symbolOf(cur)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in CURRENCIES) {
                        FilterChip(selected = cur == c.first, onClick = { cur = c.first }, label = { Text(c.second, fontSize = 13.sp) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in CYCLE_NAMES.indices) {
                        FilterChip(selected = cycle == i, onClick = { cycle = i }, label = { Text(CYCLE_NAMES[i]) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it.take(10) },
                        label = { Text("下次扣款日（年-月-日）") },
                        isError = date == null,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        val d = parseDate(dateText) ?: LocalDate.now()
                        DatePickerDialog(ctx, { _, y, mo, day ->
                            dateText = LocalDate.of(y, mo + 1, day).toString()
                        }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                    }) { Text("📅") }
                }
                Text("续费前提醒", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((label, n) in listOf("当天" to 0, "提前1天" to 1, "提前3天" to 3, "提前7天" to 7)) {
                        FilterChip(selected = before == n, onClick = { before = n }, label = { Text(label, fontSize = 13.sp) })
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("我想取消这个订阅")
                        Text("续费前会提醒你「记得去取消」", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = wantCancel, onCheckedChange = { wantCancel = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("扣款日自动记一笔支出")
                        Text("分类「订阅」，不用自己手动记", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = autoRecord, onCheckedChange = { autoRecord = it })
                }
                if (initial != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("已经取消了")
                            Text("停止提醒和自动记账，保留在列表里", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = !active, onCheckedChange = { active = !it })
                    }
                }
                OutlinedButton(
                    enabled = valid,
                    onClick = {
                        val sub = build()
                        if (sub != null && !Reminders.addSubToCalendar(ctx, sub, remindH, remindM)) msg = "没找到日历 app"
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("📅 加入手机日历（自动重复）") }
                if (msg.isNotEmpty()) Text(msg, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                Text(
                    "提醒方式（通知 / 闹钟响铃 / Email）在「设置 → 提醒与通知 → 订阅续费提醒」里选。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (initial != null) {
                    HorizontalDivider()
                    if (confirmDelete) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("删除这个订阅？", modifier = Modifier.weight(1f))
                            TextButton(onClick = { onDelete(initial) }) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { build()?.let(onSave) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
