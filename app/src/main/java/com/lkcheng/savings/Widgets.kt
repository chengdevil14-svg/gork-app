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

class QuickWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Widgets.updateAll(context)
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
            if (sumIds.isEmpty() && quickIds.isEmpty()) return

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

            if (sumIds.isNotEmpty()) {
                val goals = st.loadGoals().map { g -> g to calcGoal(g, entries, total) }
                val (costs, incomes) = dailyLines(st.loadSubs(), accounts, goals, st.loadDeposits())
                val costDay = costs.sumOf { it.perDay }
                val incDay = incomes.sumOf { it.perDay }
                val v = RemoteViews(ctx.packageName, R.layout.widget_summary)
                v.setTextColor(R.id.w_title, color)
                v.setTextViewText(R.id.w_date, "${today.monthValue}月${today.dayOfMonth}日 ${weekdayCn(today)}")
                v.setTextViewText(R.id.w_out, money(tOut))
                v.setTextViewText(R.id.w_in, money(tIn))
                v.setTextViewText(R.id.w_month, (if (monthNet < 0) "-" else "+") + money(monthNet))
                v.setTextColor(R.id.w_month, if (monthNet < 0) 0xFFDC2626.toInt() else 0xFF16A34A.toInt())
                v.setTextViewText(
                    R.id.w_cost,
                    "每日成本 ${money(costDay)} · 每日收入 ${money(incDay)}\n总存款 " + (if (total < 0) "-" else "") + money(total),
                )
                v.setOnClickPendingIntent(R.id.w_root, open(ctx, 9001, ""))
                v.setOnClickPendingIntent(R.id.w_add, open(ctx, 9002, "open_add"))
                v.setOnClickPendingIntent(R.id.w_scan, open(ctx, 9003, "open_scan"))
                mgr.updateAppWidget(sumIds, v)
            }
            if (quickIds.isNotEmpty()) {
                val v = RemoteViews(ctx.packageName, R.layout.widget_quick)
                v.setTextViewText(R.id.q_today, "今天已花 ${money(tOut)}")
                v.setOnClickPendingIntent(R.id.q_root, open(ctx, 9004, "open_add"))
                mgr.updateAppWidget(quickIds, v)
            }
        } catch (e: Exception) {
            // never crash on widget refresh
        }
    }
}
