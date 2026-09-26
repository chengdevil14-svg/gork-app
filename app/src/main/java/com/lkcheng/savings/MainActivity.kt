@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.lkcheng.savings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

private val GREEN = Color(0xFF16A34A)
private val RED = Color(0xFFDC2626)

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val locked = mutableStateOf(false)
    private var leftAt = 0L

    override fun onStop() {
        super.onStop()
        leftAt = android.os.SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val st = Store(applicationContext)
        if (st.getB(AppLock.ON, false) && leftAt > 0) {
            val sec = st.getI(AppLock.AFTER, 0)
            if (sec >= 0 && android.os.SystemClock.elapsedRealtime() - leftAt >= sec * 1000L) locked.value = true
        }
    }

    private fun applySecure(on: Boolean) {
        if (on) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crash = File(filesDir, "crash.txt")
        if (crash.exists()) {
            showCrash(crash)
            return
        }
        enableEdgeToEdge()
        val store = Store(applicationContext)
        Cur.load(store)
        locked.value = store.getB(AppLock.ON, false)
        applySecure(store.getB(AppLock.SECURE, false))
        val openAdd = intent?.getBooleanExtra("open_add", false) == true
        val openScan = intent?.getBooleanExtra("open_scan", false) == true
        val sharedUri: android.net.Uri? = if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? android.net.Uri
        } else {
            null
        }
        try {
            Reminders.scheduleAll(applicationContext)
        } catch (e: Exception) {
            // ignore
        }
        setContent {
            var theme by remember { mutableIntStateOf(store.theme) }
            val dark = theme == 1
            LaunchedEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t),
                )
            }
            MaterialTheme(colorScheme = schemeFor(theme)) {
                var quickPending by remember { mutableStateOf(openAdd) }
                val quickOk = store.getB(AppLock.QUICK, true)
                if (locked.value) {
                    val showQuick = quickPending && quickOk
                    LockScreen(store, autoPrompt = !showQuick) { locked.value = false }
                    if (showQuick) {
                        val green = store.greenForIncome
                        EntryDialog(
                            initial = null,
                            defaultDate = LocalDate.now(),
                            inc = if (green) GREEN else RED,
                            exp = if (green) RED else GREEN,
                            inTags = INCOME_TAGS,
                            outTags = EXPENSE_TAGS,
                            goals = emptyList(),
                            accounts = store.loadAccounts(),
                            prefill = null,
                            onDismiss = { quickPending = false },
                            onSave = { e ->
                                store.saveEntries(store.loadEntries() + e)
                                Widgets.updateAll(applicationContext)
                                quickPending = false
                                android.widget.Toast.makeText(applicationContext, "✅ 已记下 " + signedMoneyC(e.amount, e.currency), android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onDelete = {},
                        )
                    }
                } else SavingsApp(store, theme, openAdd && quickPending, sharedUri, openScan) { t ->
                    theme = t
                    store.theme = t
                    Widgets.updateAll(applicationContext)
                }
            }
        }
    }

    private fun showCrash(file: File) {
        val text = try { file.readText() } catch (e: Exception) { "无法读取错误信息" }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
            setBackgroundColor(android.graphics.Color.WHITE)
        }
        val title = android.widget.TextView(this).apply {
            this.text = "App 上次闪退了，请把这页截图发给 Claude"
            textSize = 18f
            setTextColor(android.graphics.Color.RED)
        }
        val retry = android.widget.Button(this).apply {
            this.text = "清除并重新打开"
            setOnClickListener {
                file.delete()
                recreate()
            }
        }
        val body = android.widget.TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(android.graphics.Color.BLACK)
            setTextIsSelectable(true)
        }
        val scroll = android.widget.ScrollView(this).apply { addView(body) }
        layout.addView(title)
        layout.addView(retry)
        layout.addView(scroll)
        setContentView(layout)
    }
}

val THEME_NAMES = listOf("冰蓝", "深夜", "海蓝", "绿叶", "樱花粉", "紫粉")
val THEME_SWATCH = listOf(
    Color(0xFF5AA9E6), Color(0xFF1F2937), Color(0xFF1D4ED8),
    Color(0xFF2E9D5B), Color(0xFFE75A8B), Color(0xFFA855F7),
)
val THEME_DESC = listOf(
    "冰蓝天空色，像 iPhone 天空蓝，清爽透亮",
    "深色背景，晚上看不刺眼",
    "经典海蓝，稳重清晰",
    "小绿叶，清新自然",
    "樱花粉，温柔可爱",
    "紫粉渐变感，梦幻优雅",
)

private fun lightTheme(p: Long, onP: Long, pc: Long, onPc: Long, bg: Long, s1: Long, s2: Long) = lightColorScheme(
    primary = Color(p),
    onPrimary = Color(onP),
    primaryContainer = Color(pc),
    onPrimaryContainer = Color(onPc),
    secondaryContainer = Color(pc),
    onSecondaryContainer = Color(onPc),
    background = Color(bg),
    surface = Color(bg),
    surfaceContainerHighest = Color(s1),
    surfaceContainerHigh = Color(s2),
    surfaceContainer = Color(s2),
    surfaceVariant = Color(s1),
)

