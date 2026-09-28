@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.lkcheng.savings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

/** 弹窗里的一行（都来自已有项目，不用另外填写） */
data class AskItem(
    val key: String,
    val name: String,
    val sub: String,
    val amount: Double,
    val currency: String,
    val accountId: Long,
    val tag: String,
    val note: String,
    val checked: Boolean = false,
    val toAccountId: Long = 0L,
    val goalId: Long = 0L,
)

object Ask {
    const val ON = "ask_on"
    const val H = "ask_h"
    const val M = "ask_m"
    const val LAST = "ask_last"
    const val PICK = "ask_pick"

    fun picks(store: Store): Set<String> = store.getS(PICK, "").split("\u0001").filter { it.isNotBlank() }.toSet()
    fun setPicks(store: Store, v: Set<String>) = store.setS(PICK, v.joinToString("\u0001"))

    /** 「今天」从每天几点开始算（默认早上 8 点）。8 点前打开算前一天。 */
    fun askDay(store: Store, now: LocalDateTime = LocalDateTime.now()): LocalDate {
        val h = store.getI(H, 8)
        val m = store.getI(M, 0)
        val t = now.hour * 60 + now.minute
        return if (t < h * 60 + m) now.toLocalDate().minusDays(1) else now.toLocalDate()
    }

    /** 所有可以选的已有项目 */
    fun candidates(accounts: List<Account>, subs: List<Sub>, goals: List<Goal>, entries: List<Entry>): List<AskItem> {
        val out = ArrayList<AskItem>()
        val live = accounts.filter { !it.archived }
        for (a in live.filter { it.type == TYPE_CARD }) {
            val fare = if (a.fare > 0) a.fare else entries.filter { it.accountId == a.id && it.amount < 0 && !it.isTransfer }.maxByOrNull { it.date }?.let { -it.amount } ?: 0.0
            out.add(AskItem("c" + a.id, "🚇 ${a.name} 车费", "交通卡", fare, a.currency, a.id, "交通", "通勤"))
        }
        for (s in subs.filter { it.active && !it.isIncome }) {
            out.add(
                AskItem(
                    "s" + s.id, "🔁 " + s.name, "固定支出 / 订阅", s.amount, s.currency, s.accountId,
                    if (s.toAccountId != 0L) "交通卡充值" else "订阅", s.name, toAccountId = s.toAccountId,
                )
            )
        }
        for (g in goals.filter { it.debt }) {
            out.add(AskItem("g" + g.id, "💳 还 " + g.name, "欠款", g.monthly, g.currency, 0L, "还款", "还款 " + g.name, goalId = g.id))
        }
        val since = LocalDate.now().minusDays(90)
        val common = entries
            .filter { it.amount < 0 && !it.isTransfer && it.note.isNotBlank() && it.goalId == 0L && !it.date.isBefore(since) }
            .groupBy { it.note.trim() }
            .filter { it.value.size >= 2 }
            .entries.sortedByDescending { it.value.size }
            .take(12)
        for ((note, list) in common) {
            val last = list.maxBy { it.date }
            if (out.any { it.note == note }) continue
            out.add(AskItem("n" + note, note, "常记（${list.size} 次）", -last.amount, last.currency, last.accountId, last.tag, note))
        }
        return out
    }

    fun items(store: Store, accounts: List<Account>, subs: List<Sub>, goals: List<Goal>, entries: List<Entry>): List<AskItem> {
        val p = picks(store)
        return candidates(accounts, subs, goals, entries).filter { it.key in p || (it.key.startsWith("c") && accounts.any { a -> "c" + a.id == it.key && a.askDaily && a.commuteOn }) }
    }

    /** 今天还没问过、且有选项目 → 返回要问的日期 */
    fun pending(store: Store, accounts: List<Account>, subs: List<Sub>, goals: List<Goal>, entries: List<Entry>): LocalDate? {
        if (!store.getB(ON, true)) return null
        val day = askDay(store)
        if (store.getS(LAST, "") == day.toString()) return null
        if (items(store, accounts, subs, goals, entries).isEmpty()) return null
        return day
    }

    fun markDone(store: Store, day: LocalDate) = store.setS(LAST, day.toString())
}

