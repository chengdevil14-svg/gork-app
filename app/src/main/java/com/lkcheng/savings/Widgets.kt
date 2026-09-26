package com.lkcheng.savings

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import java.time.LocalDate
import java.time.YearMonth

class SummaryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Widgets.updateAll(context)
    }
}

class RingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Widgets.updateAll(context)
    }
}

val RING_MODES = listOf(
    "今日预算（今天支出 ÷ 每天可花）",
    "本月收支（支出占收入）",
    "本月支出分类",
    "账户余额分布",
    "利息 / 股息收入（本月）",
    "目标 / 欠款进度",
)

private val RING_COLORS = intArrayOf(
    0xFF3E95D6.toInt(), 0xFFE0538A.toInt(), 0xFF2E9D5B.toInt(), 0xFFF59E0B.toInt(),
    0xFF9B4DDB.toInt(), 0xFF14B8A6.toInt(), 0xFFEF4444.toInt(), 0xFF64748B.toInt(),
)

data class Ring(val title: String, val center: String, val sub: String, val parts: List<Pair<String, Double>>, val total: Double)

fun ringBitmap(r: Ring, accent: Int): android.graphics.Bitmap {
    val size = 320
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val stroke = 38f
    val rect = android.graphics.RectF(stroke / 2 + 4, stroke / 2 + 4, size - stroke / 2 - 4, size - stroke / 2 - 4)
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = android.graphics.Paint.Cap.BUTT
    }
    p.color = 0xFFE5E7EB.toInt()
    c.drawArc(rect, 0f, 360f, false, p)
    val sum = r.parts.sumOf { it.second }
    val denom = maxOf(r.total, sum)
    if (denom > 0) {
        var start = -90f
        for ((i, part) in r.parts.withIndex()) {
            val sweep = (part.second / denom * 360).toFloat()
            if (sweep <= 0f) continue
            p.color = if (r.parts.size == 1) accent else RING_COLORS[i % RING_COLORS.size]
            c.drawArc(rect, start, maxOf(1f, sweep - if (r.parts.size > 1) 1.5f else 0f), false, p)
            start += sweep
        }
    }
    val t = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = android.graphics.Paint.Align.CENTER
        color = 0xFF111827.toInt()
        isFakeBoldText = true
        textSize = if (r.center.length > 7) 40f else 54f
    }
    c.drawText(r.center, size / 2f, size / 2f + 8, t)
    t.isFakeBoldText = false
    t.textSize = 26f
    t.color = 0xFF6B7280.toInt()
    c.drawText(r.sub, size / 2f, size / 2f + 46, t)
    return bmp
}

class QuickWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Widgets.updateAll(context)
    }
}

