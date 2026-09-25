@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.lkcheng.savings

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun SearchScreen(
    entries: List<Entry>,
    accounts: List<Account>,
    inc: Color,
    exp: Color,
    onOpen: (Entry) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler { onClose() }
    val ctx = LocalContext.current
    val today = LocalDate.now()
    var fromStr by rememberSaveable { mutableStateOf(YearMonth.now().atDay(1).toString()) }
    var toStr by rememberSaveable { mutableStateOf(today.toString()) }
    var q by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(0) } // 0 全部 1 存入 2 取出 3 转账
    var cat by rememberSaveable { mutableStateOf("") }
    var acc by rememberSaveable { mutableLongStateOf(0L) }
    val from = LocalDate.parse(fromStr)
    val to = LocalDate.parse(toStr)

    val results = entries.filter { e ->
        !e.date.isBefore(from) && !e.date.isAfter(to) &&
            when (kind) {
                1 -> !e.isTransfer && e.amount > 0
                2 -> !e.isTransfer && e.amount < 0
                3 -> e.isTransfer
                else -> true
            } &&
            (cat.isEmpty() || e.tag == cat) &&
            (acc == 0L || accOf(e, accounts) == acc || e.toAccountId == acc) &&
            (q.isBlank() || listOf(e.note, e.tag, accName(accOf(e, accounts), accounts), plainNumber(kotlin.math.abs(e.amount)))
                .any { it.contains(q.trim(), ignoreCase = true) })
    }.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.id })
    val inSum = results.sumOf { maxOf(0.0, statAmt(it, accounts)) }
    val outSum = -results.sumOf { minOf(0.0, statAmt(it, accounts)) }
    val cats = entries.map { it.tag }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    fun pick(cur: LocalDate, set: (LocalDate) -> Unit) {
        DatePickerDialog(ctx, { _, y, m, d -> set(LocalDate.of(y, m + 1, d)) }, cur.year, cur.monthValue - 1, cur.dayOfMonth).show()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("🔍 查询", fontWeight = FontWeight.Bold) }) }) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    label = { Text("关键字（备注、分类、账户、金额）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("日期", fontSize = 13.sp)
                    TextButton(onClick = { pick(from) { fromStr = it.toString() } }) { Text("📅 $fromStr") }
                    Text("至", fontSize = 13.sp)
                    TextButton(onClick = { pick(to) { toStr = it.toString() } }) { Text("📅 $toStr") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val ym = YearMonth.now()
                    val quick = listOf(
                        "今天" to (today to today),
                        "本周" to (today.with(DayOfWeek.MONDAY) to today),
                        "本月" to (ym.atDay(1) to today),
                        "上月" to (ym.minusMonths(1).atDay(1) to ym.minusMonths(1).atEndOfMonth()),
                        "今年" to (LocalDate.of(today.year, 1, 1) to today),
                        "全部" to (LocalDate.of(2000, 1, 1) to today),
                    )
                    for ((label, r) in quick) {
                        FilterChip(
                            selected = from == r.first && to == r.second,
                            onClick = {
                                fromStr = r.first.toString()
                                toStr = r.second.toString()
                            },
                            label = { Text(label, fontSize = 13.sp) },
                        )
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((i, l) in listOf("全部", "存入", "取出", "转账").withIndex()) {
                        FilterChip(selected = kind == i, onClick = { kind = i }, label = { Text(l, fontSize = 13.sp) })
                    }
                }
            }
            if (accounts.size > 1) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = acc == 0L, onClick = { acc = 0L }, label = { Text("所有账户", fontSize = 12.sp) })
                        for (a in accounts) {
                            FilterChip(selected = acc == a.id, onClick = { acc = a.id }, label = { Text(accIcon(a) + " " + a.name, fontSize = 12.sp) })
                        }
                    }
                }
            }
            if (cats.isNotEmpty()) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = cat.isEmpty(), onClick = { cat = "" }, label = { Text("所有分类", fontSize = 12.sp) })
                        for (c in cats.take(20)) {
                            FilterChip(selected = cat == c, onClick = { cat = if (cat == c) "" else c }, label = { Text(c, fontSize = 12.sp) })
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("${results.size} 笔", fontSize = 12.sp)
                            Text("结余 " + signedMoney(inSum - outSum), fontWeight = FontWeight.Bold, color = if (inSum >= outSum) inc else exp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("存入", fontSize = 12.sp)
                            Text(money(inSum), color = inc, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("取出", fontSize = 12.sp)
                            Text(money(outSum), color = exp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            var lastDate: LocalDate? = null
            for (e in results.take(500)) {
                if (e.date != lastDate) {
                    val d = e.date
                    val dayNet = results.filter { it.date == d }.sumOf { statAmt(it, accounts) }
                    item(key = "h$d") {
                        Row(Modifier.padding(top = 6.dp)) {
                            Text("${d.year}年${d.monthValue}月${d.dayOfMonth}日 ${weekdayCn(d)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text(signedMoney(dayNet), fontSize = 13.sp, color = if (dayNet >= 0) inc else exp)
                        }
                    }
                    lastDate = d
                }
                item(key = e.id) {
                    val v = statAmt(e, accounts)
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(e) },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    e.note.ifBlank { if (e.isTransfer) "转账" else e.tag.ifBlank { if (e.amount >= 0) "存入" else "取出" } },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOf(e.tag, accName(accOf(e, accounts), accounts)).filter { it.isNotBlank() }.joinToString(" · ") +
                                        if (e.isTransfer) " → " + accName(e.toAccountId, accounts) else "",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                if (e.isTransfer) moneyC(kotlin.math.abs(e.amount), e.currency) else signedMoneyC(e.amount, e.currency),
                                fontWeight = FontWeight.Bold,
                                color = if (v == 0.0) MaterialTheme.colorScheme.onSurfaceVariant else if (e.amount >= 0) inc else exp,
                            )
                        }
                    }
                }
            }
            if (results.size > 500) {
                item { Text("只显示最近 500 笔，请缩小日期范围", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (results.isEmpty()) {
                item { Text("没有符合条件的记录", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