@Composable
fun DailyAskDialog(
    day: LocalDate,
    items: List<AskItem>,
    accounts: List<Account>,
    onConfirm: (List<Entry>) -> Unit,
    onSkip: () -> Unit,
    onLater: () -> Unit,
) {
    val checked = remember { mutableStateListOf<String>().apply { addAll(items.filter { it.checked }.map { it.key }) } }
    val amounts = remember { mutableStateListOf<String>().apply { addAll(items.map { if (it.amount > 0) plainNumber(it.amount) else "" }) } }
    val today = day == LocalDate.now()
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(if (today) "☀️ 今天要扣款吗？" else "🌙 ${day.monthValue}月${day.dayOfMonth}日 要扣款吗？") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("打钩的才记账，金额可以改", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                items.forEachIndexed { i, it ->
                    val on = it.key in checked
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (on) checked.remove(it.key) else checked.add(it.key) },
                    ) {
                        Checkbox(checked = on, onCheckedChange = { v -> if (v) checked.add(it.key) else checked.remove(it.key) })
                        Column(Modifier.weight(1f)) {
                            Text(it.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            val an = accounts.firstOrNull { a -> a.id == it.accountId }?.name
                            if (an != null) Text(an, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedTextField(
                            value = amounts[i],
                            onValueChange = { v -> amounts[i] = v.filter { c -> c.isDigit() || c == '.' } },
                            singleLine = true,
                            prefix = { Text(it.currency + " ", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(118.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                var seed = System.currentTimeMillis()
                val out = items.mapIndexedNotNull { i, it ->
                    val amt = amounts[i].toDoubleOrNull() ?: 0.0
                    if (it.key !in checked || amt <= 0) {
                        null
                    } else {
                        Entry(
                            id = seed++,
                            date = day,
                            amount = -amt,
                            note = it.note,
                            tag = it.tag,
                            currency = it.currency,
                            accountId = it.accountId,
                            toAccountId = it.toAccountId,
                            goalId = it.goalId,
                        )
                    }
                }
                onConfirm(out)
            }) { Text("确认扣款（${checked.size}）") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onLater) { Text("稍后") }
                TextButton(onClick = onSkip) { Text("今天都不用") }
            }
        },
    )
}

/** 设置 → 打开 app 时提醒 → 每日询问扣款（从已有项目选） */
@Composable
fun DailyAskSettings() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val cands = remember { Ask.candidates(store.loadAccounts(), store.loadSubs(), store.loadGoals(), store.loadEntries()) }
    var picks by remember { mutableStateOf(Ask.picks(store)) }
    var on by remember { mutableStateOf(store.getB(Ask.ON, true)) }
    var h by remember { mutableStateOf(store.getI(Ask.H, 8)) }
    var m by remember { mutableStateOf(store.getI(Ask.M, 0)) }

    Text(
        "每日询问扣款",
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
    Card(shape = RoundedCornerShape(16.dp)) {
        SwitchRow("每天第一次打开 app 问「今天要扣吗？」", "你自己打钩要扣哪些", on) {
            on = it
            store.setB(Ask.ON, it)
        }
        HorizontalDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    pickTime(ctx, h, m) { hh, mm ->
                        h = hh
                        m = mm
                        store.setI(Ask.H, hh)
                        store.setI(Ask.M, mm)
                    }
                }
                .padding(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("每天几点重新问")
                Text("这个时间前打开算前一天，不会凌晨就把今天问掉", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(hm(h, m), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
    Text(
        "选要每天问的项目（都是你已有的，不用另外填）",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
    Card(shape = RoundedCornerShape(16.dp)) {
        if (cands.isEmpty()) {
            Text(
                "还没有可以选的项目。加交通卡账户、固定支出 / 订阅、欠款，或同一样东西记过 2 次以上，就会出现在这里。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        var lastGroup = ""
        cands.forEachIndexed { i, c ->
            val group = c.sub.substringBefore("（")
            if (group != lastGroup) {
                if (i > 0) HorizontalDivider()
                Text(
                    group,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp),
                )
                lastGroup = group
            }
            val sel = c.key in picks
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        picks = if (sel) picks - c.key else picks + c.key
                        Ask.setPicks(store, picks)
                    }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Checkbox(checked = sel, onCheckedChange = { v ->
                    picks = if (v) picks + c.key else picks - c.key
                    Ask.setPicks(store, picks)
                })
                Text(c.name, modifier = Modifier.weight(1f))
                Text(
                    if (c.amount > 0) moneyC(c.amount, c.currency) else "当天填",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
    Text(
        "交通卡选了之后，建议到「账户 → 交通卡」把通勤自动扣款关掉，免得重复扣。",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}
