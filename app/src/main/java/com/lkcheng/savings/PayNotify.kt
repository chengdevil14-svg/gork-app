package com.lkcheng.savings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate

/** 付款通知解析结果 */
data class PayInfo(
    val amount: Double,
    val currency: String,
    val isIn: Boolean,
    val merchant: String,
    val accountId: Long,
    val accountName: String,
    val tag: String,
    val raw: String,
)

/** app 内存里的数据要重新读取时 +1（通知里一键记账后） */
object DataBus {
    val ver = mutableIntStateOf(0)
}

object Pay {
    const val ON = "pay_on"
    const val CH = "pay"
    private const val LAST = "pay_last"

    private val OUT_WORDS = listOf(
        "支出", "消费", "付款", "支付", "扣款", "payment", "paid", "spent", "purchase", "debit", "charged",
        "transaction", "you've paid", "you paid", "successful",
    )
    private val IN_WORDS = listOf("received", "收到", "入账", "收款", "credited", "refund", "退款", "cashback", "salary")
    private val SKIP_WORDS = listOf("otp", "验证码", "one-time", "declined", "failed", "失败", "reject", "未成功")

    private val AMT = Regex("""(SGD|S\$|MYR|RM|USD|US\$|\$)\s?([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

    fun enabled(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(ComponentName(ctx, PayListener::class.java).flattenToString()) || flat.contains(ctx.packageName)
    }

    fun openAccessSettings(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun parse(title: String, text: String, accounts: List<Account>): PayInfo? {
        val all = "$title\n$text"
        val lower = all.lowercase()
        if (SKIP_WORDS.any { it in lower }) return null
        val m = AMT.find(all) ?: return null
        val amount = m.groupValues[2].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null
        val isIn = IN_WORDS.any { it in lower }
        if (!isIn && OUT_WORDS.none { it in lower }) return null
        val sym = m.groupValues[1].uppercase()
        val cur = when {
            sym == "RM" || sym == "MYR" -> "MYR"
            sym == "USD" || sym == "US$" -> "USD"
            sym == "SGD" || sym == "S$" -> "SGD"
            else -> Cur.code
        }
        // 商家：「商家: $4.00」的冒号前面；不然用标题（不是 Payment successful 这种）
        val generic = listOf("payment", "successful", "transaction", "支出", "付款", "消费", "交易", "alert", "notification", "received", "收到")
        val colon = Regex("""^\s*([^:：\n]{2,40})[:：]\s*(SGD|S\$|MYR|RM|USD|US\$|\$)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.trim()
        val merchant = colon ?: title.trim().takeIf { t -> t.isNotBlank() && generic.none { it in t.lowercase() } } ?: ""
        val live = accounts.filter { !it.archived }
        val acc = live.filter { it.type != 0 }.sortedByDescending { it.name.length }.firstOrNull { a ->
            val key = a.name.replace("信用卡", "").replace("借记卡", "").replace("卡", "").trim()
            key.length >= 2 && all.contains(key, true)
        } ?: live.firstOrNull { it.type == 1 } ?: live.firstOrNull()
        val low2 = (merchant + " " + all).lowercase()
        var tag = Scan.TAG_RULES.firstOrNull { (_, words) -> words.any { w -> w in low2 } }?.first ?: ""
        if (tag.isEmpty()) tag = if (isIn) "其他" else "日常"
        return PayInfo(amount, cur, isIn, merchant, acc?.id ?: 0L, acc?.name ?: "", tag, all)
    }

    /** 同一笔付款常有两个通知（Wallet + 银行 app）：3 分钟内同金额只提示一次 */
    fun isDuplicate(store: Store, p: PayInfo): Boolean {
        val now = System.currentTimeMillis()
        val key = "${p.amount}|${p.isIn}"
        val last = store.getS(LAST, "")
        val parts = last.split("#")
        if (parts.size == 2 && parts[0] == key && now - (parts[1].toLongOrNull() ?: 0L) < 180_000L) return true
        store.setS(LAST, "$key#$now")
        return false
    }

