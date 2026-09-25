@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.lkcheng.savings

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

data class Deposit(
    val id: Long,
    val name: String,
    val principal: Double,
    val currency: String = "SGD",
    val rate: Double, // 年利率 %
    val start: String, // yyyy-MM-dd
    val termMonths: Int = 0, // 0 = 活期（没有到期日）
    val endDate: String = "", // 自定义到期日（留空按期限计算）
    val accountId: Long = 0L, // 利息存入哪个账户
    val autoSettle: Boolean = true,
    val autoRenew: Boolean = false,
    val settled: String = "", // 已结算的到期日
    val active: Boolean = true,
    val daily: Double = 0.0, // 自己填的每日利息（>0 时自动记入）
    val dailyMode: Int = 0, // 0 每天记一笔, 1 每月底记一笔
    val dailyUntil: String = "", // 每日利息已记到哪天
)

fun isDepInterest(e: Entry, d: Deposit) = e.tag == "利息" && e.note.startsWith(d.name)

/** Interest actually recorded (auto or by hand) for this deposit, in its currency. */
fun recordedInterest(d: Deposit, entries: List<Entry>) =
    entries.filter { isDepInterest(it, d) && it.amount > 0 }.sumOf { Fx.convert(it.amount, it.currency, d.currency) }

fun maturityOf(d: Deposit): LocalDate? {
    parseDate(d.endDate)?.let { return it }
    if (d.termMonths <= 0) return null
    return parseDate(d.start)?.plusMonths(d.termMonths.toLong())
}

data class DepStat(
    val days: Long, // days counted so far
    val totalDays: Long, // whole term (0 for 活期)
    val accrued: Double, // interest to today
    val atMaturity: Double, // interest for full term
    val monthly: Double, // ≈ per month
    val daysLeft: Long,
    val maturity: LocalDate?,
)

fun calcDeposit(d: Deposit, today: LocalDate = LocalDate.now()): DepStat {
    val start = parseDate(d.start) ?: today
    val mat = maturityOf(d)
    val end = if (mat != null && mat.isBefore(today)) mat else today
    val days = maxOf(0L, ChronoUnit.DAYS.between(start, end))
    val totalDays = if (mat != null) maxOf(0L, ChronoUnit.DAYS.between(start, mat)) else 0L
    val r = d.rate / 100.0
    return DepStat(
        days = days,
        totalDays = totalDays,
        accrued = d.principal * r * days / 365.0,
        atMaturity = d.principal * r * totalDays / 365.0,
        monthly = d.principal * r / 12.0,
        daysLeft = if (mat != null) ChronoUnit.DAYS.between(today, mat) else 0L,
        maturity = mat,
    )
}

/** Settles matured deposits: records interest and optionally rolls over. */
fun processDeposits(deps: List<Deposit>, accounts: List<Account>, today: LocalDate = LocalDate.now()): Pair<List<Deposit>, List<Entry>> {
    val newEntries = ArrayList<Entry>()
    var seed = System.currentTimeMillis() + 500
    val out = deps.map { d0 ->
        var d = d0
        // Daily interest the user typed in: record per day or per month end.
        if (d.active && d.daily > 0) {
            val startD = parseDate(d.start) ?: today
            val mat = maturityOf(d)
            val lastEnd = if (mat != null && mat.isBefore(today)) mat else today
            var from = (parseDate(d.dailyUntil) ?: startD).plusDays(1)
            val acc = if (accounts.any { it.id == d.accountId }) d.accountId else accounts.firstOrNull()?.id ?: 0L
            var until = parseDate(d.dailyUntil)
            if (d.dailyMode == 0) {
                var g = 0
                while (!from.isAfter(lastEnd) && g < 400) {
                    newEntries.add(Entry(id = seed++, date = from, amount = round2(d.daily), note = d.name + " 利息", tag = "利息", currency = d.currency, accountId = acc))
                    until = from
                    from = from.plusDays(1)
                    g++
                }
            } else {
                var g = 0
                while (g < 60) {
                    val monthEnd = java.time.YearMonth.from(from).atEndOfMonth()
                    val end = if (mat != null && mat.isBefore(monthEnd)) mat else monthEnd
                    if (end.isAfter(today) || from.isAfter(end)) break
                    val n = ChronoUnit.DAYS.between(from, end) + 1
                    newEntries.add(Entry(id = seed++, date = end, amount = round2(d.daily * n), note = d.name + " 利息（$n 天）", tag = "利息", currency = d.currency, accountId = acc))
                    until = end
                    from = end.plusDays(1)
                    g++
                }
            }
            if (until != null && until.toString() != d.dailyUntil) d = d.copy(dailyUntil = until.toString())
        }
        var guard = 0
        while (d.active && guard < 200) {
            guard++
            val mat = maturityOf(d) ?: break
            if (mat.isAfter(today) || d.settled == mat.toString()) break
            val st = calcDeposit(d, mat)
            if (d.autoSettle && st.atMaturity > 0 && d.daily <= 0) {
                val acc = if (accounts.any { it.id == d.accountId }) d.accountId else accounts.firstOrNull()?.id ?: 0L
                newEntries.add(
                    Entry(
                        id = seed++, date = mat, amount = round2(st.atMaturity), note = d.name + " 利息",
                        tag = "利息", currency = d.currency, accountId = acc,
                    )
                )
            }
            d = if (d.autoRenew && d.termMonths > 0) {
                d.copy(principal = round2(d.principal + st.atMaturity), start = mat.toString(), endDate = "", settled = "")
            } else {
                d.copy(settled = mat.toString())
            }
        }
        d
    }
    return out to newEntries
}

