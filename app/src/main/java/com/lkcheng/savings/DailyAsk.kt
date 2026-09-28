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

/** 每天问一次要不要扣的项目（饮料、早餐…） */
data class DailyAsk(
    val id: Long,
    val name: String,
    val amount: Double,
    val accountId: Long = 0L,
    val currency: String = "SGD",
    val tag: String = "",
    val checked: Boolean = true, // 弹窗里默认打钩
    val active: Boolean = true,
)

/** 弹窗里的一行 */
data class AskItem(
    val key: String,
    val name: String,
    val amount: Double,
    val currency: String,
    val accountId: Long,
    val tag: String,
    val note: String,
    val checked: Boolean,
)

object Ask {
    const val ON = "ask_on"
    const val H = "ask_h"
    const val M = "ask_m"
    const val LAST = "ask_last"

    fun load(store: Store): List<DailyAsk> = try {
        val arr = JSONArray(store.getS("daily_asks", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            DailyAsk(
                id = o.getLong("id"),
                name = o.optString("name", ""),
                amount = o.optDouble("amount", 0.0),
                accountId = o.optLong("accountId", 0L),
                currency = o.optString("currency", "SGD"),
                tag = o.optString("tag", ""),
                checked = o.optBoolean("checked", true),
                active = o.optBoolean("active", true),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(store: Store, list: List<DailyAsk>) {
        val arr = JSONArray()
        for (a in list) {
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("name", a.name)
                    .put("amount", a.amount)
                    .put("accountId", a.accountId)
                    .put("currency", a.currency)
                    .put("tag", a.tag)
                    .put("checked", a.checked)
                    .put("active", a.active)
            )
        }
        store.setS("daily_asks", arr.toString())
    }

    /** 「今天」从每天几点开始算（默认早上 8 点）。8 点前打开算前一天。 */
    fun askDay(store: Store, now: LocalDateTime = LocalDateTime.now()): LocalDate {
        val h = store.getI(H, 8)
        val m = store.getI(M, 0)
        val t = now.hour * 60 + now.minute
        return if (t < h * 60 + m) now.toLocalDate().minusDays(1) else now.toLocalDate()
    }

    fun items(store: Store, accounts: List<Account>): List<AskItem> {
        val out = ArrayList<AskItem>()
        for (a in accounts.filter { !it.archived && it.type == TYPE_CARD && it.commuteOn && it.askDaily && it.fare > 0 }) {
            out.add(AskItem("c" + a.id, "🚇 ${a.name} 通勤", a.fare, a.currency, a.id, "交通", "通勤", true))
        }
        for (d in load(store).filter { it.active && it.amount > 0 }) {
            out.add(AskItem("d" + d.id, d.name, d.amount, d.currency, d.accountId, d.tag, d.name, d.checked))
        }
        return out
    }

    /** 今天还没问过、且有项目 → 返回要问的日期 */
    fun pending(store: Store, accounts: List<Account>): LocalDate? {
        if (!store.getB(ON, true)) return null
        val day = askDay(store)
        if (store.getS(LAST, "") == day.toString()) return null
        if (items(store, accounts).isEmpty()) return null
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
    val amounts = remember { mutableStateListOf<String>().apply { addAll(items.map { plainNumber(it.amount) }) } }
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
                Text("打钩的会记成支出，金额可以改", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** 设置 → 打开 app 时提醒 → 每日询问扣款 */
@Composable
fun DailyAskSettings() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val accounts = remember { store.loadAccounts().filter { !it.archived } }
    val list = remember { mutableStateListOf<DailyAsk>().apply { addAll(Ask.load(store)) } }
    var on by remember { mutableStateOf(store.getB(Ask.ON, true)) }
    var h by remember { mutableStateOf(store.getI(Ask.H, 8)) }
    var m by remember { mutableStateOf(store.getI(Ask.M, 0)) }
    var editing by remember { mutableStateOf<DailyAsk?>(null) }
    fun persist() = Ask.save(store, list.toList())

    Text(
        "每日询问扣款",
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
    Card(shape = RoundedCornerShape(16.dp)) {
        SwitchRow("每天第一次打开 app 问「今天要扣吗？」", "比如每日饮料、早餐、交通卡", on) {
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
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(top = 12.dp)) {
        val cards = accounts.filter { it.type == TYPE_CARD && it.commuteOn && it.askDaily }
        for (c in cards) {
            Row(Modifier.padding(16.dp)) {
                Text("🚇 ${c.name} 通勤", modifier = Modifier.weight(1f))
                Text(moneyC(c.fare, c.currency), fontWeight = FontWeight.Medium)
            }
            HorizontalDivider()
        }
        list.forEach { d ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editing = d }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(d.name + if (d.active) "" else "（已停用）", fontWeight = FontWeight.Medium)
                    Text(
                        (accounts.firstOrNull { it.id == d.accountId }?.name ?: "默认账户") + if (d.checked) " · 默认打钩" else " · 默认不打钩",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(moneyC(d.amount, d.currency), fontWeight = FontWeight.Medium)
            }
            HorizontalDivider()
        }
        TextButton(
            onClick = { editing = DailyAsk(id = 0L, name = "", amount = 0.0, accountId = accounts.firstOrNull()?.id ?: 0L, currency = accounts.firstOrNull()?.currency ?: Cur.code, tag = "饮食") },
            modifier = Modifier.padding(8.dp),
        ) { Text("＋ 新增每日项目（饮料、早餐…）") }
    }
    Text(
        "交通卡：到「账户 → 交通卡 → 通勤自动扣款」打开「每天打开 app 时问我」，就不用设日期。",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )

    editing?.let { d0 ->
        DailyAskDialog2(
            d0, accounts,
            onDismiss = { editing = null },
            onDelete = if (d0.id == 0L) null else {
                {
                    list.removeAll { it.id == d0.id }
                    persist()
                    editing = null
                }
            },
            onSave = { d ->
                if (d0.id == 0L) {
                    list.add(d.copy(id = System.currentTimeMillis()))
                } else {
                    val i = list.indexOfFirst { it.id == d0.id }
                    if (i >= 0) list[i] = d
                }
                persist()
                editing = null
            },
        )
    }
}

@Composable
private fun DailyAskDialog2(
    d0: DailyAsk,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (DailyAsk) -> Unit,
) {
    var name by remember { mutableStateOf(d0.name) }
    var amt by remember { mutableStateOf(if (d0.amount > 0) plainNumber(d0.amount) else "") }
    var acc by remember { mutableStateOf(d0.accountId) }
    var tag by remember { mutableStateOf(d0.tag) }
    var checked by remember { mutableStateOf(d0.checked) }
    var active by remember { mutableStateOf(d0.active) }
    val cur = accounts.firstOrNull { it.id == acc }?.currency ?: d0.currency
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (d0.id == 0L) "新增每日项目" else "编辑每日项目") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("名称（如：每日饮料）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    amt,
                    { v -> amt = v.filter { it.isDigit() || it == '.' } },
                    label = { Text("每次多少") },
                    prefix = { Text("$cur ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (accounts.isNotEmpty()) {
                    Text("从哪个账户扣", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (a in accounts) FilterChip(selected = acc == a.id, onClick = { acc = a.id }, label = { Text(a.name) })
                    }
                }
                Text("标签", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (t in listOf("饮食", "交通", "购物", "日常", "娱乐")) {
                        FilterChip(selected = tag == t, onClick = { tag = if (tag == t) "" else t }, label = { Text(t) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("弹窗里默认打钩", modifier = Modifier.weight(1f))
                    Switch(checked = checked, onCheckedChange = { checked = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用", modifier = Modifier.weight(1f))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
                if (onDelete != null) {
                    OutlinedButton(onClick = onDelete) { Text("删除这个项目") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (amt.toDoubleOrNull() ?: 0.0) > 0,
                onClick = {
                    onSave(
                        d0.copy(
                            name = name.trim(),
                            amount = amt.toDoubleOrNull() ?: 0.0,
                            accountId = acc,
                            currency = cur,
                            tag = tag,
                            checked = checked,
                            active = active,
                        )
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
