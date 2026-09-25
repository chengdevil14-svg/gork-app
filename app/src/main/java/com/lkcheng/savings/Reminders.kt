package com.lkcheng.savings

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Preference keys shared by the settings screen and the background receiver. */
object K {
    const val REM_ON = "rem_on"
    const val SKIP_IF_RECORDED = "rem_skip"
    const val DUE_ON = "due_on"
    const val DUE_H = "due_h"
    const val DUE_M = "due_m"
    const val DUE_STYLE = "due_style" // 0 通知, 1 闹钟式响铃
    const val EMAIL_ON = "email_on"
    const val POPUP_ON = "popup_on"
    const val SUB_ON = "sub_on"
    const val SUB_H = "sub_h"
    const val SUB_M = "sub_m"
    const val SUB_STYLE = "sub_style"
    const val SUB_EMAIL = "sub_email"
    fun slotOn(k: String) = "rem_${k}_on"
    fun slotH(k: String) = "rem_${k}_h"
    fun slotM(k: String) = "rem_${k}_m"
}

data class Slot(
    val key: String,
    val title: String,
    val h: Int,
    val m: Int,
    val isRecord: Boolean,
    val messages: List<String>,
)

val SLOTS = listOf(
    Slot(
        "morning", "早安问候", 8, 0, false,
        listOf("早安 ☀️ 新的一天，也要好好存钱！", "早上好 ☀️ 今天也是存钱的一天", "早安！记得吃早餐，花钱记一笔 🍞"),
    ),
    Slot(
        "lunch", "午餐记账", 12, 30, true,
        listOf("午饭时间 🍜 吃完记得记一笔", "中午好！午餐花了多少？顺手记一下 🍱"),
    ),
    Slot(
        "dinner", "晚餐记账", 19, 0, true,
        listOf("晚饭时间 🍲 今天的晚餐记了吗？", "晚上好！吃饭的钱记一笔 🍛"),
    ),
    Slot(
        "night", "晚安总结", 22, 0, true,
        listOf("晚安 🌙 今天的收支都记了吗？"),
    ),
)

fun greeting(t: LocalTime = LocalTime.now()): String = when (t.hour) {
    in 5..10 -> "早上好 ☀️"
    in 11..13 -> "中午好 🍜"
    in 14..17 -> "下午好 ☕"
    in 18..21 -> "晚上好 🌆"
    else -> "夜深了 🌙"
}

/** Next due date on/after [from], clamped to month length. */
fun nextDueDate(g: Goal, from: LocalDate = LocalDate.now()): LocalDate? {
    if (g.dueDay <= 0) return null
    val ym = YearMonth.from(from)
    val thisMonth = ym.atDay(minOf(g.dueDay, ym.lengthOfMonth()))
    if (!thisMonth.isBefore(from)) return thisMonth
    val next = ym.plusMonths(1)
    return next.atDay(minOf(g.dueDay, next.lengthOfMonth()))
}

fun dueLine(g: Goal): String? {
    val d = nextDueDate(g) ?: return null
    val days = ChronoUnit.DAYS.between(LocalDate.now(), d)
    val word = if (g.debt) "还款日" else "存款日"
    val whenTxt = when (days) {
        0L -> "就是今天"
        1L -> "明天"
        else -> "还有 $days 天"
    }
    return "每月 ${g.dueDay} 号$word · 下次 ${d.monthValue}月${d.dayOfMonth}日（$whenTxt）"
}