fun round2(v: Double) = Math.round(v * 100.0) / 100.0

fun pct(v: Double) = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.') + "%"

@Composable
fun DepositsSummary(deps: List<Deposit>, inc: Color, onAdd: () -> Unit) {
    val active = deps.filter { it.active && (maturityOf(it)?.let { m -> it.settled != m.toString() } ?: true) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (active.isEmpty()) {
                Text(
                    "记录定期存款、储蓄户口、moomoo 等的利息：填本金、年利率、期限，自动算利息和到期日，到期自动记一笔。",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onAdd),
                )
            } else {
                val principal = active.sumOf { Fx.toBase(it.principal, it.currency) }
                val accrued = active.sumOf { Fx.toBase(calcDeposit(it).accrued, it.currency) }
                val yearly = active.sumOf { Fx.toBase(it.principal * it.rate / 100, it.currency) }
                Text("本金合计 ${money(principal)}", fontWeight = FontWeight.Bold)
                Text("到今天已赚利息 ≈ ${money(accrued)}", color = inc, fontSize = 14.sp)
                Text("每年约 ${money(yearly)} · 每月约 ${money(yearly / 12)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DepositRow(d: Deposit, entries: List<Entry>, inc: Color, onClick: () -> Unit) {
    val st = calcDeposit(d)
    val got = recordedInterest(d, entries)
    val matured = st.maturity != null && d.settled == st.maturity.toString()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    d.name,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    textDecoration = if (d.active && !matured) null else TextDecoration.LineThrough,
                )
                Text("年利率 " + pct(d.rate), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Text(
                "本金 ${moneyC(d.principal, d.currency)} · " + if (d.termMonths > 0 || d.endDate.isNotBlank()) "定期 ${d.termMonths.takeIf { it > 0 }?.let { "$it 个月" } ?: ""}" else "活期",
                fontSize = 13.sp,
            )
            val mat = st.maturity
            if (!d.active) {
                Text("已停用", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (mat == null) {
                Text(
                    "已存 ${st.days} 天 · 已赚 ≈ ${moneyC(st.accrued, d.currency)} · 每月约 ${moneyC(st.monthly, d.currency)}",
                    fontSize = 12.sp,
                    color = inc,
                )
            } else if (matured) {
                Text("✅ ${dateCn(mat)} 已到期 · 利息 ${moneyC(st.atMaturity, d.currency)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    "到期 ${mat.year}年${dateCn(mat)}（${daysWord(st.daysLeft)}）· 到期利息 ${moneyC(st.atMaturity, d.currency)}",
                    fontSize = 12.sp,
                    color = inc,
                )
                Text(
                    "已过 ${st.days}/${st.totalDays} 天 · 目前已赚 ≈ ${moneyC(st.accrued, d.currency)}" + if (d.autoRenew) " · 到期自动续存" else "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (d.daily > 0) {
                Text(
                    "每日利息 ${moneyC(d.daily, d.currency)}（你填的）· " + if (d.dailyMode == 0) "每天自动记入" else "每月底自动记入",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (got > 0) {
                val diff = got - st.accrued
                Text(
                    "实际记到 ${moneyC(got, d.currency)} · 按利率算 ${moneyC(st.accrued, d.currency)} · 差 " +
                        (if (diff >= 0) "+" else "-") + moneyC(kotlin.math.abs(diff), d.currency),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun DepositDialog(
    initial: Deposit?,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    entries: List<Entry>,
    onSave: (Deposit) -> Unit,
    onDelete: (Deposit) -> Unit,
    onRecord: (Entry) -> Unit,
) {
    val ctx = LocalContext.current
    var dailyText by remember { mutableStateOf(initial?.let { if (it.daily > 0) String.format(Locale.US, "%.4f", it.daily).trimEnd('0').trimEnd('.') else "" } ?: "") }
    var dailyMode by remember { mutableStateOf(initial?.dailyMode ?: 0) }
    var recText by remember { mutableStateOf("") }
    var recMsg by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var principalText by remember { mutableStateOf(initial?.let { plainNumber(it.principal) } ?: "") }
    var cur by remember { mutableStateOf(initial?.currency ?: Cur.code) }
    var rateText by remember { mutableStateOf(initial?.let { String.format(Locale.US, "%.4f", it.rate).trimEnd('0').trimEnd('.') } ?: "") }
    var start by remember { mutableStateOf(initial?.start ?: LocalDate.now().toString()) }
    var fixed by remember { mutableStateOf(initial?.let { it.termMonths > 0 || it.endDate.isNotBlank() } ?: true) }
    var termText by remember { mutableStateOf(initial?.let { if (it.termMonths > 0) it.termMonths.toString() else "" } ?: "3") }
    var endText by remember { mutableStateOf(initial?.endDate ?: "") }
    var accId by remember { mutableLongStateOf(initial?.accountId ?: accounts.firstOrNull { !it.archived }?.id ?: 0L) }
    var autoSettle by remember { mutableStateOf(initial?.autoSettle ?: true) }
    var autoRenew by remember { mutableStateOf(initial?.autoRenew ?: false) }
    var active by remember { mutableStateOf(initial?.active ?: true) }
    var confirmDelete by remember { mutableStateOf(false) }

    val principal = principalText.toDoubleOrNull() ?: 0.0
    val rate = rateText.toDoubleOrNull() ?: 0.0
    val term = termText.toIntOrNull() ?: 0
    val startOk = parseDate(start) != null
    val endOk = endText.isBlank() || parseDate(endText) != null
    val valid = name.isNotBlank() && principal > 0 && rate >= 0 && startOk && endOk && (!fixed || term > 0 || endText.isNotBlank())

    fun build() = Deposit(
        id = initial?.id ?: System.currentTimeMillis(),
        name = name.trim(),
        principal = principal,
        currency = cur,
        rate = rate,
        start = parseDate(start)?.toString() ?: LocalDate.now().toString(),
        termMonths = if (fixed) term else 0,
        endDate = if (fixed) (parseDate(endText)?.toString() ?: "") else "",
        accountId = accId,
        autoSettle = autoSettle,
        autoRenew = fixed && autoRenew,
        settled = if (initial != null && initial.start == start && initial.termMonths == term) initial.settled else "",
        active = active,
        daily = dailyText.toDoubleOrNull() ?: 0.0,
        dailyMode = dailyMode,
        // start counting daily interest from today when first set, so past days aren't back-filled by surprise
        dailyUntil = initial?.let { i -> if (i.dailyUntil.isNotBlank() && i.daily > 0) i.dailyUntil else null }
            ?: if ((dailyText.toDoubleOrNull() ?: 0.0) > 0) LocalDate.now().minusDays(1).toString() else "",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增利息 / 定期" else "编辑") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("名称（自己填，例：Maybank 定期、moomoo 现金）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = principalText,
                    onValueChange = { v -> principalText = v.filter { it.isDigit() || it == '.' } },
                    label = { Text("本金") },
                    prefix = { Text("$cur ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in CURRENCIES) FilterChip(selected = cur == c.first, onClick = { cur = c.first }, label = { Text(c.third, fontSize = 13.sp) })
                }
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { v -> rateText = v.filter { it.isDigit() || it == '.' } },
                    label = { Text("年利率") },
                    suffix = { Text("% / 年") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("开始日期：", fontSize = 14.sp)
                    TextButton(onClick = {
                        val d = parseDate(start) ?: LocalDate.now()
                        DatePickerDialog(ctx, { _, y, mo, day -> start = LocalDate.of(y, mo + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                    }) { Text("📅 $start") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = fixed, onClick = { fixed = true }, label = { Text("定期（有到期日）") })
                    FilterChip(selected = !fixed, onClick = { fixed = false }, label = { Text("活期") })
                }
                if (fixed) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (m in listOf(1, 3, 6, 9, 12, 24)) {
                            FilterChip(
                                selected = termText == m.toString() && endText.isBlank(),
                                onClick = {
                                    termText = m.toString()
                                    endText = ""
                                },
                                label = { Text(if (m < 12) "$m 个月" else "${m / 12} 年", fontSize = 13.sp) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = termText,
                            onValueChange = { v -> termText = v.filter { it.isDigit() }.take(3) },
                            label = { Text("期限（月）") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = endText,
                            onValueChange = { endText = it.take(10) },
                            label = { Text("或到期日") },
                            placeholder = { Text("2026-12-25") },
                            isError = !endOk,
                            singleLine = true,
                            modifier = Modifier.weight(1.3f),
                        )
                    }
                }
                // Live calculation so you can check against the bank
                if (valid) {
                    val st = calcDeposit(build())
                    Card(shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("计算（单利）", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("本金 × 年利率 × 天数 ÷ 365", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val mat = st.maturity
                            if (mat != null) {
                                Text("到期日：$mat（共 ${st.totalDays} 天）", fontSize = 13.sp)
                                Text(
                                    "到期利息：${plainNumber(principal)} × ${pct(rate)} × ${st.totalDays} ÷ 365 = ${moneyC(st.atMaturity, cur)}",
                                    fontSize = 13.sp,
                                )
                                Text("本息合计：${moneyC(principal + st.atMaturity, cur)}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("到今天已赚：${moneyC(st.accrued, cur)}（${st.days} 天）", fontSize = 13.sp)
                            Text("每月约：${moneyC(st.monthly, cur)} · 每天约：${moneyC(principal * rate / 100 / 365, cur)}", fontSize = 13.sp)
                        }
                    }
                }
                HorizontalDivider()
                Text("每日利息（自己填，可选）", fontWeight = FontWeight.Medium)
                Text(
                    "像 moomoo、储蓄 app 每天显示的利息，填进来就按这个数自动记入（不再用上面的利率算）。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = dailyText,
                    onValueChange = { v -> dailyText = v.filter { it.isDigit() || it == '.' } },
                    label = { Text("每天利息") },
                    prefix = { Text("$cur ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if ((dailyText.toDoubleOrNull() ?: 0.0) > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = dailyMode == 0, onClick = { dailyMode = 0 }, label = { Text("每天记一笔") })
                        FilterChip(selected = dailyMode == 1, onClick = { dailyMode = 1 }, label = { Text("每月底记一笔") })
                    }
                    val dv = dailyText.toDoubleOrNull() ?: 0.0
                    Text(
                        "≈ 每月 ${moneyC(dv * 30, cur)} · 每年 ${moneyC(dv * 365, cur)} · 相当于年利率 " +
                            (if (principal > 0) pct(dv * 365 / principal * 100) else "-"),
                        fontSize = 12.sp,
                    )
                }
                if (initial != null) {
                    HorizontalDivider()
                    Text("手动记一笔实际收到的利息", fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = recText,
                            onValueChange = { v -> recText = v.filter { it.isDigit() || it == '.' } },
                            label = { Text("今天收到") },
                            prefix = { Text("$cur ") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = (recText.toDoubleOrNull() ?: 0.0) > 0,
                            onClick = {
                                val v = recText.toDoubleOrNull() ?: 0.0
                                onRecord(
                                    Entry(
                                        id = System.currentTimeMillis(), date = LocalDate.now(), amount = v,
                                        note = initial.name + " 利息", tag = "利息", currency = cur,
                                        accountId = accId,
                                    )
                                )
                                recText = ""
                                recMsg = "✅ 已记入 ${moneyC(v, cur)}"
                            },
                        ) { Text("记入") }
                    }
                    if (recMsg.isNotEmpty()) Text(recMsg, fontSize = 12.sp)
                    val got = recordedInterest(initial, entries)
                    if (got > 0) {
                        Text("已记录利息合计：${moneyC(got, initial.currency)}", fontSize = 13.sp)
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("到期自动结算利息")
                        Text("到期那天自动记一笔「利息」存入", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = autoSettle, onCheckedChange = { autoSettle = it })
                }
                if (autoSettle) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (a in accounts.filter { !it.archived }) {
                            FilterChip(selected = accId == a.id, onClick = { accId = a.id }, label = { Text(accIcon(a) + " " + a.name, fontSize = 12.sp) })
                        }
                    }
                }
                if (fixed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("到期自动续存")
                            Text("本金 + 利息 自动转存下一期", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = autoRenew, onCheckedChange = { autoRenew = it })
                    }
                }
                if (initial != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("已经取出 / 停用", modifier = Modifier.weight(1f))
                        Switch(checked = !active, onCheckedChange = { active = !it })
                    }
                    HorizontalDivider()
                    if (confirmDelete) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("删除这项？", modifier = Modifier.weight(1f))
                            TextButton(onClick = { onDelete(initial) }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        }
                    } else {
                        TextButton(onClick = { confirmDelete = true }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(build()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
