@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.lkcheng.savings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth

val ACCOUNT_TYPES = listOf("现金", "银行卡", "信用卡", "电子钱包", "其他", "交通卡 / 储值卡")
val ACCOUNT_ICONS = listOf("💵", "🏦", "💳", "📱", "👛", "🚇")
val ACCOUNT_PRESETS = mapOf(
    1 to listOf("DBS", "POSB", "OCBC", "UOB", "Maybank", "CIMB", "Public Bank"),
    2 to listOf("DBS 信用卡", "UOB 信用卡", "Citi 信用卡", "Maybank 信用卡", "OCBC 信用卡"),
    3 to listOf("PayNow", "PayLah!", "GrabPay", "Touch 'n Go", "ShopeePay", "Boost"),
    5 to listOf("EZ-Link", "SimplyGo", "NETS FlashPay", "Touch 'n Go 卡", "MyRapid"),
)

fun accIcon(a: Account) = ACCOUNT_ICONS.getOrElse(a.type) { "👛" }

/** Which account an entry belongs to (0 = first account). */
fun accOf(e: Entry, accounts: List<Account>): Long =
    if (e.accountId != 0L && accounts.any { it.id == e.accountId }) e.accountId else accounts.firstOrNull()?.id ?: 0L

/** Balance in the account's own currency. */
fun balanceOf(a: Account, entries: List<Entry>, accounts: List<Account>): Double {
    var b = a.opening
    for (e in entries) {
        if (accOf(e, accounts) == a.id) b += Fx.convert(e.amount, e.currency, a.currency)
        if (e.toAccountId == a.id) b += Fx.convert(-e.amount, e.currency, a.currency)
    }
    return b
}

/** Net worth in the settlement currency = all account balances. */
fun netWorth(entries: List<Entry>, accounts: List<Account>): Double =
    entries.filter { !it.isTransfer }.sumOf { it.base } + accounts.sumOf { Fx.toBase(it.opening, it.currency) }

fun accName(id: Long, accounts: List<Account>): String =
    accounts.firstOrNull { it.id == id }?.let { accIcon(it) + " " + it.name } ?: ""

