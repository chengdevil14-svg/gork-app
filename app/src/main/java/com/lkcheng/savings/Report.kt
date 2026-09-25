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
fun ReportScreen(entries: List<Entry>, accounts: List<Account>, inc: Color, exp: Color, onClose: () -> Unit) {
    BackHandler { onClose() }
    var yearMode by rememberSaveable { mutableStateOf(false) }
    var ymStr by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val ym = YearMonth.parse(ymStr)
    val real = entries.filter { !it.isTransfer }

    fun inPeriod(d: LocalDate, y: YearMonth) = if (yearMode) d.year == y.year else YearMonth.from(d) == y
    val cur = real.filter { inPeriod(it.date, ym) }
    val prevYm = if (yearMode) ym.minusYears(1) else ym.minusMonths(1)
    val prev = real.filter { inPeriod(it.date, prevYm) }

    val inSum = cur.filter { it.amount > 0 }.sumOf { it.base }
    val outSum = -cur.filter { it.amount < 0 }.sumOf { it.base }
    val prevOut = -prev.filter { it.amount < 0 }.sumOf { it.base }
    val prevIn = prev.filter { it.amount > 0 }.sumOf { it.base }
    val periodName = if (yearMode) "${ym.year}年" else "${ym.year}年${ym.monthValue}月"
    val prevName = if (yearMode) "去年" else "上月"

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
                FilterChip(selected = !yearMode, onClick = { yearMode = false }, label = { Text("按月") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = yearMode, onClick = { yearMode = true }, label = { Text("按年") })
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { ymStr = prevYm.toString() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一期")
                }
                Text(periodName, fontWeight = FontWeight.Bold)
                IconButton(onClick = { ymStr = (if (yearMode) ym.plusYears(1) else ym.plusMonths(1)).toString() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一期")
                }
            }

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
            Text(if (yearMode) "每月支出 / 收入" else "每日支出", fontWeight = FontWeight.Bold)
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    if (yearMode) {
                        val outs = (1..12).map { m -> -cur.filter { it.date.monthValue == m && it.amount < 0 }.sumOf { it.base } }
                        val ins = (1..12).map { m -> cur.filter { it.date.monthValue == m && it.amount > 0 }.sumOf { it.base } }
                        BarChart(outs, ins, exp, inc)
                        AxisLabels(listOf("1月", "4月", "7月", "10月", "12月"))
                    } else {
                        val days = ym.lengthOfMonth()
                        val outs = (1..days).map { d -> -cur.filter { it.date.dayOfMonth == d && it.amount < 0 }.sumOf { it.base } }
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
                cur.filter { it.amount < 0 }.groupBy { it.tag.ifBlank { "未分类" } }.mapValues { (_, v) -> -v.sumOf { it.base } },
                outSum,
                exp,
            )
            BreakdownCard(
                "收入分类",
                cur.filter { it.amount > 0 }.groupBy { it.tag.ifBlank { "未分类" } }.mapValues { (_, v) -> v.sumOf { it.base } },
                inSum,
                inc,
            )
            // Account breakdown
            BreakdownCard(
                "各账户支出",
                cur.filter { it.amount < 0 }.groupBy { accName(accOf(it, accounts), accounts) }.mapValues { (_, v) -> -v.sumOf { it.base } },
                outSum,
                exp,
            )

            // Top expenses
            val top = cur.filter { it.amount < 0 }.sortedBy { it.base }.take(5)
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
