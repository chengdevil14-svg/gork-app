@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.lkcheng.savings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class GoalStat(
    val done: Double,
    val remaining: Double,
    val pct: Double,
    val thisMonth: Double,
    val count: Int,
    val finish: YearMonth?,
    val monthsLeft: Int,
    val deadline: YearMonth?,
    val monthsToDeadline: Long,
    val needPerMonth: Double?,
    val monthlyInterest: Double = 0.0,
    val dailyInterest: Double = 0.0,
    val neverPayoff: Boolean = false,
    val futureInterest: Double = 0.0,
)

/** Adds a month of interest to debts for every completed month since last time. */
fun processGoalInterest(goals: List<Goal>, entries: List<Entry>, now: YearMonth = YearMonth.now()): List<Goal> =
    goals.map { g0 ->
        if (!g0.debt || g0.rate <= 0) return@map g0
        var g = g0
        val last = parseYm(g.accruedUntil)
        if (last == null) return@map g.copy(accruedUntil = now.minusMonths(1).toString())
        var m = last.plusMonths(1)
        var guard = 0
        while (m.isBefore(now) && guard < 120) {
            val st = calcGoal(g, entries, 0.0, m)
            val add = st.remaining * g.rate / 1200.0
            g = g.copy(accrued = round2(g.accrued + add), accruedUntil = m.toString())
            m = m.plusMonths(1)
            guard++
        }
        g
    }

fun parseYm(s: String): YearMonth? = try {
    if (s.isBlank()) null else YearMonth.parse(s.trim())
} catch (e: Exception) {
    null
}

fun ymCn(ym: YearMonth) = "${ym.year}年${ym.monthValue}月"

/** How much an entry counts toward a goal: payments (取出) count for debts, deposits for savings. */
fun contribution(g: Goal, e: Entry) = Fx.convert(if (g.debt) -e.amount else e.amount, e.currency, g.currency)

fun calcGoal(g: Goal, entries: List<Entry>, total: Double, now: YearMonth = YearMonth.now()): GoalStat {
    val linked = entries.filter { it.goalId == g.id }
    val byTotal = !g.debt && g.useTotal
    val done = if (byTotal) Fx.convert(total, Cur.code, g.currency) else g.startDone + linked.sumOf { contribution(g, it) }
    val owed = g.target + (if (g.debt) g.accrued else 0.0)
    val remaining = max(0.0, owed - done)
    val pct = if (owed > 0) (done / owed).coerceIn(0.0, 1.0) else 0.0
    val r = if (g.debt && g.rate > 0) g.rate / 1200.0 else 0.0
    var never = false
    var futureInterest = 0.0
    val thisMonth = if (byTotal) {
        Fx.convert(entries.filter { YearMonth.from(it.date) == now && !it.isTransfer }.sumOf { it.base }, Cur.code, g.currency)
    } else {
        linked.filter { YearMonth.from(it.date) == now }.sumOf { contribution(g, it) }
    }
    var finish: YearMonth? = null
    var monthsLeft = 0
    if (remaining <= 0.0) {
        finish = now
    } else if (g.monthly > 0) {
        // what is still planned for this month, then full months after
        val restOfThisMonth = min(remaining, max(0.0, g.monthly - thisMonth))
        val rest = remaining - restOfThisMonth
        if (r > 0 && rest > 0) {
            // amortization: months n so that payments cover balance + interest
            if (g.monthly <= rest * r) {
                never = true
            } else {
                monthsLeft = ceil(-kotlin.math.ln(1 - r * rest / g.monthly) / kotlin.math.ln(1 + r) - 1e-9).toInt()
                finish = now.plusMonths(monthsLeft.toLong())
                futureInterest = max(0.0, monthsLeft * g.monthly - rest)
            }
        } else {
            monthsLeft = ceil(rest / g.monthly - 1e-9).toInt()
            finish = now.plusMonths(monthsLeft.toLong())
        }
    }
    val dl = parseYm(g.deadline)
    val monthsToDl = if (dl != null) ChronoUnit.MONTHS.between(now, dl) else 0L
    val need = if (dl != null && remaining > 0) {
        if (monthsToDl <= 0) {
            remaining
        } else if (r > 0) {
            remaining * r / (1 - Math.pow(1 + r, -monthsToDl.toDouble()))
        } else {
            remaining / monthsToDl
        }
    } else {
        null
    }
    return GoalStat(
        done, remaining, pct, thisMonth, linked.size, finish, monthsLeft, dl, monthsToDl, need,
        monthlyInterest = remaining * r,
        dailyInterest = if (g.debt) remaining * g.rate / 100.0 / 365.0 else 0.0,
        neverPayoff = never,
        futureInterest = futureInterest,
    )
}