@Composable
fun AccountsStrip(accounts: List<Account>, entries: List<Entry>, inc: Color, exp: Color, onOpen: () -> Unit) {
    val shown = accounts.filter { !it.archived }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💳 账户", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("管理 ›", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }
            for (a in shown.take(6)) {
                val b = balanceOf(a, entries, accounts)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(accIcon(a) + " " + a.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                    Text(
                        (if (b < 0) "-" else "") + moneyC(b, a.currency),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (b < 0) exp else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (shown.size > 6) Text("还有 ${shown.size - 6} 个账户…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AccountsScreen(
    accounts: List<Account>,
    entries: List<Entry>,
    inc: Color,
    exp: Color,
    onSave: (Account) -> Unit,
    onDelete: (Account) -> Unit,
    onOpenEntry: (Entry) -> Unit,
    onClose: () -> Unit,
) {
    var editing by remember { mutableStateOf<Account?>(null) }
    var adding by remember { mutableStateOf(false) }
    var detailId by remember { mutableLongStateOf(0L) }
    BackHandler { if (detailId != 0L) detailId = 0L else onClose() }
    val detail = accounts.firstOrNull { it.id == detailId }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.let { accIcon(it) + " " + it.name } ?: "账户", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { if (detailId != 0L) detailId = 0L else onClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (detail != null) {
                        TextButton(onClick = { editing = detail }) { Text("编辑") }
                    } else {
                        TextButton(onClick = { adding = true }) { Text("＋ 新增") }
                    }
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
            if (detail == null) {
                val nw = netWorth(entries, accounts)
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("净资产（所有账户，结算 ${Cur.code}）", fontSize = 13.sp)
                        Text((if (nw < 0) "-" else "") + money(nw), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (nw < 0) exp else MaterialTheme.colorScheme.onSurface)
                        val debt = accounts.filter { !it.archived }.sumOf { a ->
                            val b = balanceOf(a, entries, accounts)
                            if (b < 0) Fx.toBase(-b, a.currency) else 0.0
                        }
                        if (debt > 0) Text("其中欠款（信用卡等）${money(debt)}", fontSize = 13.sp, color = exp)
                    }
                }
                for (t in ACCOUNT_TYPES.indices) {
                    val list = accounts.filter { it.type == t && !it.archived }
                    if (list.isEmpty()) continue
                    Text(ACCOUNT_ICONS[t] + " " + ACCOUNT_TYPES[t], fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                    Card(shape = RoundedCornerShape(16.dp)) {
                        for ((i, a) in list.withIndex()) {
                            if (i > 0) HorizontalDivider()
                            AccountRow(a, balanceOf(a, entries, accounts), exp) { detailId = a.id }
                        }
                    }
                }
                val archived = accounts.filter { it.archived }
                if (archived.isNotEmpty()) {
                    Text("已停用", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Card(shape = RoundedCornerShape(16.dp)) {
                        for ((i, a) in archived.withIndex()) {
                            if (i > 0) HorizontalDivider()
                            AccountRow(a, balanceOf(a, entries, accounts), exp) { detailId = a.id }
                        }
                    }
                }
                Text(
                    "提示：用银行卡还信用卡、提款放进现金，记一笔时选「转账」，不会被算成支出。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val b = balanceOf(detail, entries, accounts)
                val now = YearMonth.now()
                val mine = entries.filter { accOf(it, accounts) == detail.id || it.toAccountId == detail.id }
                    .sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.id })
                val monthMine = mine.filter { YearMonth.from(it.date) == now && !it.isTransfer }
                val mIn = monthMine.filter { it.amount > 0 }.sumOf { Fx.convert(it.amount, it.currency, detail.currency) }
                val mOut = -monthMine.filter { it.amount < 0 }.sumOf { Fx.convert(it.amount, it.currency, detail.currency) }
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (detail.type == 2) "目前余额（负数 = 欠款）" else "余额", fontSize = 13.sp)
                        Text((if (b < 0) "-" else "") + moneyC(b, detail.currency), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (b < 0) exp else MaterialTheme.colorScheme.onSurface)
                        if (detail.type == 2 && detail.limit > 0) {
                            val used = maxOf(0.0, -b)
                            LinearProgressIndicator(
                                progress = { (used / detail.limit).coerceIn(0.0, 1.0).toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = exp,
                            )
                            Text("已用 ${moneyC(used, detail.currency)} · 可用 ${moneyC(maxOf(0.0, detail.limit - used), detail.currency)} · 额度 ${moneyC(detail.limit, detail.currency)}", fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("本月 存入 ${moneyC(mIn, detail.currency)} · 取出 ${moneyC(mOut, detail.currency)}", fontSize = 13.sp)
                    }
                }
                Text("最近记录", fontWeight = FontWeight.Bold)
                if (mine.isEmpty()) {
                    Text("还没有记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        for ((i, e) in mine.take(60).withIndex()) {
                            if (i > 0) HorizontalDivider()
                            val incoming = e.toAccountId == detail.id
                            val v = if (incoming) -e.amount else e.amount
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenEntry(e) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (e.isTransfer) {
                                            if (incoming) "⇄ 从 ${accName(accOf(e, accounts), accounts)} 转入" else "⇄ 转到 ${accName(e.toAccountId, accounts)}"
                                        } else {
                                            e.note.ifBlank { e.tag.ifBlank { if (e.amount >= 0) "存入" else "取出" } }
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 14.sp,
                                    )
                                    Text("${e.date.monthValue}月${e.date.dayOfMonth}日" + if (e.tag.isNotBlank() && !e.isTransfer) " · ${e.tag}" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    signedMoneyC(v, e.currency),
                                    fontWeight = FontWeight.Bold,
                                    color = if (e.isTransfer) MaterialTheme.colorScheme.onSurfaceVariant else if (v >= 0) inc else exp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        AccountDialog(
            initial = editing,
            canDelete = editing != null && accounts.size > 1,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = {
                onSave(it)
                adding = false
                editing = null
            },
            onDelete = {
                onDelete(it)
                adding = false
                editing = null
                detailId = 0L
            },
        )
    }
}

@Composable
fun AccountRow(a: Account, b: Double, exp: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text(accIcon(a), fontSize = 18.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.name, fontWeight = FontWeight.Medium)
            Text(
                ACCOUNT_TYPES.getOrElse(a.type) { "其他" } + " · " + a.currency +
                    if (a.type == 2 && a.limit > 0) " · 额度 ${moneyC(a.limit, a.currency)}" else "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text((if (b < 0) "-" else "") + moneyC(b, a.currency), fontWeight = FontWeight.Bold, color = if (b < 0) exp else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun AccountDialog(
    initial: Account?,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (Account) -> Unit,
    onDelete: (Account) -> Unit,
) {
    var type by remember { mutableIntStateOf(initial?.type ?: 1) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var cur by remember { mutableStateOf(initial?.currency ?: Cur.code) }
    var openText by remember {
        mutableStateOf(initial?.let { if (it.opening != 0.0) plainNumber(kotlin.math.abs(it.opening)) else "" } ?: "")
    }
    var owe by remember { mutableStateOf(initial?.let { it.opening < 0 } ?: false) }
    var limitText by remember { mutableStateOf(initial?.let { if (it.limit > 0) plainNumber(it.limit) else "" } ?: "") }
    var archived by remember { mutableStateOf(initial?.archived ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val valid = name.isNotBlank()
    val isCredit = type == 2

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增账户" else "编辑账户") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in ACCOUNT_TYPES.indices) {
                        FilterChip(selected = type == i, onClick = { type = i }, label = { Text(ACCOUNT_ICONS[i] + " " + ACCOUNT_TYPES[i], fontSize = 13.sp) })
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val presets = ACCOUNT_PRESETS[type].orEmpty()
                if (presets.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (n in presets) FilterChip(selected = name == n, onClick = { name = n }, label = { Text(n, fontSize = 12.sp) })
                    }
                }
                Text("货币", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in CURRENCIES) FilterChip(selected = cur == c.first, onClick = { cur = c.first }, label = { Text(c.third, fontSize = 13.sp) })
                }
                OutlinedTextField(
                    value = openText,
                    onValueChange = { v -> openText = v.filter { it.isDigit() || it == '.' } },
                    label = { Text(if (isCredit) "目前欠款（开始记账时）" else "目前余额（开始记账时）") },
                    prefix = { Text("$cur ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!isCredit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("这是欠款（负数）", modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Switch(checked = owe, onCheckedChange = { owe = it })
                    }
                }
                if (isCredit) {
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { v -> limitText = v.filter { it.isDigit() || it == '.' } },
                        label = { Text("信用额度（可选）") },
                        prefix = { Text("$cur ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "用信用卡消费记「取出」选这张卡；还卡时记「转账」：从银行卡转到这张信用卡。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (initial != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("停用这个账户")
                            Text("不再出现在选择里，记录保留", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = archived, onCheckedChange = { archived = it })
                    }
                }
                if (canDelete && initial != null) {
                    HorizontalDivider()
                    if (confirmDelete) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("删除账户？它的记录会归到第一个账户", modifier = Modifier.weight(1f), fontSize = 14.sp)
                            TextButton(onClick = { onDelete(initial) }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        }
                    } else {
                        TextButton(onClick = { confirmDelete = true }) { Text("删除账户", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val v = openText.toDoubleOrNull() ?: 0.0
                onSave(
                    Account(
                        id = initial?.id ?: System.currentTimeMillis(),
                        name = name.trim(),
                        type = type,
                        currency = cur,
                        opening = if (isCredit || owe) -v else v,
                        limit = if (isCredit) limitText.toDoubleOrNull() ?: 0.0 else 0.0,
                        archived = archived,
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
