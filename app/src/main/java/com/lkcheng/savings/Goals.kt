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
)

fun parseYm(s: String): YearMonth? = try {
    if (s.isBlank()) null else YearMonth.parse(s.trim())
} catch (e: Exception) {
    null
}

fun ymCn(ym: YearMonth) = "${ym.year}年${ym.monthValue}月"

/** How much an entry counts toward a goal: payments (取出) count for debts, deposits for savings. */
fun contribution(g: Goal, e: Entry) = if (g.debt) -e.amount else e.amount

fun calcGoal(g: Goal, entries: List<Entry>, total: Double, now: YearMonth = YearMonth.now()): GoalStat {
    val linked = entries.filter { it.goalId == g.id }
    val byTotal = !g.debt && g.useTotal
    val done = if (byTotal) total else g.startDone + linked.sumOf { contribution(g, it) }
    val remaining = max(0.0, g.target - done)
    val pct = if (g.target > 0) (done / g.target).coerceIn(0.0, 1.0) else 0.0
    val thisMonth = if (byTotal) {
        entries.filter { YearMonth.from(it.date) == now }.sumOf { it.amount }
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
        monthsLeft = ceil(rest / g.monthly - 1e-9).toInt()
        finish = now.plusMonths(monthsLeft.toLong())
    }
    val dl = parseYm(g.deadline)
    val monthsToDl = if (dl != null) ChronoUnit.MONTHS.between(now, dl) else 0L
    val need = if (dl != null && remaining > 0) {
        if (monthsToDl <= 0) remaining else remaining / monthsToDl
    } else {
        null
    }
    return GoalStat(done, remaining, pct, thisMonth, linked.size, finish, monthsLeft, dl, monthsToDl, need)
}

@Composable
fun GoalCard(g: Goal, s: GoalStat, inc: Color, exp: Color, onClick: () -> Unit) {
    val verb = if (g.debt) "还" else "存"
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
                    (if (g.debt) "已还 " else "已存 ") + money(s.done),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Text(" / " + money(g.target), color = muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
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
            if (s.remaining <= 0.0) {
                Text(if (g.debt) "🎉 已经还清！" else "🎉 目标达成！", color = inc, fontWeight = FontWeight.Bold)
            } else {
                Text(
                    (if (g.debt) "还欠 " else "还差 ") + money(s.remaining),
                    fontWeight = FontWeight.Medium,
                )
                if (g.monthly > 0) {
                    val left = g.monthly - s.thisMonth
                    Text(
                        when {
                            left < 0 -> "本月已$verb ${money(s.thisMonth)}，比计划多$verb ${money(-left)} 👍"
                            left == 0.0 -> "本月已$verb ${money(s.thisMonth)}，本月计划完成 ✅"
                            else -> "本月已$verb ${money(s.thisMonth)}，还差 ${money(left)} 达到每月 ${money(g.monthly)}"
                        },
                        fontSize = 13.sp,
                    )
                    val f = s.finish
                    if (f != null) {
                        Text(
                            "按每月 ${money(g.monthly)}：预计 ${ymCn(f)}${if (g.debt) "还清" else "存够"}" +
                                if (s.monthsLeft > 0) "（再 ${s.monthsLeft} 个月）" else "（就在本月）",
                            fontSize = 13.sp,
                        )
                    }
                } else {
                    Text(
                        "本月已$verb ${money(s.thisMonth)} · 设置每月计划就能算出完成时间",
                        fontSize = 13.sp,
                        color = muted,
                    )
                }
                val dl = s.deadline
                val need = s.needPerMonth
                if (dl != null && need != null) {
                    Text(
                        if (s.monthsToDeadline <= 0) {
                            "期限 ${ymCn(dl)}：本月内需$verb ${money(s.remaining)}"
                        } else {
                            "要在 ${ymCn(dl)} 前完成：从下个月起每月需$verb ${money(need)}"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (g.monthly > 0 && need > g.monthly) exp else MaterialTheme.colorScheme.onSurface,
                    )
                }
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
fun MoneyField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> onChange(s.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        prefix = { Text("$") },
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
    var confirmDelete by remember { mutableStateOf(false) }

    val target = targetText.toDoubleOrNull() ?: 0.0
    val deadlineOk = deadlineText.isBlank() || parseYm(deadlineText) != null
    val valid = name.isNotBlank() && target > 0 && deadlineOk

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
                MoneyField(targetText, { targetText = it }, if (debt) "欠款总额" else "目标金额")
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
                    MoneyField(startText, { startText = it }, if (debt) "之前已还（可选）" else "之前已存（可选）")
                }
                MoneyField(monthlyText, { monthlyText = it }, if (debt) "每月计划还多少" else "每月计划存多少")
                OutlinedTextField(
                    value = deadlineText,
                    onValueChange = { deadlineText = it.take(7) },
                    label = { Text("完成期限（可选，例 2027-06）") },
                    isError = !deadlineOk,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val now = YearMonth.now()
                    for ((label, n) in listOf("半年" to 6L, "1年" to 12L, "2年" to 24L, "3年" to 36L)) {
                        val v = now.plusMonths(n).toString()
                        FilterChip(selected = deadlineText == v, onClick = { deadlineText = v }, label = { Text(label, fontSize = 13.sp) })
                    }
                    FilterChip(selected = deadlineText.isBlank(), onClick = { deadlineText = "" }, label = { Text("不设", fontSize = 13.sp) })
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