@Composable
fun GoalCard(g: Goal, s: GoalStat, inc: Color, exp: Color, onClick: () -> Unit) {
    val verb = if (g.debt) "还" else "存"
    val m: (Double) -> String = { moneyC(it, g.currency) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                val tagColor = if (g.debt) exp else inc
                Text(
                    if (g.debt) "欠款" else "存款目标",
                    color = tagColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(tagColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    (if (g.debt) "已还 " else "已存 ") + m(s.done),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Text(" / " + m(g.target + if (g.debt) g.accrued else 0.0), color = muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(
                    "${(s.pct * 100).toInt()}%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LinearProgressIndicator(
                progress = { s.pct.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
            )
            if (g.debt && g.rate > 0 && s.remaining > 0) {
                Text(
                    "💸 年利率 ${pct(g.rate)} · 每月利息约 ${m(s.monthlyInterest)} · 每天约 ${m(s.dailyInterest)}" +
                        if (g.accrued > 0) " · 已累计利息 ${m(g.accrued)}" else "",
                    fontSize = 12.sp,
                    color = exp,
                )
                if (s.neverPayoff) {
                    Text("⚠️ 每月还 ${m(g.monthly)} 不够付利息，欠款会越滚越多！至少要还 ${m(s.monthlyInterest)} 以上", fontSize = 13.sp, color = exp, fontWeight = FontWeight.Bold)
                } else if (s.futureInterest > 0) {
                    Text("按计划还清，还要再付利息约 ${m(s.futureInterest)}", fontSize = 12.sp, color = muted)
                }
            }
            if (s.remaining <= 0.0) {
                Text(if (g.debt) "🎉 已经还清！" else "🎉 目标达成！", color = inc, fontWeight = FontWeight.Bold)
            } else {
                Text(
                    (if (g.debt) "还欠 " else "还差 ") + m(s.remaining),
                    fontWeight = FontWeight.Medium,
                )
                if (g.monthly > 0) {
                    val left = g.monthly - s.thisMonth
                    Text(
                        when {
                            left < 0 -> "本月已$verb ${m(s.thisMonth)}，比计划多$verb ${m(-left)} 👍"
                            left == 0.0 -> "本月已$verb ${m(s.thisMonth)}，本月计划完成 ✅"
                            else -> "本月已$verb ${m(s.thisMonth)}，还差 ${m(left)} 达到每月 ${m(g.monthly)}"
                        },
                        fontSize = 13.sp,
                    )
                    val f = s.finish
                    if (f != null) {
                        Text(
                            "按每月 ${m(g.monthly)}：预计 ${ymCn(f)}${if (g.debt) "还清" else "存够"}" +
                                if (s.monthsLeft > 0) "（再 ${s.monthsLeft} 个月）" else "（就在本月）",
                            fontSize = 13.sp,
                        )
                    }
                } else {
                    Text(
                        "本月已$verb ${m(s.thisMonth)} · 设置每月计划就能算出完成时间",
                        fontSize = 13.sp,
                        color = muted,
                    )
                }
                val dl = s.deadline
                val need = s.needPerMonth
                if (dl != null && need != null) {
                    Text(
                        if (s.monthsToDeadline <= 0) {
                            "期限 ${ymCn(dl)}：本月内需$verb ${m(s.remaining)}"
                        } else {
                            "要在 ${ymCn(dl)} 前完成：从下个月起每月需$verb ${m(need)}"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (g.monthly > 0 && need > g.monthly) exp else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            val dl2 = dueLine(g)
            if (dl2 != null && s.remaining > 0) {
                Text("📅 $dl2", fontSize = 13.sp)
            }
            if (!(!g.debt && g.useTotal)) {
                Text("已关联 ${s.count} 笔记录 · 点击编辑", fontSize = 12.sp, color = muted)
            } else {
                Text("按总存款计算 · 点击编辑", fontSize = 12.sp, color = muted)
            }
        }
    }
}

@Composable
fun MoneyField(value: String, onChange: (String) -> Unit, label: String, code: String = Cur.code) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> onChange(s.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        prefix = { Text(symbolOf(code) + " ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalDialog(
    initial: Goal?,
    inc: Color,
    exp: Color,
    onDismiss: () -> Unit,
    onSave: (Goal) -> Unit,
    onDelete: (Goal) -> Unit,
) {
    var debt by remember { mutableStateOf(initial?.debt ?: false) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var targetText by remember { mutableStateOf(initial?.let { plainNumber(it.target) } ?: "") }
    var startText by remember { mutableStateOf(initial?.let { if (it.startDone > 0) plainNumber(it.startDone) else "" } ?: "") }
    var monthlyText by remember { mutableStateOf(initial?.let { if (it.monthly > 0) plainNumber(it.monthly) else "" } ?: "") }
    var deadlineText by remember { mutableStateOf(initial?.deadline ?: "") }
    var useTotal by remember { mutableStateOf(initial?.useTotal ?: false) }
    var dueText by remember { mutableStateOf(initial?.let { if (it.dueDay > 0) it.dueDay.toString() else "" } ?: "") }
    var before by remember { mutableStateOf(initial?.remindBefore ?: 0) }
    var cur by remember { mutableStateOf(initial?.currency ?: Cur.code) }
    var rateText by remember { mutableStateOf(initial?.let { if (it.rate > 0) plainNumber(it.rate) else "" } ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    val target = targetText.toDoubleOrNull() ?: 0.0
    val deadlineOk = deadlineText.isBlank() || parseYm(deadlineText) != null
    val dueDay = dueText.toIntOrNull() ?: 0
    val dueOk = dueText.isBlank() || dueDay in 1..31
    val valid = name.isNotBlank() && target > 0 && deadlineOk && dueOk

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增目标 / 欠款" else "编辑") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeButton("存款目标", !debt, inc, Modifier.weight(1f)) { debt = false }
                    TypeButton("欠款", debt, exp, Modifier.weight(1f)) { debt = true }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text(if (debt) "名称（例：信用卡、欠朋友）" else "名称（例：旅行基金）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (debt) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (n in listOf("卡债", "信用卡", "车贷", "房贷", "欠朋友", "分期")) {
                            FilterChip(selected = name == n, onClick = { name = n }, label = { Text(n, fontSize = 13.sp) })
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in CURRENCIES) {
                        FilterChip(selected = cur == c.first, onClick = { cur = c.first }, label = { Text(c.third, fontSize = 13.sp) })
                    }
                }
                MoneyField(targetText, { targetText = it }, if (debt) "欠款总额" else "目标金额", cur)
                if (debt) {
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { v -> rateText = v.filter { it.isDigit() || it == '.' } },
                        label = { Text("年利率（可选，例如信用卡 24）") },
                        suffix = { Text("% / 年") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val rv = rateText.toDoubleOrNull() ?: 0.0
                    val tv = targetText.toDoubleOrNull() ?: 0.0
                    if (rv > 0 && tv > 0) {
                        Text(
                            "每月利息约 ${moneyC(tv * rv / 1200, cur)} · 每天约 ${moneyC(tv * rv / 36500, cur)}。每月底会自动把利息加进欠款。",
                            fontSize = 12.sp,
                            color = exp,
                        )
                    }
                    if ((initial?.accrued ?: 0.0) > 0) {
                        Text("已累计加入的利息：${moneyC(initial?.accrued ?: 0.0, cur)}", fontSize = 12.sp)
                    }
                }
                if (!debt) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("用总存款计算进度")
                            Text(
                                if (useTotal) "总存款就是进度" else "只算关联到这个目标的记录",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = useTotal, onCheckedChange = { useTotal = it })
                    }
                }
                if (debt || !useTotal) {
                    MoneyField(startText, { startText = it }, if (debt) "之前已还（可选）" else "之前已存（可选）", cur)
                }
                MoneyField(monthlyText, { monthlyText = it }, if (debt) "每月计划还多少" else "每月计划存多少", cur)
                // ---- 完成期限：年 + 月 滚轮 ----
                val nowYm = YearMonth.now()
                val hasDl = deadlineText.isNotBlank()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("设定完成期限")
                        Text(
                            parseYm(deadlineText)?.let { d ->
                                val n = ChronoUnit.MONTHS.between(nowYm, d)
                                "预计完成：${ymCn(d)}（共 $n 个月）"
                            } ?: "不设期限",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = hasDl, onCheckedChange = { on -> deadlineText = if (on) nowYm.plusMonths(12).toString() else "" })
                }
                if (hasDl) {
                    val total = parseYm(deadlineText)?.let { ChronoUnit.MONTHS.between(nowYm, it).toInt() }?.coerceIn(0, 371) ?: 12
                    val yIdx = total / 12
                    val mIdx = total % 12
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WheelPicker((0..30).map { "$it 年" }, yIdx, Modifier.weight(1f)) { y ->
                            val m = (parseYm(deadlineText)?.let { ChronoUnit.MONTHS.between(nowYm, it).toInt() } ?: 0) % 12
                            deadlineText = nowYm.plusMonths((y * 12 + m).toLong().coerceAtLeast(1)).toString()
                        }
                        WheelPicker((0..11).map { "$it 个月" }, mIdx, Modifier.weight(1f)) { m ->
                            val y = (parseYm(deadlineText)?.let { ChronoUnit.MONTHS.between(nowYm, it).toInt() } ?: 0) / 12
                            deadlineText = nowYm.plusMonths((y * 12 + m).toLong().coerceAtLeast(1)).toString()
                        }
                    }
                }
                // ---- 每月还款日：1–31 号 滚轮 ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (debt) "每月还款日" else "每月存款日")
                        Text(
                            if (dueDay in 1..31) "每月 $dueDay 号" else "不设",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = dueDay in 1..31, onCheckedChange = { on -> dueText = if (on) "1" else "" })
                }
                if (dueDay in 1..31) {
                    WheelPicker((1..31).map { "$it 号" }, dueDay - 1, Modifier.fillMaxWidth()) { i -> dueText = (i + 1).toString() }
                }
                if (dueDay in 1..31) {
                    Text("提醒时间", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((label, n) in listOf("当天" to 0, "提前1天" to 1, "提前3天" to 3, "提前7天" to 7)) {
                            FilterChip(selected = before == n, onClick = { before = n }, label = { Text(label, fontSize = 13.sp) })
                        }
                    }
                    Text(
                        "提醒方式（通知 / 闹钟响铃 / Email / 手机日历）在「设置 → 提醒与通知」里选。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (debt) "记一笔「取出」时选这个欠款，就会自动算进还款进度。" else "记一笔「存入」时选这个目标，就会自动算进进度。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (initial != null) {
                    HorizontalDivider()
                    if (confirmDelete) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("删除这个${if (initial.debt) "欠款" else "目标"}？记录会保留", modifier = Modifier.weight(1f), fontSize = 14.sp)
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
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        Goal(
                            id = initial?.id ?: System.currentTimeMillis(),
                            name = name.trim(),
                            debt = debt,
                            target = target,
                            monthly = monthlyText.toDoubleOrNull() ?: 0.0,
                            deadline = parseYm(deadlineText)?.toString() ?: "",
                            startDone = if (!debt && useTotal) 0.0 else startText.toDoubleOrNull() ?: 0.0,
                            useTotal = !debt && useTotal,
                            dueDay = if (dueDay in 1..31) dueDay else 0,
                            remindBefore = before,
                            rate = if (debt) rateText.toDoubleOrNull() ?: 0.0 else 0.0,
                            accrued = initial?.accrued ?: 0.0,
                            accruedUntil = initial?.accruedUntil ?: "",
                            currency = cur,
                        )
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 所有欠款 / 存款目标加起来的总数（换算成结算货币）。 */
@Composable
fun GoalsSummary(goals: List<Goal>, stats: List<GoalStat>, inc: Color, exp: Color) {
    val debts = goals.indices.filter { goals[it].debt }
    val saves = goals.indices.filter { !goals[it].debt }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun b(i: Int, v: Double) = Fx.toBase(v, goals[i].currency)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (debts.isNotEmpty()) {
                val owed = debts.sumOf { b(it, goals[it].target + goals[it].accrued) }
                val paid = debts.sumOf { b(it, stats[it].done) }
                val left = debts.sumOf { b(it, stats[it].remaining) }
                val monthly = debts.sumOf { b(it, goals[it].monthly) }
                val mInt = debts.sumOf { b(it, stats[it].monthlyInterest) }
                val dInt = debts.sumOf { b(it, stats[it].dailyInterest) }
                val accrued = debts.sumOf { b(it, goals[it].accrued) }
                val future = debts.sumOf { b(it, stats[it].futureInterest) }
                val never = debts.any { stats[it].neverPayoff }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💳 欠款合计（${debts.size} 笔）", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(money(left), fontWeight = FontWeight.Bold, color = exp)
                }
                SumLine("已还 / 总共", money(paid) + " / " + money(owed))
                SumLine("每月计划还", money(monthly))
                SumLine("每月利息", money(mInt), exp)
                SumLine("每天利息", money(dInt), exp)
                if (accrued > 0) SumLine("已经产生的利息", money(accrued), exp)
                SumLine("还清前还要付的利息", if (never) "还不完 ⚠️" else "≈ " + money(future), exp)
                if (monthly > 0 && mInt > 0) {
                    Text(
                        "每月还的钱里约 ${(mInt / monthly * 100).toInt()}% 是利息",
                        fontSize = 12.sp,
                        color = muted,
                    )
                }
            }
            if (debts.isNotEmpty() && saves.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 6.dp))
            if (saves.isNotEmpty()) {
                val target = saves.sumOf { b(it, goals[it].target) }
                val done = saves.sumOf { b(it, stats[it].done) }
                val left = saves.sumOf { b(it, stats[it].remaining) }
                val monthly = saves.sumOf { b(it, goals[it].monthly) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎯 存款目标合计（${saves.size} 个）", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(if (target > 0) "${(done / target * 100).toInt()}%" else "", fontWeight = FontWeight.Bold, color = inc)
                }
                SumLine("已存 / 目标", money(done) + " / " + money(target))
                SumLine("还差", money(left))
                SumLine("每月计划存", money(monthly))
            }
        }
    }
}

@Composable
private fun SumLine(label: String, value: String, color: Color = Color.Unspecified) {
    Row {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color)
    }
}
