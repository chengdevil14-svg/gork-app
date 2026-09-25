@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lkcheng.savings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

@Composable
fun ReportScreen(
    entries: List<Entry>,
    accounts: List<Account>,
    subs: List<Sub>,
    goalStats: List<Pair<Goal, GoalStat>>,
    deposits: List<Deposit>,
    inc: Color,
    exp: Color,
    onClose: () -> Unit,
) {
    BackHandler { onClose() }
    var mode by rememberSaveable { mutableStateOf(1) } // 0 日 1 月 2 年
    var dayStr by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val day = LocalDate.parse(dayStr)
    val ym = YearMonth.from(day)
    val yearMode = mode == 2
    val sv = { e: Entry -> statAmt(e, accounts) }
    val real = entries.filter { sv(it) != 0.0 }

    fun inPeriod(d: LocalDate, ref: LocalDate) = when (mode) {
        0 -> d == ref
        2 -> d.year == ref.year
        else -> YearMonth.from(d) == YearMonth.from(ref)
    }
    val prevDay = when (mode) {
        0 -> day.minusDays(1)
        2 -> day.minusYears(1)
        else -> day.minusMonths(1)
    }
    val nextDay = when (mode) {
        0 -> day.plusDays(1)
        2 -> day.plusYears(1)
        else -> day.plusMonths(1)
    }
    val cur = real.filter { inPeriod(it.date, day) }
    val prev = real.filter { inPeriod(it.date, prevDay) }

    val inSum = cur.filter { sv(it) > 0 }.sumOf { sv(it) }
    val outSum = -cur.filter { sv(it) < 0 }.sumOf { sv(it) }
    val prevOut = -prev.filter { sv(it) < 0 }.sumOf { sv(it) }
    val prevIn = prev.filter { sv(it) > 0 }.sumOf { sv(it) }
    val periodName = when (mode) {
        0 -> "${day.monthValue}月${day.dayOfMonth}日 ${weekdayCn(day)}"
        2 -> "${ym.year}年"
        else -> "${ym.year}年${ym.monthValue}月"
    }
    val prevName = when (mode) {
        0 -> "前一天"
        2 -> "去年"
        else -> "上月"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📊 报表", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("按日") })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("按月") })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = mode == 2, onClick = { mode = 2 }, label = { Text("按年") })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { dayStr = prevDay.toString() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一期")
                }
                Text(periodName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { dayStr = nextDay.toString() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一期")
                }
            }
            if (day != LocalDate.now()) {
                androidx.compose.material3.TextButton(onClick = { dayStr = LocalDate.now().toString() }) { Text("回到今天") }
            }

            CostStatsCard(mode, day, cur, sv, subs, accounts, goalStats, deposits, inSum, inc, exp)

            // Totals
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("$periodName · 结算 ${Cur.code}", fontSize = 13.sp)
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("存入", fontSize = 12.sp)
                            Text(money(inSum), color = inc, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("取出", fontSize = 12.sp)
                            Text(money(outSum), color = exp, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("结余", fontSize = 12.sp)
                            val net = inSum - outSum
                            Text(signedMoney(net), color = if (net >= 0) inc else exp, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(compareLine("支出", outSum, prevOut, prevName, higherIsBad = true), fontSize = 13.sp)
                    Text(compareLine("收入", inSum, prevIn, prevName, higherIsBad = false), fontSize = 13.sp)
                    if (inSum > 0) {
                        val rate = (inSum - outSum) / inSum * 100
                        Text("储蓄率 ${rate.toInt()}%（结余 ÷ 存入）", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Trend chart
            if (mode != 0) Text(if (yearMode) "每月支出 / 收入" else "每日支出", fontWeight = FontWeight.Bold)
            if (mode == 0) {
                Text("当天记录", fontWeight = FontWeight.Bold)
                val todays = entries.filter { it.date == day }.sortedByDescending { it.id }
                if (todays.isEmpty()) {
                    Text("这天没有记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        for ((i, e) in todays.withIndex()) {
                            if (i > 0) HorizontalDivider()
                            val v = sv(e)
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.note.ifBlank { e.tag.ifBlank { if (e.amount >= 0) "存入" else "取出" } }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        e.tag.ifBlank { "未分类" } + " · " + accName(accOf(e, accounts), accounts) +
                                            if (v == 0.0) " · 不计入收支" else "",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    signedMoneyC(e.amount, e.currency),
                                    fontWeight = FontWeight.Bold,
                                    color = if (v == 0.0) MaterialTheme.colorScheme.onSurfaceVariant else if (e.amount >= 0) inc else exp,
                                )
                            }
                        }
                    }
                }
            }
            if (mode != 0) Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    if (yearMode) {
                        val outs = (1..12).map { m -> -cur.filter { it.date.monthValue == m && sv(it) < 0 }.sumOf { sv(it) } }
                        val ins = (1..12).map { m -> cur.filter { it.date.monthValue == m && sv(it) > 0 }.sumOf { sv(it) } }
                        BarChart(outs, ins, exp, inc)
                        AxisLabels(listOf("1月", "4月", "7月", "10月", "12月"))
                    } else {
                        val days = ym.lengthOfMonth()
                        val outs = (1..days).map { d -> -cur.filter { it.date.dayOfMonth == d && sv(it) < 0 }.sumOf { sv(it) } }
                        BarChart(outs, null, exp, inc)
                        AxisLabels(listOf("1", "8", "15", "22", "$days"))
                        val maxDay = outs.withIndex().maxByOrNull { it.value }
                        if (maxDay != null && maxDay.value > 0) {
                            Text(
                                "花最多的一天：${ym.monthValue}月${maxDay.index + 1}日 ${money(maxDay.value)} · 日均 ${money(outSum / days)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Category breakdown
            BreakdownCard(
                "支出分类",
                cur.filter { sv(it) < 0 }.groupBy { it.tag.ifBlank { "未分类" } }.mapValues { (_, v) -> -v.sumOf { sv(it) } },
                outSum,
                exp,
            )
            BreakdownCard(
                "收入分类",
                cur.filter { sv(it) > 0 }.groupBy { it.tag.ifBlank { "未分类" } }.mapValues { (_, v) -> v.sumOf { sv(it) } },
                inSum,
                inc,
            )
            // Account breakdown
            BreakdownCard(
                "各账户支出",
                cur.filter { sv(it) < 0 }.groupBy { accName(accOf(it, accounts), accounts) }.mapValues { (_, v) -> -v.sumOf { sv(it) } },
                outSum,
                exp,
            )

            // Top expenses
            val top = cur.filter { sv(it) < 0 }.sortedBy { sv(it) }.take(5)
            if (top.isNotEmpty()) {
                Text("最大的 5 笔支出", fontWeight = FontWeight.Bold)
                Card(shape = RoundedCornerShape(16.dp)) {
                    for ((i, e) in top.withIndex()) {
                        if (i > 0) HorizontalDivider()
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.note.ifBlank { e.tag.ifBlank { "取出" } }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${e.date.monthValue}月${e.date.dayOfMonth}日 · ${e.tag.ifBlank { "未分类" }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(moneyC(abs(e.amount), e.currency), color = exp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (cur.isEmpty()) {
                Text("$periodName 还没有记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

fun compareLine(label: String, now: Double, before: Double, prevName: String, higherIsBad: Boolean): String {
    if (before <= 0.0) return "$label：${prevName}没有数据可比较"
    val diff = now - before
    val pct = abs(diff) / before * 100
    val dir = if (diff >= 0) "多" else "少"
    val mark = when {
        abs(diff) < 0.005 -> "持平"
        (diff > 0) == higherIsBad -> "⚠️"
        else -> "👍"
    }
    return "$label 比$prevName$dir ${money(abs(diff))}（${pct.toInt()}%）$mark"
}

@Composable
fun AxisLabels(labels: List<String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (l in labels) Text(l, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BarChart(a: List<Double>, b: List<Double>?, colorA: Color, colorB: Color) {
    val maxV = maxOf(a.maxOrNull() ?: 0.0, b?.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val grid = MaterialTheme.colorScheme.outlineVariant
    Text("最高 ${money(maxV)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        val n = a.size
        val slot = size.width / n
        val groups = if (b != null) 2 else 1
        val barW = (slot * 0.7f / groups).coerceAtLeast(1f)
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
        drawLine(grid, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 0.5f)
        for (i in 0 until n) {
            val x0 = i * slot + slot * 0.15f
            val ha = (a[i] / maxV * size.height).toFloat()
            if (ha > 0) {
                drawRoundRect(colorA, Offset(x0, size.height - ha), Size(barW, ha), CornerRadius(3f, 3f))
            }
            if (b != null) {
                val hb = (b[i] / maxV * size.height).toFloat()
                if (hb > 0) drawRoundRect(colorB, Offset(x0 + barW, size.height - hb), Size(barW, hb), CornerRadius(3f, 3f))
            }
        }
    }
    if (b != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(10.dp).height(10.dp).clip(RoundedCornerShape(2.dp)).background(colorA))
            Text(" 支出   ", fontSize = 11.sp)
            Box(Modifier.width(10.dp).height(10.dp).clip(RoundedCornerShape(2.dp)).background(colorB))
            Text(" 收入", fontSize = 11.sp)
        }
    }
}

@Composable
fun BreakdownCard(title: String, data: Map<String, Double>, total: Double, color: Color) {
    val rows = data.entries.filter { it.value > 0 }.sortedByDescending { it.value }
    if (rows.isEmpty()) return
    Text(title, fontWeight = FontWeight.Bold)
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val maxV = rows.first().value
            for (r in rows) {
                val pct = if (total > 0) r.value / total * 100 else 0.0
                Column {
                    Row {
                        Text(r.key, modifier = Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${money(r.value)}  ${pct.toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((r.value / maxV).toFloat().coerceIn(0.02f, 1f))
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(color)
                        )
                    }
                }
            }
        }
    }
}


/** Entries that are already counted as fixed costs (auto-recorded subscriptions / top-ups). */
fun isFixedEntry(e: Entry, subs: List<Sub>) =
    e.amount < 0 && (e.tag == "订阅" || e.tag == "交通卡充值" || subs.any { !it.isIncome && it.name == e.note })

@Composable
fun CostStatsCard(
    mode: Int,
    day: LocalDate,
    cur: List<Entry>,
    sv: (Entry) -> Double,
    subs: List<Sub>,
    accounts: List<Account>,
    goalStats: List<Pair<Goal, GoalStat>>,
    deposits: List<Deposit>,
    inSum: Double,
    inc: Color,
    exp: Color,
) {
    // months covered by the period, with how many days of each
    val parts: List<Pair<YearMonth, Int>> = when (mode) {
        0 -> listOf(YearMonth.from(day) to 1)
        2 -> (1..12).map { YearMonth.of(day.year, it) }.map { it to it.lengthOfMonth() }
        else -> YearMonth.from(day).let { listOf(it to it.lengthOfMonth()) }
    }
    val periodDays = parts.sumOf { it.second }
    val costMap = LinkedHashMap<String, Double>()
    var fixedCost = 0.0
    var fixedIncome = 0.0
    for ((m, n) in parts) {
        val (c, i) = dailyLines(subs, accounts, goalStats, deposits, m)
        for (l in c) {
            costMap[l.label] = (costMap[l.label] ?: 0.0) + l.perDay * n
            fixedCost += l.perDay * n
        }
        fixedIncome += i.sumOf { it.perDay } * n
    }
    val variable = -cur.filter { sv(it) < 0 && !isFixedEntry(it, subs) }.sumOf { sv(it) }
    val realCost = fixedCost + variable
    val label = when (mode) {
        0 -> "当天"
        2 -> "今年"
        else -> "本月"
    }
    Text("💡 $label 成本统计", fontWeight = FontWeight.Bold)
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("固定成本（分摊）", fontSize = 12.sp)
                    Text(money(fixedCost), color = exp, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text("日常花费", fontSize = 12.sp)
                    Text(money(variable), color = exp, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text("真实总成本", fontSize = 12.sp)
                    Text(money(realCost), color = exp, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            if (mode != 0) {
                Text("平均每天成本 ${money(realCost / periodDays)}（${money(realCost)} ÷ $periodDays 天）", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            Text("固定收入（分摊）${money(fixedIncome)} · 实际收入 ${money(inSum)}", fontSize = 13.sp)
            val left = inSum - realCost
            Text(
                "按真实成本算结余：" + signedMoney(left),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (left >= 0) inc else exp,
            )
            if (costMap.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 2.dp))
                Text("固定成本明细", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                for ((k, v) in costMap.entries.sortedByDescending { it.value }) {
                    Row {
                        Text(k, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(money(v), fontSize = 13.sp)
                    }
                }
            }
            Text(
                "固定成本 = 房租、订阅、自动扣款、通勤、欠款利息按天分摊；日常花费 = 其他实际支出（已记的房租、订阅不重复算）。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