fun computeRing(st: Store, mode: Int, entries: List<Entry>, accounts: List<Account>, total: Double, todayOut: Double): Ring {
    val ym = YearMonth.now()
    val monthE = entries.filter { YearMonth.from(it.date) == ym }
    return when (mode) {
        1 -> {
            val inc = monthE.sumOf { maxOf(0.0, statAmt(it, accounts)) }
            val out = -monthE.sumOf { minOf(0.0, statAmt(it, accounts)) }
            val pct = if (inc > 0) (out / inc * 100).toInt() else 0
            Ring("本月支出占收入", if (inc > 0) "$pct%" else "-", "支出 ${money(out)}", listOf("支出" to out), inc)
        }
        2 -> {
            val byCat = monthE.filter { statAmt(it, accounts) < 0 }.groupBy { it.tag.ifBlank { "未分类" } }
                .mapValues { (_, v) -> -v.sumOf { statAmt(it, accounts) } }.entries.sortedByDescending { it.value }
            val sum = byCat.sumOf { it.value }
            val parts = byCat.take(7).map { it.key to it.value } + if (byCat.size > 7) listOf("其他" to byCat.drop(7).sumOf { it.value }) else emptyList()
            Ring("本月支出分类", money(sum).substringAfter(' '), "本月支出", parts, sum)
        }
        3 -> {
            val bal = accounts.filter { !it.archived }.map { a -> a.name to Fx.toBase(balanceOf(a, entries, accounts), a.currency) }
                .filter { it.second > 0 }.sortedByDescending { it.second }
            val sum = bal.sumOf { it.second }
            Ring("账户余额分布", money(sum).substringAfter(' '), "总余额", bal, sum)
        }
        4 -> {
            val inc = monthE.filter { statAmt(it, accounts) > 0 && (it.tag == "利息" || it.tag == "投资收益" || it.tag.contains("股息")) }
                .groupBy { it.note.ifBlank { it.tag } }.mapValues { (_, v) -> v.sumOf { statAmt(it, accounts) } }
                .entries.sortedByDescending { it.value }.map { it.key to it.value }
            val sum = inc.sumOf { it.second }
            Ring("本月利息 / 股息", money(sum).substringAfter(' '), "被动收入", inc, sum)
        }
        5 -> {
            val g = st.loadGoals().firstOrNull()
            if (g == null) {
                Ring("目标 / 欠款", "-", "还没有目标", emptyList(), 0.0)
            } else {
                val s = calcGoal(g, entries, total)
                Ring(g.name, "${(s.pct * 100).toInt()}%", if (g.debt) "还欠 ${money(s.remaining)}" else "还差 ${money(s.remaining)}", listOf(g.name to s.pct), 1.0)
            }
        }
        else -> {
            val goals = st.loadGoals().map { g -> g to calcGoal(g, entries, total) }
            val (costs, incomes) = dailyLines(st.loadSubs(), accounts, goals, st.loadDeposits())
            val canSpend = incomes.sumOf { it.perDay } - costs.sumOf { it.perDay }
            if (canSpend <= 0) {
                Ring("今日预算", money(todayOut).substringAfter(' '), "今天支出", listOf("支出" to todayOut), todayOut)
            } else {
                val pct = (todayOut / canSpend * 100).toInt()
                Ring("今日预算 ${money(canSpend)}", "$pct%", if (todayOut <= canSpend) "还可花 ${money(canSpend - todayOut).substringAfter(' ')}" else "超支 ${money(todayOut - canSpend).substringAfter(' ')}", listOf("已花" to minOf(todayOut, canSpend)), canSpend)
            }
        }
    }
}