    fun toEntry(p: PayInfo): Entry = Entry(
        id = System.currentTimeMillis(),
        date = LocalDate.now(),
        amount = if (p.isIn) p.amount else -p.amount,
        note = p.merchant,
        tag = p.tag,
        currency = p.currency,
        accountId = p.accountId,
    )

    private fun put(i: Intent, p: PayInfo): Intent = i
        .putExtra("pay_amount", p.amount)
        .putExtra("pay_cur", p.currency)
        .putExtra("pay_in", p.isIn)
        .putExtra("pay_note", p.merchant)
        .putExtra("pay_acc", p.accountId)
        .putExtra("pay_acc_name", p.accountName)
        .putExtra("pay_tag", p.tag)
        .putExtra("pay_raw", p.raw)

    fun fromIntent(i: Intent?): PayInfo? {
        if (i == null || !i.hasExtra("pay_amount")) return null
        return PayInfo(
            i.getDoubleExtra("pay_amount", 0.0),
            i.getStringExtra("pay_cur") ?: Cur.code,
            i.getBooleanExtra("pay_in", false),
            i.getStringExtra("pay_note") ?: "",
            i.getLongExtra("pay_acc", 0L),
            i.getStringExtra("pay_acc_name") ?: "",
            i.getStringExtra("pay_tag") ?: "",
            i.getStringExtra("pay_raw") ?: "",
        )
    }

    fun toScan(p: PayInfo) = ScanResult(p.amount, p.currency, p.isIn, p.tag, p.merchant, LocalDate.now(), p.accountName, "", p.raw)

    fun show(ctx: Context, p: PayInfo) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "付款通知一键记账", NotificationManager.IMPORTANCE_HIGH))
        val id = (System.currentTimeMillis() % 100000).toInt() + 50000
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val rec = put(Intent(ctx, PayActionReceiver::class.java).setAction("com.lkcheng.savings.PAY_SAVE"), p).putExtra("nid", id)
        val save = PendingIntent.getBroadcast(ctx, id, rec, flags)
        val edit = PendingIntent.getActivity(
            ctx, id + 1,
            put(Intent(ctx, MainActivity::class.java), p).putExtra("open_add", true).putExtra("nid", id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags,
        )
        val sign = if (p.isIn) "+" else "-"
        val title = "💳 $sign${moneyC(p.amount, p.currency)}" + if (p.merchant.isNotBlank()) " · ${p.merchant}" else ""
        val text = listOf(p.accountName, p.tag).filter { it.isNotBlank() }.joinToString(" · ") + "　要记下这笔吗？"
        val n = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(edit)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "✅ 一键记账", save)
            .addAction(0, "✏️ 修改再记", edit)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (e: SecurityException) {
            // no permission
        }
    }
}

class PayListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            if (sbn == null || sbn.packageName == packageName) return
            if (sbn.isOngoing) return
            val store = Store(applicationContext)
            if (!store.getB(Pay.ON, true)) return
            val ex = sbn.notification.extras ?: return
            val title = ex.getCharSequence("android.title")?.toString() ?: ""
            val text = (ex.getCharSequence("android.bigText") ?: ex.getCharSequence("android.text"))?.toString() ?: ""
            if (title.isBlank() && text.isBlank()) return
            Cur.load(store)
            val p = Pay.parse(title, text, store.loadAccounts()) ?: return
            if (Pay.isDuplicate(store, p)) return
            Pay.show(applicationContext, p)
        } catch (e: Exception) {
            // never crash the listener
        }
    }
}

class PayActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val p = Pay.fromIntent(intent) ?: return
        val store = Store(ctx.applicationContext)
        val e = Pay.toEntry(p)
        store.saveEntries(store.loadEntries() + e)
        DataBus.ver.intValue++
        Widgets.updateAll(ctx.applicationContext)
        NotificationManagerCompat.from(ctx).cancel(intent.getIntExtra("nid", 0))
        android.widget.Toast.makeText(ctx, "✅ 已记下 " + signedMoneyC(e.amount, e.currency), android.widget.Toast.LENGTH_SHORT).show()
    }
}