object Reminders {
    private const val ACTION_FIRE = "com.lkcheng.savings.REMIND"
    private const val CH_DAILY = "daily"
    private const val CH_DUE = "due"
    private const val CH_DUE_ALARM = "due_alarm"
    private const val CH_SUB = "sub"

    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_DAILY, "每日问候与记账提醒", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DUE, "还款 / 存款日提醒", NotificationManager.IMPORTANCE_HIGH)
        )
        val alarm = NotificationChannel(CH_DUE_ALARM, "还款日闹钟（响铃）", NotificationManager.IMPORTANCE_HIGH)
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        alarm.setSound(
            sound,
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
        )
        alarm.enableVibration(true)
        nm.createNotificationChannel(alarm)
        nm.createNotificationChannel(
            NotificationChannel(CH_SUB, "订阅续费提醒", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun fireIntent(ctx: Context, kind: String): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra("kind", kind)
        return PendingIntent.getBroadcast(
            ctx, kind.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setAlarm(ctx: Context, kind: String, at: LocalDateTime) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = fireIntent(ctx, kind)
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun cancel(ctx: Context, kind: String) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(fireIntent(ctx, kind))
    }

    private fun nextAt(h: Int, m: Int): LocalDateTime {
        val now = LocalDateTime.now()
        var t = LocalDate.now().atTime(h, m)
        if (!t.isAfter(now)) t = t.plusDays(1)
        return t
    }

    /** Recomputes every alarm from current settings. Safe to call often. */
    fun scheduleAll(ctx: Context) {
        ensureChannels(ctx)
        val st = Store(ctx)
        val master = st.getB(K.REM_ON, false)
        for (s in SLOTS) {
            val kind = "slot:" + s.key
            if (master && st.getB(K.slotOn(s.key), true)) {
                setAlarm(ctx, kind, nextAt(st.getI(K.slotH(s.key), s.h), st.getI(K.slotM(s.key), s.m)))
            } else {
                cancel(ctx, kind)
            }
        }
        val dueOn = master && st.getB(K.DUE_ON, true)
        val h = st.getI(K.DUE_H, 9)
        val m = st.getI(K.DUE_M, 0)
        for (g in st.loadGoals()) {
            val kind = "due:" + g.id
            if (!dueOn || g.dueDay <= 0) {
                cancel(ctx, kind)
                continue
            }
            val now = LocalDateTime.now()
            var from = LocalDate.now()
            var at: LocalDateTime? = null
            for (i in 0 until 3) {
                val due = nextDueDate(g, from) ?: break
                val t = due.minusDays(g.remindBefore.toLong()).atTime(h, m)
                if (t.isAfter(now)) {
                    at = t
                    break
                }
                from = due.plusDays(1)
            }
            if (at != null) setAlarm(ctx, kind, at) else cancel(ctx, kind)
        }
        for (d in st.loadDeposits()) {
            val kind = "dep:" + d.id
            val mat = maturityOf(d)
            val at = mat?.atTime(h, m)
            if (dueOn && d.active && at != null && at.isAfter(LocalDateTime.now())) setAlarm(ctx, kind, at) else cancel(ctx, kind)
        }
        val subOn = master && st.getB(K.SUB_ON, true)
        val sh = st.getI(K.SUB_H, 10)
        val sm = st.getI(K.SUB_M, 0)
        for (s in st.loadSubs()) {
            val kind = "sub:" + s.id
            if (!subOn || !s.active) {
                cancel(ctx, kind)
                continue
            }
            val now = LocalDateTime.now()
            var d = nextBilling(s) ?: continue
            var at: LocalDateTime? = null
            for (i in 0 until 3) {
                val t = d.minusDays(s.remindBefore.toLong()).atTime(sh, sm)
                if (t.isAfter(now)) {
                    at = t
                    break
                }
                d = advance(d, s.cycle)
            }
            if (at != null) setAlarm(ctx, kind, at) else cancel(ctx, kind)
        }
    }

    private fun openAppIntent(ctx: Context, code: Int, openAdd: Boolean): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("open_add", openAdd)
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun notify(ctx: Context, id: Int, channel: String, title: String, text: String, openAdd: Boolean, insistent: Boolean) {
        if (!hasPermission(ctx)) return
        val b = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(ctx, id, openAdd))
            .setPriority(if (insistent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
        if (openAdd) b.addAction(0, "记一笔", openAppIntent(ctx, id + 1, true))
        if (insistent) b.setCategory(NotificationCompat.CATEGORY_ALARM)
        val n = b.build()
        if (insistent) n.flags = n.flags or android.app.Notification.FLAG_INSISTENT
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (e: SecurityException) {
            // permission revoked
        }
    }

    fun fire(ctx: Context, kind: String) {
        ensureChannels(ctx)
        val st = Store(ctx)
        if (!st.getB(K.REM_ON, false)) return
        Cur.load(st)
        if (kind.startsWith("sub:")) {
            fireSub(ctx, st, kind)
            return
        }
        if (kind.startsWith("dep:")) {
            if (!st.getB(K.DUE_ON, true)) return
            val id = kind.removePrefix("dep:").toLongOrNull() ?: return
            val d = st.loadDeposits().firstOrNull { it.id == id && it.active } ?: return
            val stt = calcDeposit(d)
            val text = "今天到期。本金 ${moneyC(d.principal, d.currency)}，利息约 ${moneyC(stt.atMaturity, d.currency)}，" +
                "本息合计 ${moneyC(d.principal + stt.atMaturity, d.currency)}。" + if (d.autoRenew) "已设自动续存。" else "记得处理（续存或取出）。"
            val alarmStyle = st.getI(K.DUE_STYLE, 0) == 1
            notify(ctx, kind.hashCode(), if (alarmStyle) CH_DUE_ALARM else CH_DUE, "💰 ${d.name} 到期提醒", text, false, alarmStyle)
            return
        }
        val entries = st.loadEntries()
        val today = LocalDate.now()
        val todayEntries = entries.filter { it.date == today }
        if (kind.startsWith("slot:")) {
            val s = SLOTS.firstOrNull { "slot:" + it.key == kind } ?: return
            if (!st.getB(K.slotOn(s.key), true)) return
            if (s.isRecord && s.key != "night" && st.getB(K.SKIP_IF_RECORDED, true) && todayEntries.isNotEmpty()) return
            var text = s.messages.random()
            if (s.key == "night") {
                val inSum = todayEntries.filter { it.amount > 0 && !it.isTransfer }.sumOf { it.base }
                val outSum = -todayEntries.filter { it.amount < 0 && !it.isTransfer }.sumOf { it.base }
                text = if (todayEntries.isEmpty()) {
                    "晚安 🌙 今天还没有记账，花一分钟记一下吧"
                } else {
                    "晚安 🌙 今天记了 ${todayEntries.size} 笔：存入 ${money(inSum)}，取出 ${money(outSum)}"
                }
            }
            notify(ctx, kind.hashCode(), CH_DAILY, "存钱记录", text, s.isRecord, false)
        } else if (kind.startsWith("due:")) {
            if (!st.getB(K.DUE_ON, true)) return
            val id = kind.removePrefix("due:").toLongOrNull() ?: return
            val g = st.loadGoals().firstOrNull { it.id == id } ?: return
            val due = nextDueDate(g) ?: return
            val stat = calcGoal(g, entries, entries.sumOf { it.base })
            if (stat.remaining <= 0.0) return
            val days = ChronoUnit.DAYS.between(today, due)
            val word = if (g.debt) "还款" else "存款"
            val whenTxt = if (days <= 0L) "今天是${word}日" else "${days} 天后（${due.monthValue}月${due.dayOfMonth}日）是${word}日"
            val v = if (g.debt) "还" else "存"
            val plan = if (g.monthly > 0) {
                val left = g.monthly - stat.thisMonth
                if (left > 0) "本月计划$v ${money(g.monthly)}，还差 ${money(left)}。" else "本月计划已完成 ✅"
            } else {
                ""
            }
            val text = "$whenTxt。$plan${if (g.debt) "还欠" else "还差"} ${money(stat.remaining)}。"
            val alarmStyle = st.getI(K.DUE_STYLE, 0) == 1
            notify(ctx, kind.hashCode(), if (alarmStyle) CH_DUE_ALARM else CH_DUE, "💳 ${g.name} ${word}提醒", text, true, alarmStyle)
        }
    }

    private fun fireSub(ctx: Context, st: Store, kind: String) {
        if (!st.getB(K.SUB_ON, true)) return
        val id = kind.removePrefix("sub:").toLongOrNull() ?: return
        val s = st.loadSubs().firstOrNull { it.id == id && it.active } ?: return
        val d = nextBilling(s) ?: return
        val days = ChronoUnit.DAYS.between(LocalDate.now(), d)
        val whenTxt = if (days <= 0L) "今天" else "$days 天后（${d.monthValue}月${d.dayOfMonth}日）"
        if (s.isIncome) {
            notify(ctx, kind.hashCode(), CH_SUB, "💰 ${s.name}", "${s.name} $whenTxt 入账 ${moneyC(s.amount, s.currency)}", false, false)
            return
        }
        val text = if (s.wantCancel) {
            "✂️ ${s.name} $whenTxt 会自动续费 ${moneyC(s.amount, s.currency)}。你标记了想取消，记得在扣款前去取消！"
        } else {
            "${s.name} $whenTxt 会扣款 ${moneyC(s.amount, s.currency)}（${CYCLE_NAMES.getOrElse(s.cycle) { "每月" }}）。还在用吗？不用的话可以先取消。"
        }
        val alarmStyle = st.getI(K.SUB_STYLE, 0) == 1
        notify(ctx, kind.hashCode(), if (alarmStyle) CH_DUE_ALARM else CH_SUB, "🔁 订阅续费提醒：${s.name}", text, false, alarmStyle)
    }

    fun addSubToCalendar(ctx: Context, s: Sub, h: Int, m: Int): Boolean = try {
        val d = nextBilling(s) ?: LocalDate.now()
        val start = d.atTime(h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val rule = when (s.cycle) {
            1 -> "FREQ=YEARLY"
            2 -> "FREQ=WEEKLY"
            else -> "FREQ=MONTHLY"
        }
        val i = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 30 * 60 * 1000)
            .putExtra(CalendarContract.Events.TITLE, "🔁 ${s.name} 续费 ${moneyC(s.amount, s.currency)}")
            .putExtra(
                CalendarContract.Events.DESCRIPTION,
                if (s.wantCancel) "存钱记录：记得在扣款前取消 ${s.name}" else "存钱记录：${s.name} 自动续费",
            )
            .putExtra(CalendarContract.Events.RRULE, rule)
            .putExtra(CalendarContract.Events.HAS_ALARM, 1)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        true
    } catch (e: Exception) {
        false
    }

    fun test(ctx: Context) {
        ensureChannels(ctx)
        notify(ctx, 777, CH_DAILY, "存钱记录", "测试通知 ✅ 提醒可以正常显示", true, false)
    }

    /** Opens the phone's clock app with a daily alarm pre-filled. */
    fun addPhoneAlarm(ctx: Context, h: Int, m: Int, label: String): Boolean = try {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(
                AlarmClock.EXTRA_DAYS,
                arrayListOf(
                    java.util.Calendar.MONDAY, java.util.Calendar.TUESDAY, java.util.Calendar.WEDNESDAY,
                    java.util.Calendar.THURSDAY, java.util.Calendar.FRIDAY, java.util.Calendar.SATURDAY,
                    java.util.Calendar.SUNDAY,
                ),
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        true
    } catch (e: Exception) {
        false
    }

    /** Opens the phone's calendar with a monthly repeating event + reminder. */
    fun addToCalendar(ctx: Context, g: Goal, h: Int, m: Int): Boolean = try {
        val due = nextDueDate(g) ?: LocalDate.now()
        val start = due.atTime(h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val word = if (g.debt) "还款日" else "存款日"
        val desc = buildString {
            append("存钱记录：${g.name} 每月 ${g.dueDay} 号$word")
            if (g.monthly > 0) append("，计划${if (g.debt) "还" else "存"} ${money(g.monthly)}")
        }
        val i = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 30 * 60 * 1000)
            .putExtra(CalendarContract.Events.TITLE, "💳 ${g.name} $word")
            .putExtra(CalendarContract.Events.DESCRIPTION, desc)
            .putExtra(CalendarContract.Events.RRULE, "FREQ=MONTHLY;BYMONTHDAY=${g.dueDay}")
            .putExtra(CalendarContract.Events.HAS_ALARM, 1)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        true
    } catch (e: Exception) {
        false
    }

    fun openAppSettings(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // ignore
        }
    }

    fun openNotificationSettings(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            openAppSettings(ctx)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action == "com.lkcheng.savings.REMIND") {
                Reminders.fire(context, intent.getStringExtra("kind") ?: "")
            }
        } catch (e: Exception) {
            // never crash in background
        }
        try {
            Reminders.scheduleAll(context)
        } catch (e: Exception) {
            // ignore
        }
    }
}