fun schemeFor(theme: Int) = when (theme) {
    1 -> darkColorScheme(
        primary = Color(0xFF8AB4FF),
        onPrimary = Color(0xFF0B1B3A),
        primaryContainer = Color(0xFF1E2A44),
        onPrimaryContainer = Color(0xFFDCE6FF),
        background = Color(0xFF0E1117),
        surface = Color(0xFF0E1117),
        surfaceContainerHighest = Color(0xFF1A1F29),
        surfaceContainerHigh = Color(0xFF1F2530),
    )
    2 -> lightTheme(0xFF1D4ED8, 0xFFFFFFFF, 0xFFDBE6FF, 0xFF102A72, 0xFFFFFFFF, 0xFFF0F4FC, 0xFFF7F9FE)
    3 -> lightTheme(0xFF2E9D5B, 0xFFFFFFFF, 0xFFD8F3E3, 0xFF0F4D2A, 0xFFF7FBF8, 0xFFE9F5EE, 0xFFF1F9F4)
    4 -> lightTheme(0xFFE0538A, 0xFFFFFFFF, 0xFFFFE0EC, 0xFF7A1F45, 0xFFFFF8FB, 0xFFFCEAF1, 0xFFFFF1F6)
    5 -> lightTheme(0xFF9B4DDB, 0xFFFFFFFF, 0xFFF3E1FF, 0xFF4B1670, 0xFFFCF8FF, 0xFFF3E9FB, 0xFFF8F1FD)
    else -> lightTheme(0xFF3E95D6, 0xFFFFFFFF, 0xFFDDEFFC, 0xFF0B3D66, 0xFFF6FBFF, 0xFFE8F3FC, 0xFFF0F7FD)
}

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        // no browser
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsApp(store: Store, theme: Int, openAdd: Boolean, sharedUri: android.net.Uri?, openScan: Boolean = false, onThemeChange: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries = remember { mutableStateListOf<Entry>().apply { addAll(store.loadEntries()) } }
    val goals = remember { mutableStateListOf<Goal>().apply { addAll(store.loadGoals()) } }
    var editingGoal by remember { mutableStateOf<Goal?>(null) }
    val subs = remember { mutableStateListOf<Sub>().apply { addAll(store.loadSubs()) } }
    var editingSub by remember { mutableStateOf<Sub?>(null) }
    var newSub by remember { mutableStateOf(false) }
    var newGoal by remember { mutableStateOf(false) }
    val accounts = remember { mutableStateListOf<Account>().apply { addAll(store.loadAccounts()) } }
    val deposits = remember { mutableStateListOf<Deposit>().apply { addAll(store.loadDeposits()) } }
    var editingDep by remember { mutableStateOf<Deposit?>(null) }
    var newDep by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var prefill by remember { mutableStateOf<ScanResult?>(null) }
    var scanBusy by remember { mutableStateOf(false) }
    var greenIncome by remember { mutableStateOf(store.greenForIncome) }
    var syncUrl by remember { mutableStateOf(store.syncUrl) }
    var sheetUrl by remember { mutableStateOf(store.sheetUrl) }
    var syncing by remember { mutableStateOf(false) }
    var syncMsg by remember { mutableStateOf("") }
    var monthStr by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selStr by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val prefs = remember { AppPrefs(store) }
    var showAdd by remember { mutableStateOf(openAdd) }
    var showWelcome by rememberSaveable { mutableStateOf(prefs.popupOn.value && !openAdd && !openScan) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    val month = YearMonth.parse(monthStr)
    val selected = LocalDate.parse(selStr)
    val inc = if (greenIncome) GREEN else RED
    val exp = if (greenIncome) RED else GREEN

    fun persist() {
        store.saveEntries(entries.toList())
        Widgets.updateAll(context)
    }
    fun persistGoals() = store.saveGoals(goals.toList())
    fun persistSubs() = store.saveSubs(subs.toList())
    fun persistAccounts() = store.saveAccounts(accounts.toList())
    fun persistDeposits() = store.saveDeposits(deposits.toList())

    fun runCommute() {
        val (na, ne) = processCommute(accounts.toList())
        if (na != accounts.toList()) {
            accounts.clear()
            accounts.addAll(na)
            persistAccounts()
        }
        if (ne.isNotEmpty()) {
            entries.addAll(ne)
            persist()
        }
    }

    fun runGoalInterest() {
        val ng = processGoalInterest(goals.toList(), entries.toList())
        if (ng != goals.toList()) {
            goals.clear()
            goals.addAll(ng)
            persistGoals()
        }
    }

    fun runDeposits() {
        val (nd, ne) = processDeposits(deposits.toList(), accounts.toList())
        if (nd != deposits.toList()) {
            deposits.clear()
            deposits.addAll(nd)
            persistDeposits()
        }
        if (ne.isNotEmpty()) {
            entries.addAll(ne)
            persist()
        }
    }

    fun startScan(uri: android.net.Uri) {
        val name = Photos.import(context, uri) ?: ""
        scanBusy = true
        Scan.recognize(context, uri, name) { r ->
            scanBusy = false
            prefill = r ?: ScanResult(null, null, false, "", "", null, "", name, "")
            showAdd = true
        }
    }

    /** Roll subscription dates forward and auto-record charges that are due. */
    fun runSubs() {
        val (ns, ne) = processSubs(subs.toList())
        if (ns != subs.toList()) {
            subs.clear()
            subs.addAll(ns)
            persistSubs()
        }
        if (ne.isNotEmpty()) {
            entries.addAll(ne)
            persist()
        }
    }

    fun syncNow() {
        val url = syncUrl
        if (syncing || url.isBlank()) return
        syncing = true
        syncMsg = "同步中…"
        scope.launch {
            var err: String? = null
            try {
                for (id in store.pendingDeletes()) {
                    withContext(Dispatchers.IO) { Sync.delete(url, id) }
                    store.removePendingDelete(id)
                }
                for (e in entries.filter { !it.synced }.toList()) {
                    val gName = goals.firstOrNull { it.id == e.goalId }?.name ?: ""
                    val aName = accounts.firstOrNull { it.id == accOf(e, accounts) }?.name ?: ""
                    val tName = if (e.isTransfer) accounts.firstOrNull { it.id == e.toAccountId }?.name ?: "" else ""
                    val st = statAmt(e, accounts.toList())
                    withContext(Dispatchers.IO) { Sync.upsert(context, url, e, gName, aName, tName, st) }
                    val i = entries.indexOfFirst { it.id == e.id }
                    if (i >= 0 && entries[i] == e) {
                        entries[i] = e.copy(synced = true, photoSent = e.photo.isNotEmpty())
                        persist()
                    }
                }
                val nwSnap = netWorth(entries.toList(), accounts.toList())
                val snapshot = goals.toList().map { g -> g to calcGoal(g, entries.toList(), nwSnap) }
                val subsSnap = subs.toList()
                val accSnap = accounts.toList().filter { !it.archived }.map { a -> a to balanceOf(a, entries.toList(), accounts.toList()) }
                val depSnap = deposits.toList()
                withContext(Dispatchers.IO) {
                    Sync.goals(url, snapshot, prefs.emailOn.value, subsSnap, prefs.subEmail.value, accSnap, depSnap)
                }
            } catch (ex: Exception) {
                err = ex.message ?: ex.toString()
            }
            syncMsg = if (err == null) "已同步到 Google Sheet" else "同步失败：$err"
            syncing = false
            // edits made while syncing get picked up
            if (err == null && entries.any { !it.synced }) syncNow()
        }
    }

    LaunchedEffect(Unit) {
        runSubs()
        runDeposits()
        runCommute()
        runGoalInterest()
        Widgets.updateAll(context)
        if (sharedUri != null) startScan(sharedUri)
        syncNow()
    }

    fun goToMonth(m: YearMonth) {
        monthStr = m.toString()
        val now = LocalDate.now()
        selStr = (if (YearMonth.from(now) == m) now else m.atDay(1)).toString()
    }

    val total = netWorth(entries, accounts)
    val accList = accounts.toList()
    val monthEntries = entries.filter { YearMonth.from(it.date) == month && statAmt(it, accList) != 0.0 }
    val monthIn = monthEntries.sumOf { maxOf(0.0, statAmt(it, accList)) }
    val monthOut = -monthEntries.sumOf { minOf(0.0, statAmt(it, accList)) }
    val daily = monthEntries.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { statAmt(it, accList) } }
    val dayEntries = entries.filter { it.date == selected }.sortedByDescending { it.id }
    val dayNet = dayEntries.sumOf { statAmt(it, accList) }
    val unsynced = entries.count { !it.synced } + store.pendingDeletes().size

    val scanPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) startScan(uri)
    }
    LaunchedEffect(openScan) {
        if (openScan) scanPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    if (showSettings) {
        SettingsScreen(
            theme = theme,
            onTheme = onThemeChange,
            greenIncome = greenIncome,
            onGreen = { g ->
                greenIncome = g
                store.greenForIncome = g
            },
            syncUrl = syncUrl,
            sheetUrl = sheetUrl,
            onSyncUrl = { url ->
                if (url != syncUrl) {
                    syncUrl = url
                    store.syncUrl = url
                    for (i in entries.indices) entries[i] = entries[i].copy(synced = false, photoSent = false)
                    persist()
                    syncMsg = ""
                    syncNow()
                }
            },
            onSheetFound = { u ->
                sheetUrl = u
                store.sheetUrl = u
            },
            prefs = prefs,
            entries = entries.toList(),
            goals = goals.toList(),
            subs = subs.toList(),
            onEditSub = { x -> editingSub = x },
            onCurrency = { code ->
                store.currency = code
                Cur.set(code)
            },
            onEditGoal = { g -> editingGoal = g },
            onClearAll = {
                entries.forEach { Photos.delete(context, it.photo) }
                entries.clear()
                persist()
            },
            onClose = { showSettings = false },
        )
    } else {
    Column(Modifier.fillMaxSize()) {
    Box(
        Modifier
            .weight(1f)
            .consumeWindowInsets(WindowInsets.navigationBars)
    ) {
    when (tab) {
    3 -> {
        AccountsScreen(
            accounts = accounts.toList(),
            entries = entries.toList(),
            inc = inc,
            exp = exp,
            onSave = { a ->
                val i = accounts.indexOfFirst { it.id == a.id }
                if (i >= 0) accounts[i] = a else accounts.add(a)
                persistAccounts()
                runCommute()
            },
            onDelete = { a ->
                accounts.removeAll { it.id == a.id }
                for (i in entries.indices) {
                    val e = entries[i]
                    if (e.accountId == a.id || e.toAccountId == a.id) {
                        entries[i] = e.copy(
                            accountId = if (e.accountId == a.id) 0L else e.accountId,
                            toAccountId = if (e.toAccountId == a.id) 0L else e.toAccountId,
                            synced = false,
                        )
                    }
                }
                persistAccounts()
                persist()
            },
            onOpenEntry = { e -> editing = e },
            onClose = { tab = 0 },
        )
    }
    2 -> {
        val allE = entries.toList()
        val nw = netWorth(allE, accounts.toList())
        ReportScreen(
            allE, accounts.toList(), subs.toList(),
            goals.toList().map { g -> g to calcGoal(g, allE, nw) },
            deposits.toList(), inc, exp,
        ) { tab = 0 }
    }
    4 -> SearchScreen(entries.toList(), accounts.toList(), inc, exp, onOpen = { e -> editing = e }, onClose = { tab = 0 })
    1 -> Scaffold(
        topBar = {
            TopAppBar(title = { Text("🎯 计划", fontWeight = FontWeight.Bold) })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "目标与欠款",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { newGoal = true }) { Text("＋ 新增") }
                }
            }
            if (goals.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { newGoal = true },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            "还没有目标。可以加一个存款目标，或记下欠款（比如卡债），设置每月还多少，app 会自动算进度、本月还差多少、大概什么时候完成。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            val allEntries = entries.toList()
            items(goals.toList(), key = { "g" + it.id }) { g ->
                GoalCard(g, calcGoal(g, allEntries, total), inc, exp) { editingGoal = g }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "固定收支 / 订阅",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { newSub = true }) { Text("＋ 新增") }
                }
            }
            item { SubsSummary(subs.toList(), exp) { newSub = true } }
            val sortedSubs = subs.toList().sortedWith(
                compareBy<Sub>({ !it.active }, { nextBilling(it) ?: java.time.LocalDate.MAX })
            )
            items(sortedSubs, key = { "s" + it.id }) { s ->
                SubRow(s, exp) { editingSub = s }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "💰 利息 / 定期",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { newDep = true }) { Text("＋ 新增") }
                }
            }
            item { DepositsSummary(deposits.toList(), inc) { newDep = true } }
            items(deposits.toList(), key = { "d" + it.id }) { d ->
                DepositRow(d, allEntries, inc) { editingDep = d }
            }
        }
    }
    else -> {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("成长账本", fontWeight = FontWeight.Bold) },
                actions = {
                    if (syncUrl.isNotBlank()) {
                        if (syncing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(onClick = { syncNow() }) {
                                Text(
                                    if (unsynced > 0) "☁ 待同步 $unsynced" else "☁ 已同步",
                                    color = if (syncMsg.startsWith("同步失败")) exp else MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!scanBusy) scanPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = {
                        if (scanBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("🧾")
                    },
                    text = { Text(if (scanBusy) "识别中…" else "识别截图") },
                )
                ExtendedFloatingActionButton(
                    onClick = {
                        prefill = null
                        showAdd = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("记一笔") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (syncMsg.startsWith("同步失败")) {
                item {
                    Text(
                        syncMsg + "（点右上角重试）",
                        color = exp,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item {
                SummaryCard(total, 0.0, month, monthIn, monthOut, inc, exp)
            }
            if (prefs.homeAcc.value) item {
                AccountsStrip(accounts.toList(), entries.toList(), inc, exp) { tab = 3 }
            }

            item {
                MonthHeader(
                    month = month,
                    onPrev = { goToMonth(month.minusMonths(1)) },
                    onNext = { goToMonth(month.plusMonths(1)) },
                    onToday = { goToMonth(YearMonth.now()) },
                    onPick = {
                        android.app.DatePickerDialog(context, { _, y, mo, d ->
                            val picked = LocalDate.of(y, mo + 1, d)
                            monthStr = YearMonth.from(picked).toString()
                            selStr = picked.toString()
                        }, selected.year, selected.monthValue - 1, selected.dayOfMonth).show()
                    },
                )
            }
            item {
                CalendarGrid(month, selected, daily, inc, exp) { selStr = it.toString() }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${selected.monthValue}月${selected.dayOfMonth}日 ${weekdayCn(selected)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (dayEntries.isNotEmpty()) {
                        Text(
                            signedMoney(dayNet),
                            color = if (dayNet >= 0) inc else exp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            if (dayEntries.isEmpty()) {
                item {
                    Text(
                        "这天还没有记录，点右下角「记一笔」添加",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(dayEntries, key = { it.id }) { e ->
                EntryRow(
                    e, inc, exp,
                    showSync = syncUrl.isNotBlank(),
                    goalName = goals.firstOrNull { it.id == e.goalId }?.name ?: "",
                    accountText = if (e.isTransfer) {
                        accName(accOf(e, accounts), accounts) + " → " + accName(e.toAccountId, accounts)
                    } else if (accounts.size > 1) {
                        accName(accOf(e, accounts), accounts)
                    } else {
                        ""
                    },
                ) { editing = e }
            }
            if (prefs.homeDaily.value) item {
                Text(
                    "📅 每日成本",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            if (prefs.homeDaily.value) item {
                val allE = entries.toList()
                val nowYm = YearMonth.now()
                val outNow = -allE.filter { YearMonth.from(it.date) == nowYm }.sumOf { minOf(0.0, statAmt(it, accList)) }
                DailyCostCard(
                    subs.toList(),
                    accList,
                    goals.toList().map { g -> g to calcGoal(g, allE, total) },
                    deposits.toList(),
                    outNow,
                    inc,
                    exp,
                )
            }
            if (prefs.homeTodo.value) item {
                val todos = ArrayList<Todo>()
                val live = accList.filter { !it.archived }
                if (live.size <= 1) todos.add(Todo("新增你的银行卡 / 信用卡 / 交通卡账户") { tab = 3 })
                for (a in live.filter { acct -> acct.opening == 0.0 && entries.none { e -> accOf(e, accList) == acct.id || e.toAccountId == acct.id } }) {
                    todos.add(Todo("「${a.name}」还没填目前余额") { tab = 3 })
                }
                for (a in live.filter { it.type == 2 && it.limit <= 0 }) todos.add(Todo("信用卡「${a.name}」还没填额度") { tab = 3 })
                for (a in live.filter { it.type == TYPE_CARD && !it.commuteOn }) todos.add(Todo("交通卡「${a.name}」可以设置通勤自动扣款") { tab = 3 })
                if (subs.none { it.isIncome && it.active }) todos.add(Todo("加入固定收入（工资）") { newSub = true })
                if (subs.none { !it.isIncome && it.active }) todos.add(Todo("加入固定支出（房租、电话费、订阅）") { newSub = true })
                for (g in goals.filter { it.debt }) {
                    if (g.rate <= 0) todos.add(Todo("欠款「${g.name}」还没填年利率") { editingGoal = g })
                    if (g.monthly <= 0) todos.add(Todo("欠款「${g.name}」还没填每月还多少") { editingGoal = g })
                    if (g.dueDay <= 0) todos.add(Todo("欠款「${g.name}」还没填每月还款日") { editingGoal = g })
                }
                if (!prefs.remOn.value) todos.add(Todo("开启提醒通知") { showSettings = true })
                if (syncUrl.isBlank()) todos.add(Todo("连接 Google Sheet 同步（备份）") { showSettings = true })
                if (entries.none { it.date == LocalDate.now() }) todos.add(Todo("今天还没记账") {
                    prefill = null
                    showAdd = true
                })
                TodoCard(todos)
            }
        }
    }
    }
    }
    }
    NavigationBar {
        val navItems = listOf("🏠" to "首页", "🎯" to "计划", "📊" to "报表", "💳" to "账户", "🔍" to "查询")
        for ((i, it) in navItems.withIndex()) {
            NavigationBarItem(
                selected = tab == i,
                onClick = { tab = i },
                icon = { Text(it.first, fontSize = 20.sp) },
                label = { Text(it.second, fontSize = 12.sp) },
            )
        }
    }
    }
    }

    if (newSub || editingSub != null) {
        SubDialog(
            initial = editingSub,
            exp = exp,
            remindH = prefs.subH.value,
            remindM = prefs.subM.value,
            accounts = accounts.toList(),
            onDismiss = {
                newSub = false
                editingSub = null
            },
            onSave = { x ->
                val i = subs.indexOfFirst { it.id == x.id }
                if (i >= 0) subs[i] = x else subs.add(x)
                persistSubs()
                runSubs()
                newSub = false
                editingSub = null
                Reminders.scheduleAll(context)
                syncNow()
            },
            onDelete = { x ->
                subs.removeAll { it.id == x.id }
                persistSubs()
                newSub = false
                editingSub = null
                Reminders.scheduleAll(context)
                syncNow()
            },
        )
    }

    if (newDep || editingDep != null) {
        DepositDialog(
            initial = editingDep,
            accounts = accounts.toList(),
            onDismiss = {
                newDep = false
                editingDep = null
            },
            entries = entries.toList(),
            onSave = { d ->
                val i = deposits.indexOfFirst { it.id == d.id }
                if (i >= 0) deposits[i] = d else deposits.add(d)
                persistDeposits()
                runDeposits()
                newDep = false
                editingDep = null
                Reminders.scheduleAll(context)
                syncNow()
            },
            onDelete = { d ->
                deposits.removeAll { it.id == d.id }
                persistDeposits()
                newDep = false
                editingDep = null
                Reminders.scheduleAll(context)
            },
            onRecord = { e ->
                entries.add(e)
                persist()
                syncNow()
            },
        )
    }

    if (showWelcome) {
        WelcomeDialog(
            entries = entries.toList(),
            goals = goals.toList(),
            subs = subs.toList(),
            onAdd = {
                showWelcome = false
                showAdd = true
            },
            onDismiss = { showWelcome = false },
        )
    }

    if (showAdd) {
        EntryDialog(
            initial = null,
            defaultDate = selected,
            inc = inc,
            exp = exp,
            inTags = tagCandidates(entries, true),
            outTags = tagCandidates(entries, false),
            goals = goals.toList(),
            accounts = accounts.toList(),
            prefill = prefill,
            onDismiss = {
                showAdd = false
                prefill = null
            },
            onSave = { e ->
                entries.add(e)
                persist()
                showAdd = false
                prefill = null
                syncNow()
            },
            onDelete = {},
        )
    }

    val current = editing
    if (current != null) {
        EntryDialog(
            initial = current,
            defaultDate = current.date,
            inc = inc,
            exp = exp,
            inTags = tagCandidates(entries, true),
            outTags = tagCandidates(entries, false),
            goals = goals.toList(),
            accounts = accounts.toList(),
            prefill = null,
            onDismiss = { editing = null },
            onSave = { e ->
                val i = entries.indexOfFirst { it.id == e.id }
                if (i >= 0) {
                    val old = entries[i]
                    if (old.photo.isNotEmpty() && old.photo != e.photo) Photos.delete(context, old.photo)
                    entries[i] = e
                }
                persist()
                editing = null
                syncNow()
            },
            onDelete = { e ->
                entries.removeAll { it.id == e.id }
                Photos.delete(context, e.photo)
                persist()
                if (syncUrl.isNotBlank()) store.addPendingDelete(e.id)
                editing = null
                syncNow()
            },
        )
    }

    if (newGoal || editingGoal != null) {
        val g0 = editingGoal
        GoalDialog(
            initial = g0,
            inc = inc,
            exp = exp,
            onDismiss = {
                newGoal = false
                editingGoal = null
            },
            onSave = { g ->
                val i = goals.indexOfFirst { it.id == g.id }
                if (i >= 0) goals[i] = g else goals.add(g)
                persistGoals()
                newGoal = false
                editingGoal = null
                Reminders.scheduleAll(context)
                syncNow()
            },
            onDelete = { g ->
                goals.removeAll { it.id == g.id }
                for (i in entries.indices) {
                    if (entries[i].goalId == g.id) entries[i] = entries[i].copy(goalId = 0L, synced = false)
                }
                persist()
                persistGoals()
                newGoal = false
                editingGoal = null
                Reminders.scheduleAll(context)
                syncNow()
            },
        )
    }

}

@Composable
fun SummaryCard(
    total: Double,
    goal: Double,
    month: YearMonth,
    monthIn: Double,
    monthOut: Double,
    inc: Color,
    exp: Color,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                "总存款 · 结算货币：" + (CURRENCIES.firstOrNull { it.first == Cur.code }?.third ?: Cur.code),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                (if (total < 0) "-" else "") + money(total),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = if (total < 0) exp else MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (goal > 0) {
                val p = (total / goal).coerceIn(0.0, 1.0)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { p.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = inc,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(6.dp))
                val remain = goal - total
                Text(
                    "目标 ${money(goal)} · 已完成 ${(p * 100).toInt()}%" +
                        if (remain > 0) " · 还差 ${money(remain)}" else " · 已达成 🎉",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("${month.monthValue}月存入", "+" + money(monthIn), inc, Modifier.weight(1f))
                Stat("${month.monthValue}月取出", "-" + money(monthOut), exp, Modifier.weight(1f))
                val net = monthIn - monthOut
                Stat("本月净额", signedMoney(net), if (net >= 0) inc else exp, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(
            value,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit, onPick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
        }
        Text(
            "${month.year}年${month.monthValue}月 ▾",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onPick),
        )
        if (month != YearMonth.now()) {
            TextButton(onClick = onToday) { Text("回到本月") }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
        }
    }
}

@Composable
fun CalendarGrid(
    month: YearMonth,
    selected: LocalDate,
    daily: Map<LocalDate, Double>,
    inc: Color,
    exp: Color,
    onSelect: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val offset = month.atDay(1).dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val rows = (offset + days + 6) / 7
    val shape = RoundedCornerShape(10.dp)

    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                for (h in weekHeaders()) {
                    Text(
                        h,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val d = r * 7 + c - offset + 1
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(0.82f)
                                .padding(2.dp)
                        ) {
                            if (d in 1..days) {
                                val date = month.atDay(d)
                                val net = daily[date]
                                val isSel = date == selected
                                val isToday = date == today
                                val bg = when {
                                    isSel -> MaterialTheme.colorScheme.primary
                                    net != null && net > 0 -> inc.copy(alpha = 0.14f)
                                    net != null && net < 0 -> exp.copy(alpha = 0.14f)
                                    else -> Color.Transparent
                                }
                                var m = Modifier
                                    .fillMaxSize()
                                    .clip(shape)
                                    .background(bg)
                                if (isToday && !isSel) {
                                    m = m.border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
                                }
                                Column(
                                    modifier = m.clickable { onSelect(date) },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        "$d",
                                        fontSize = 15.sp,
                                        fontWeight = if (isToday || isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (net != null && net != 0.0) {
                                        Text(
                                            compact(net),
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) Color.White else if (net > 0) inc else exp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoThumb(name: String, sizeDp: Int, sample: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bmp = remember(name, sample) { Photos.load(context, name, sample)?.asImageBitmap() }
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = "照片",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
    }
}

@Composable
fun EntryRow(e: Entry, inc: Color, exp: Color, showSync: Boolean, goalName: String, accountText: String, onClick: () -> Unit) {
    val isIn = e.amount >= 0
    val color = if (e.isTransfer) MaterialTheme.colorScheme.primary else if (isIn) inc else exp
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (e.photo.isNotEmpty()) {
                PhotoThumb(e.photo, 44, 8)
            } else {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (e.isTransfer) "⇄" else if (isIn) "＋" else "－", color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    e.note.ifBlank { if (e.isTransfer) "转账" else e.tag.ifBlank { if (isIn) "存入" else "取出" } },
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val parts = mutableListOf(if (e.isTransfer) "转账" else if (isIn) "存入" else "取出")
                if (e.tag.isNotBlank() && e.note.isNotBlank() && !e.isTransfer) parts.add(e.tag)
                if (accountText.isNotBlank()) parts.add(accountText)
                if (goalName.isNotBlank()) parts.add("🎯 $goalName")
                if (e.photo.isNotEmpty()) parts.add("📷")
                if (showSync) parts.add(if (e.synced) "☁ 已同步" else "☁ 待同步")
                Text(
                    parts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (e.isTransfer) moneyC(kotlin.math.abs(e.amount), e.currency) else signedMoneyC(e.amount, e.currency),
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
                if (e.currency != Cur.code) {
                    Text(
                        "≈ " + signedMoney(e.base),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun TypeButton(text: String, active: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    if (active) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        ) { Text(text, fontWeight = FontWeight.Bold) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(text, color = color)
        }
    }
}

@Composable
fun PhotoViewer(name: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val bmp = remember(name) { Photos.load(context, name, 1)?.asImageBitmap() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = "照片",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                "点任意处关闭",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryDialog(
    initial: Entry?,
    defaultDate: LocalDate,
    inc: Color,
    exp: Color,
    inTags: List<String>,
    outTags: List<String>,
    goals: List<Goal>,
    accounts: List<Account>,
    prefill: ScanResult?,
    onDismiss: () -> Unit,
    onSave: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
) {
    val context = LocalContext.current
    val liveAccounts = accounts.filter { !it.archived || it.id == initial?.accountId || it.id == initial?.toAccountId }
    fun hintAccount(hint: String): Long? = if (hint.isBlank()) null else accounts.firstOrNull { a ->
        !a.archived && (a.name.contains(hint, true) || hint.contains(a.name, true) ||
            (hint.startsWith("Touch") && (a.name.contains("TNG", true) || a.name.contains("Touch", true))))
    }?.id
    var tag by remember { mutableStateOf(initial?.tag ?: prefill?.tag ?: "") }
    var cur by remember { mutableStateOf(initial?.currency ?: prefill?.currency ?: Cur.code) }
    var goalId by remember { mutableLongStateOf(initial?.goalId ?: 0L) }
    var isIn by remember { mutableStateOf(initial?.let { it.amount >= 0 } ?: prefill?.isIn ?: true) }
    var transfer by remember { mutableStateOf(initial?.isTransfer ?: false) }
    var accId by remember {
        mutableLongStateOf(
            initial?.let { accOf(it, accounts) } ?: prefill?.let { hintAccount(it.accountHint) } ?: accounts.firstOrNull { !it.archived }?.id ?: 0L
        )
    }
    var toAccId by remember { mutableLongStateOf(initial?.toAccountId ?: 0L) }
    var date by remember { mutableStateOf(initial?.date ?: prefill?.date ?: defaultDate) }
    var amountText by remember {
        mutableStateOf(initial?.let { plainNumber(kotlin.math.abs(it.amount)) } ?: prefill?.amount?.let { plainNumber(it) } ?: "")
    }
    var note by remember { mutableStateOf(initial?.note ?: prefill?.note ?: "") }
    var photo by remember { mutableStateOf(initial?.photo ?: prefill?.photo ?: "") }
    var scanning by remember { mutableStateOf(false) }
    var scanMsg by remember { mutableStateOf(if (prefill != null) "🧾 已从截图识别，请核对后保存" else "") }
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val name = Photos.import(context, uri)
            if (name != null) photo = name
            scanning = true
            scanMsg = "识别中…"
            Scan.recognize(context, uri, name ?: "") { r ->
                scanning = false
                if (r == null || r.amount == null) {
                    scanMsg = "没认出金额，请手动填写"
                } else {
                    amountText = plainNumber(r.amount)
                    r.currency?.let { cur = it }
                    isIn = r.isIn
                    transfer = false
                    tag = r.tag
                    if (r.note.isNotBlank()) note = r.note
                    r.date?.let { date = it }
                    hintAccount(r.accountHint)?.let { accId = it }
                    scanMsg = "🧾 已识别，请核对后保存"
                }
            }
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewPhoto by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf(false) }
    val amount = amountText.toDoubleOrNull()
    val valid = amount != null && amount > 0 && (!transfer || (toAccId != 0L && toAccId != accId))

    val camFile = remember { File(context.cacheDir, "camera.jpg") }
    val camUri = remember { FileProvider.getUriForFile(context, context.packageName + ".files", camFile) }
    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            val name = Photos.import(context, camUri)
            if (name != null) photo = name else photoError = true
        }
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val name = Photos.import(context, uri)
            if (name != null) photo = name else photoError = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "记一笔" else "编辑记录") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            android.app.DatePickerDialog(context, { _, y, mo, d ->
                                date = LocalDate.of(y, mo + 1, d)
                            }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                        },
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) { Text("📅 ${date.year}年${date.monthValue}月${date.dayOfMonth}日 ${weekdayCn(date)}") }
                    Spacer(Modifier.weight(1f))
                    if (initial == null) {
                        TextButton(
                            enabled = !scanning,
                            onClick = {
                                scanLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        ) { Text("🧾 识别截图") }
                    }
                }
                if (scanMsg.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (scanning) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(scanMsg, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TypeButton("＋ 存入", isIn && !transfer, inc, Modifier.weight(1f)) {
                        isIn = true
                        transfer = false
                    }
                    TypeButton("－ 取出", !isIn && !transfer, exp, Modifier.weight(1f)) {
                        isIn = false
                        transfer = false
                    }
                    TypeButton("⇄ 转账", transfer, MaterialTheme.colorScheme.primary, Modifier.weight(1f)) {
                        isIn = false
                        transfer = true
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { s -> amountText = s.filter { it.isDigit() || it == '.' } },
                    label = { Text("金额（" + (CURRENCIES.firstOrNull { it.first == cur }?.third ?: cur) + "）") },
                    prefix = { Text(symbolOf(cur) + " ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in CURRENCIES) {
                        FilterChip(
                            selected = cur == c.first,
                            onClick = { cur = c.first },
                            label = { Text(c.third, fontSize = 13.sp) },
                        )
                    }
                }
                val a0 = amountText.toDoubleOrNull()
                if (cur != Cur.code && a0 != null) {
                    Text(
                        "≈ " + money(Fx.toBase(a0, cur)) + "（按结算货币）",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (q in listOf(10, 50, 100, 500)) {
                        OutlinedButton(
                            onClick = { amountText = plainNumber((amountText.toDoubleOrNull() ?: 0.0) + q) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp),
                        ) { Text("+$q", fontSize = 12.sp) }
                    }
                }
                Text(
                    if (transfer) "从哪个账户转出" else if (isIn) "存进哪个账户" else "用哪个账户付",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (a in liveAccounts) {
                        FilterChip(selected = accId == a.id, onClick = { accId = a.id }, label = { Text(accIcon(a) + " " + a.name, fontSize = 13.sp) })
                    }
                }
                if (transfer) {
                    Text("转到哪个账户", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (a in liveAccounts.filter { it.id != accId }) {
                            FilterChip(selected = toAccId == a.id, onClick = { toAccId = a.id }, label = { Text(accIcon(a) + " " + a.name, fontSize = 13.sp) })
                        }
                    }
                    if (liveAccounts.size < 2) {
                        Text("要先在「账户」里加第二个账户才能转账", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        if (typeOf(toAccId, accounts) == TYPE_CARD) "充值到交通卡 / 储值卡：算作支出（钱拿不回来）。之后刷卡只扣卡余额。"
                        else "转账不算收入也不算支出，只是钱从一个账户移到另一个。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!transfer) {
                    TagPicker(tag, if (isIn) inTags else outTags) { tag = it }
                }
                val goalChoices = goals.filter { g ->
                    g.id == goalId || (!(g.useTotal && !g.debt) && g.debt != isIn)
                }
                if (goalChoices.isNotEmpty()) {
                    Text(
                        if (isIn) "算进存款目标（可选）" else "用来还欠款（可选）",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = goalId == 0L,
                            onClick = { goalId = 0L },
                            label = { Text("不关联", fontSize = 13.sp) },
                        )
                        for (g in goalChoices) {
                            FilterChip(
                                selected = goalId == g.id,
                                onClick = {
                                    goalId = g.id
                                    if (tag.isBlank()) tag = if (g.debt) "欠债还款" else "存款"
                                },
                                label = { Text("🎯 " + g.name, fontSize = 13.sp) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (photo.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PhotoThumb(photo, 72, 4, Modifier.clickable { viewPhoto = true })
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            TextButton(onClick = { viewPhoto = true }) { Text("查看大图") }
                            TextButton(onClick = { photo = "" }) {
                                Text("移除照片", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                photoError = false
                                try {
                                    camLauncher.launch(camUri)
                                } catch (e: Exception) {
                                    photoError = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("📷 拍照") }
                        OutlinedButton(
                            onClick = {
                                photoError = false
                                pickLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("🖼 相册") }
                    }
                }
                if (photoError) {
                    Text("照片读取失败，请再试一次", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                if (initial != null) {
                    HorizontalDivider()
                    if (confirmDelete) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("确定删除？", modifier = Modifier.weight(1f))
                            TextButton(onClick = { confirmDelete = false }) { Text("不删") }
                            TextButton(onClick = { onDelete(initial) }) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("删除这条记录", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val a = amountText.toDoubleOrNull()
                    if (a != null && a > 0) {
                        val samePhoto = initial != null && initial.photo == photo
                        onSave(
                            Entry(
                                id = initial?.id ?: System.currentTimeMillis(),
                                date = date,
                                amount = if (isIn && !transfer) a else -a,
                                note = note.trim(),
                                photo = photo,
                                synced = false,
                                photoSent = samePhoto && initial?.photoSent == true,
                                tag = if (transfer) (if (typeOf(toAccId, accounts) == TYPE_CARD) "交通卡充值" else "转账") else tag.trim(),
                                goalId = goalId,
                                currency = cur,
                                accountId = accId,
                                toAccountId = if (transfer) toAccId else 0L,
                            )
                        )
                    }
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )

    if (viewPhoto && photo.isNotEmpty()) {
        PhotoViewer(photo) { viewPhoto = false }
    }
}

@Composable
fun SettingsDialog(
    goal: Double,
    greenIncome: Boolean,
    syncUrl: String,
    sheetUrl: String,
    theme: Int,
    entries: List<Entry>,
    onDismiss: () -> Unit,
    onSheetFound: (String) -> Unit,
    onSave: (Double, Boolean, String, Int) -> Unit,
    onClearAll: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var goalText by remember { mutableStateOf(if (goal > 0) plainNumber(goal) else "") }
    var green by remember { mutableStateOf(greenIncome) }
    var themeSel by remember { mutableIntStateOf(theme) }
    var url by remember { mutableStateOf(syncUrl) }
    var sheet by remember { mutableStateOf(sheetUrl) }
    var testMsg by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("主题", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in THEME_NAMES.indices) {
                        val sel = themeSel == i
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    if (sel) 2.dp else 1.dp,
                                    if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { themeSel = i }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(THEME_SWATCH[i])
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                THEME_NAMES[i],
                                fontSize = 13.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("颜色习惯")
                        Text(
                            if (green) "存入=绿色，取出=红色" else "存入=红色，取出=绿色",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = green, onCheckedChange = { green = it })
                }
                HorizontalDivider()
                Text("Google Sheet 同步", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.trim(); testMsg = "" },
                    label = { Text("脚本网址（https://script.google.com/…）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = url.isNotBlank() && !testing,
                        onClick = {
                            testing = true
                            testMsg = "连接中…"
                            val u = url
                            scope.launch {
                                testMsg = try {
                                    val res = withContext(Dispatchers.IO) { Sync.ping(u) }
                                    val s = res.optString("sheet", "")
                                    if (s.isNotEmpty()) {
                                        sheet = s
                                        onSheetFound(s)
                                    }
                                    "✅ 连接成功"
                                } catch (e: Exception) {
                                    "❌ " + (e.message ?: "连接失败")
                                }
                                testing = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("测试连接") }
                    OutlinedButton(
                        enabled = sheet.isNotEmpty(),
                        onClick = { openUrl(context, sheet) },
                        modifier = Modifier.weight(1f),
                    ) { Text("打开表格") }
                }
                if (testMsg.isNotEmpty()) Text(testMsg, fontSize = 13.sp)
                Text(
                    "保存后会自动把全部记录（含照片）上传到表格，之后每记一笔都会自动同步。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                OutlinedButton(
                    onClick = {
                        val csv = buildString {
                            append("日期,类型,金额,分类,备注\n")
                            for (e in entries.sortedWith(compareBy({ it.date }, { it.id }))) {
                                append(e.date.toString()).append(',')
                                append(if (e.amount >= 0) "存入" else "取出").append(',')
                                append(plainNumber(kotlin.math.abs(e.amount))).append(',')
                                append(e.tag.replace(",", "，")).append(',')
                                append(e.note.replace(",", "，").replace("\n", " ")).append('\n')
                            }
                        }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "成长账本导出")
                            putExtra(Intent.EXTRA_TEXT, csv)
                        }
                        context.startActivity(Intent.createChooser(send, "导出记录"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("导出全部记录（${entries.size} 条）") }
                if (confirmClear) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("清空 app 里的全部记录？无法恢复（表格不受影响）", modifier = Modifier.weight(1f))
                        TextButton(onClick = onClearAll) {
                            Text("清空", color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    TextButton(onClick = { confirmClear = true }) {
                        Text("清空全部记录", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(goalText.toDoubleOrNull() ?: 0.0, green, url.trim(), themeSel) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
