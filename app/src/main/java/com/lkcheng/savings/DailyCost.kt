package com.lkcheng.savings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

data class CostLine(val label: String, val perDay: Double, val detail: String)

/** Everything expressed per day for the current month, in settlement currency. */
fun dailyLines(
    subs: List<Sub>,
    accounts: List<Account>,
    goals: List<Pair<Goal, GoalStat>>,
    deposits: List<Deposit>,
    ym: YearMonth = YearMonth.now(),
): Pair<List<CostLine>, List<CostLine>> {
    val days = ym.lengthOfMonth()
    val costs = ArrayList<CostLine>()
    val incomes = ArrayList<CostLine>()
    val cycleTxt = { s: Sub -> CYCLE_NAMES.getOrElse(s.cycle) { "每月" } }
    for (s in subs.filter { it.active }) {
        val m = monthlyCost(s)
        val line = CostLine(s.name, m / days, "${cycleTxt(s)} ${moneyC(s.amount, s.currency)} → 每月 ${money(m)} ÷ $days 天")
        if (s.isIncome) incomes.add(line) else costs.add(line)
    }
    // commute on transit cards (only if no automatic top-up already covers that card)
    for (a in accounts.filter { it.type == TYPE_CARD && it.commuteOn && it.fare > 0 && !it.archived }) {
        if (subs.any { it.active && it.toAccountId == a.id }) continue
        val work = (1..days).count { isWorkDay(a, ym.atDay(it)) }
        val m = Fx.toBase(a.fare * work, a.currency)
        costs.add(CostLine("通勤（${a.name}）", m / days, "$work 个上班日 × ${moneyC(a.fare, a.currency)} ÷ $days 天"))
    }
    for ((g, st) in goals) {
        if (g.debt && g.rate > 0 && st.remaining > 0) {
            costs.add(CostLine("${g.name} 利息", st.dailyInterest, "欠 ${money(st.remaining)} × ${pct(g.rate)} ÷ 365"))
        }
    }
    for (d in deposits.filter { it.active }) {
        val mat = maturityOf(d)
        if (mat != null && mat.isBefore(LocalDate.now())) continue
        val perDay = if (d.daily > 0) d.daily else d.principal * d.rate / 100.0 / 365.0
        if (perDay <= 0) continue
        incomes.add(
            CostLine(
                "${d.name} 利息",
                Fx.toBase(perDay, d.currency),
                if (d.daily > 0) "你填的每日利息" else "${moneyC(d.principal, d.currency)} × ${pct(d.rate)} ÷ 365",
            )
        )
    }
    return costs to incomes
}

@androidx.compose.runtime.Composable
fun DailyCostCard(
    subs: List<Sub>,
    accounts: List<Account>,
    goals: List<Pair<Goal, GoalStat>>,
    deposits: List<Deposit>,
    monthOutSoFar: Double,
    inc: Color,
    exp: Color,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val (costs, incomes) = dailyLines(subs, accounts, goals, deposits)
    val ym = YearMonth.now()
    val days = ym.lengthOfMonth()
    val costDay = costs.sumOf { it.perDay }
    val incDay = incomes.sumOf { it.perDay }
    val net = incDay - costDay
    val dayOfMonth = LocalDate.now().dayOfMonth
    val actualAvg = monthOutSoFar / dayOfMonth
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text("${ym.year}年${ym.monthValue}月 · 共 $days 天", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { open = !open }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(if (open) "收起" else "明细", fontSize = 13.sp)
                }
            }
            if (costs.isEmpty() && incomes.isEmpty()) {
                Text(
                    "在「固定收支」加入房租、订阅、工资，在欠款填年利率，就能算出你每天的固定成本和收入。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("每天固定成本", fontSize = 12.sp)
                        Text(money(costDay), color = exp, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("每天固定收入", fontSize = 12.sp)
                        Text(money(incDay), color = inc, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("每天净额", fontSize = 12.sp)
                        Text(signedMoney(net), color = if (net >= 0) inc else exp, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
                Text(
                    "本月固定成本 ${money(costDay * days)} · 固定收入 ${money(incDay * days)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "本月实际日均支出 ${money(actualAvg)}（${money(monthOutSoFar)} ÷ $dayOfMonth 天）",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            if (open) {
                if (costs.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("固定成本（每天）", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = exp)
                    for (c in costs.sortedByDescending { it.perDay }) LineRow(c)
                }
                if (incomes.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("固定收入（每天）", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = inc)
                    for (c in incomes.sortedByDescending { it.perDay }) LineRow(c)
                }
            }
        }
    }
}

@Composable
private fun LineRow(c: CostLine) {
    Column {
        Row {
            Text(c.label, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(money(c.perDay), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Text(c.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

data class Todo(val text: String, val action: () -> Unit)

@Composable
fun TodoCard(todos: List<Todo>) {
    if (todos.isEmpty()) return
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("📝 还没填写（${todos.size}）", fontWeight = FontWeight.Bold)
            Text("填好这些，每日成本和余额会更准。点一下直接去填。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (t in todos) {
                TextButton(onClick = t.action, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                    Text("○ " + t.text, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