object Widgets {
    private fun open(ctx: Context, code: Int, extra: String): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (extra.isNotEmpty()) i.putExtra(extra, true)
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun updateAll(ctx: Context) {
        try {
            val mgr = AppWidgetManager.getInstance(ctx)
            val sumIds = mgr.getAppWidgetIds(ComponentName(ctx, SummaryWidget::class.java))
            val quickIds = mgr.getAppWidgetIds(ComponentName(ctx, QuickWidget::class.java))
            val ringIds = mgr.getAppWidgetIds(ComponentName(ctx, RingWidget::class.java))
            if (sumIds.isEmpty() && quickIds.isEmpty() && ringIds.isEmpty()) return

            val st = Store(ctx)
            Cur.load(st)
            val entries = st.loadEntries()
            val accounts = st.loadAccounts()
            val today = LocalDate.now()
            val todayE = entries.filter { it.date == today }
            val tIn = todayE.sumOf { maxOf(0.0, statAmt(it, accounts)) }
            val tOut = -todayE.sumOf { minOf(0.0, statAmt(it, accounts)) }
            val ym = YearMonth.now()
            val monthNet = entries.filter { YearMonth.from(it.date) == ym }.sumOf { statAmt(it, accounts) }
            val total = netWorth(entries, accounts)
            val theme = st.theme
            val color = THEME_SWATCH.getOrElse(theme) { THEME_SWATCH[0] }.toArgb()
            val hide = st.getB(AppLock.WIDGET_HIDE, false)
            val mask = { s: String -> if (hide) "****" else s }

            if (sumIds.isNotEmpty()) {
                val goals = st.loadGoals().map { g -> g to calcGoal(g, entries, total) }
                val (costs, incomes) = dailyLines(st.loadSubs(), accounts, goals, st.loadDeposits())
                val costDay = costs.sumOf { it.perDay }
                val incDay = incomes.sumOf { it.perDay }
                val v = RemoteViews(ctx.packageName, R.layout.widget_summary)
                v.setTextColor(R.id.w_title, color)
                v.setTextViewText(R.id.w_date, "${today.monthValue}月${today.dayOfMonth}日 ${weekdayCn(today)}")
                v.setTextViewText(R.id.w_out, mask(money(tOut)))
                v.setTextViewText(R.id.w_in, mask(money(tIn)))
                v.setTextViewText(R.id.w_month, mask((if (monthNet < 0) "-" else "+") + money(monthNet)))
                v.setTextColor(R.id.w_month, if (monthNet < 0) 0xFFDC2626.toInt() else 0xFF16A34A.toInt())
                v.setTextViewText(
                    R.id.w_cost,
                    if (hide) "🔒 金额已隐藏 · 点「记一笔」照常记账"
                    else "每日成本 ${money(costDay)} · 每日收入 ${money(incDay)}\n总存款 " + (if (total < 0) "-" else "") + money(total),
                )
                v.setOnClickPendingIntent(R.id.w_root, open(ctx, 9001, ""))
                v.setOnClickPendingIntent(R.id.w_add, open(ctx, 9002, "open_add"))
                v.setOnClickPendingIntent(R.id.w_scan, open(ctx, 9003, "open_scan"))
                mgr.updateAppWidget(sumIds, v)
            }
            if (ringIds.isNotEmpty()) {
                val v = RemoteViews(ctx.packageName, R.layout.widget_ring)
                v.setTextColor(R.id.r_title, color)
                v.setTextViewText(R.id.r_title, "📒 ${today.monthValue}月${today.dayOfMonth}日 ${weekdayCn(today)}")
                v.setTextViewText(R.id.r_out, mask(money(tOut)))
                v.setTextViewText(R.id.r_in, mask(money(tIn)))
                v.setTextViewText(R.id.r_month, mask("本月结余 " + (if (monthNet < 0) "-" else "+") + money(monthNet)))
                val ring = if (hide) Ring("🔒 已隐藏", "🔒", "", emptyList(), 0.0) else computeRing(st, st.getI("widget_ring_mode", 0), entries, accounts, total, tOut)
                v.setImageViewBitmap(R.id.r_chart, ringBitmap(ring, color))
                v.setTextViewText(R.id.r_chart_title, ring.title)
                v.setTextViewText(
                    R.id.r_legend,
                    if (ring.parts.size <= 1) ring.sub
                    else ring.parts.take(5).withIndex().joinToString("\n") { (i, p) ->
                        val dot = listOf("🔵", "🩷", "🟢", "🟠", "🟣")[i]
                        val pct = if (ring.total > 0) (p.second / ring.total * 100).toInt() else 0
                        "$dot ${p.first} $pct%"
                    },
                )
                v.setOnClickPendingIntent(R.id.r_root, open(ctx, 9005, ""))
                v.setOnClickPendingIntent(R.id.r_add, open(ctx, 9006, "open_add"))
                mgr.updateAppWidget(ringIds, v)
            }
            if (quickIds.isNotEmpty()) {
                val v = RemoteViews(ctx.packageName, R.layout.widget_quick)
                v.setTextViewText(R.id.q_today, if (hide) "🔒" else "今天已花 ${money(tOut)}")
                v.setOnClickPendingIntent(R.id.q_root, open(ctx, 9004, "open_add"))
                mgr.updateAppWidget(quickIds, v)
            }
        } catch (e: Exception) {
            // never crash on widget refresh
        }
    }
}
