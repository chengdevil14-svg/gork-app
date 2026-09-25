package com.lkcheng.savings

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

data class Entry(
    val id: Long,
    val date: LocalDate,
    val amount: Double, // positive = 存入, negative = 取出
    val note: String,
    val photo: String = "", // file name under filesDir/photos, "" = none
    val synced: Boolean = false,
    val photoSent: Boolean = false,
    val tag: String = "",
    val goalId: Long = 0L,
    val currency: String = "SGD",
)

data class Goal(
    val id: Long,
    val name: String,
    val debt: Boolean, // true = 欠款, false = 存款目标
    val target: Double,
    val monthly: Double = 0.0,
    val deadline: String = "", // yyyy-MM or ""
    val startDone: Double = 0.0,
    val useTotal: Boolean = false, // 存款目标: 用总存款计算进度
    val dueDay: Int = 0, // 每月还款/存款日 1-31, 0 = 不提醒
    val remindBefore: Int = 0, // 提前几天提醒
)

data class Sub(
    val id: Long,
    val name: String,
    val amount: Double,
    val cycle: Int = 0, // 0 每月, 1 每年, 2 每周
    val nextDate: String, // yyyy-MM-dd 下次扣款日（会自动往后滚）
    val remindBefore: Int = 1,
    val wantCancel: Boolean = false,
    val autoRecord: Boolean = false,
    val active: Boolean = true,
    val recordedUntil: String = "",
    val currency: String = "SGD",
)

class Store(context: Context) {
    private val sp = context.getSharedPreferences("savings", Context.MODE_PRIVATE)

    fun loadEntries(): List<Entry> {
        val arr = try {
            JSONArray(sp.getString("entries", "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
        val out = ArrayList<Entry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            try {
                out.add(
                    Entry(
                        id = o.getLong("id"),
                        date = LocalDate.parse(o.getString("date")),
                        amount = o.getDouble("amount"),
                        note = o.optString("note", ""),
                        photo = o.optString("photo", ""),
                        synced = o.optBoolean("synced", false),
                        photoSent = o.optBoolean("photoSent", false),
                        tag = o.optString("tag", ""),
                        goalId = o.optLong("goalId", 0L),
                        currency = o.optString("currency", "SGD").ifBlank { "SGD" },
                    )
                )
            } catch (e: Exception) {
                // skip broken record
            }
        }
        return out
    }

    fun saveEntries(list: List<Entry>) {
        val arr = JSONArray()
        for (e in list) {
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("date", e.date.toString())
                    .put("amount", e.amount)
                    .put("note", e.note)
                    .put("photo", e.photo)
                    .put("synced", e.synced)
                    .put("photoSent", e.photoSent)
                    .put("tag", e.tag)
                    .put("goalId", e.goalId)
                    .put("currency", e.currency)
            )
        }
        sp.edit().putString("entries", arr.toString()).apply()
    }

    fun loadGoals(): List<Goal> {
        if (!sp.contains("goals")) {
            val old = goal
            val list = if (old > 0) listOf(Goal(1L, "存钱目标", false, old, useTotal = true)) else emptyList()
            saveGoals(list)
            return list
        }
        val arr = try {
            JSONArray(sp.getString("goals", "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
        val out = ArrayList<Goal>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            try {
                out.add(
                    Goal(
                        id = o.getLong("id"),
                        name = o.optString("name", "目标"),
                        debt = o.optBoolean("debt", false),
                        target = o.optDouble("target", 0.0),
                        monthly = o.optDouble("monthly", 0.0),
                        deadline = o.optString("deadline", ""),
                        startDone = o.optDouble("startDone", 0.0),
                        useTotal = o.optBoolean("useTotal", false),
                        dueDay = o.optInt("dueDay", 0),
                        remindBefore = o.optInt("remindBefore", 0),
                    )
                )
            } catch (e: Exception) {
                // skip
            }
        }
        return out
    }

    fun saveGoals(list: List<Goal>) {
        val arr = JSONArray()
        for (g in list) {
            arr.put(
                JSONObject()
                    .put("id", g.id)
                    .put("name", g.name)
                    .put("debt", g.debt)
                    .put("target", g.target)
                    .put("monthly", g.monthly)
                    .put("deadline", g.deadline)
                    .put("startDone", g.startDone)
                    .put("useTotal", g.useTotal)
                    .put("dueDay", g.dueDay)
                    .put("remindBefore", g.remindBefore)
            )
        }
        sp.edit().putString("goals", arr.toString()).apply()
    }

    fun loadSubs(): List<Sub> {
        val arr = try {
            JSONArray(sp.getString("subs", "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
        val out = ArrayList<Sub>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            try {
                out.add(
                    Sub(
                        id = o.getLong("id"),
                        name = o.optString("name", "订阅"),
                        amount = o.optDouble("amount", 0.0),
                        cycle = o.optInt("cycle", 0),
                        nextDate = o.getString("nextDate"),
                        remindBefore = o.optInt("remindBefore", 1),
                        wantCancel = o.optBoolean("wantCancel", false),
                        autoRecord = o.optBoolean("autoRecord", false),
                        active = o.optBoolean("active", true),
                        recordedUntil = o.optString("recordedUntil", ""),
                        currency = o.optString("currency", "SGD").ifBlank { "SGD" },
                    )
                )
            } catch (e: Exception) {
                // skip
            }
        }
        return out
    }

    fun saveSubs(list: List<Sub>) {
        val arr = JSONArray()
        for (x in list) {
            arr.put(
                JSONObject()
                    .put("id", x.id)
                    .put("name", x.name)
                    .put("amount", x.amount)
                    .put("cycle", x.cycle)
                    .put("nextDate", x.nextDate)
                    .put("remindBefore", x.remindBefore)
                    .put("wantCancel", x.wantCancel)
                    .put("autoRecord", x.autoRecord)
                    .put("active", x.active)
                    .put("recordedUntil", x.recordedUntil)
                    .put("currency", x.currency)
            )
        }
        sp.edit().putString("subs", arr.toString()).apply()
    }

    fun getB(key: String, def: Boolean) = sp.getBoolean(key, def)
    fun setB(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()
    fun getI(key: String, def: Int) = sp.getInt(key, def)
    fun getS(key: String, def: String) = sp.getString(key, def) ?: def
    fun setS(key: String, v: String) = sp.edit().putString(key, v).apply()
    fun setI(key: String, v: Int) = sp.edit().putInt(key, v).apply()

    var goal: Double
        get() = sp.getString("goal", "0")?.toDoubleOrNull() ?: 0.0
        set(value) {
            sp.edit().putString("goal", value.toString()).apply()
        }

    /** true: 存入=绿 取出=红；false: 存入=红 取出=绿 */
    var greenForIncome: Boolean
        get() = sp.getBoolean("greenIncome", true)
        set(value) {
            sp.edit().putBoolean("greenIncome", value).apply()
        }

    /** 0 白蓝, 1 深夜, 2 暖金 */
    var theme: Int
        get() = sp.getInt("theme", 0)
        set(value) {
            sp.edit().putInt("theme", value).apply()
        }

    var currency: String
        get() = sp.getString("currency", "SGD") ?: "SGD"
        set(value) {
            sp.edit().putString("currency", value).apply()
        }

    var syncUrl: String
        get() = sp.getString("syncUrl", "") ?: ""
        set(value) {
            sp.edit().putString("syncUrl", value.trim()).apply()
        }

    var sheetUrl: String
        get() = sp.getString("sheetUrl", "") ?: ""
        set(value) {
            sp.edit().putString("sheetUrl", value).apply()
        }

    fun pendingDeletes(): List<Long> {
        val arr = try {
            JSONArray(sp.getString("pendingDeletes", "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
        return (0 until arr.length()).map { arr.optLong(it) }.filter { it != 0L }
    }

    private fun setPendingDeletes(ids: List<Long>) {
        val arr = JSONArray()
        ids.forEach { arr.put(it) }
        sp.edit().putString("pendingDeletes", arr.toString()).apply()
    }

    fun addPendingDelete(id: Long) = setPendingDeletes(pendingDeletes() + id)

    fun removePendingDelete(id: Long) = setPendingDeletes(pendingDeletes().filter { it != id })
}

val CURRENCIES = listOf(
    Triple("SGD", "SGD", "新币 SGD"),
    Triple("MYR", "MYR", "马币 MYR"),
    Triple("USD", "USD", "美金 USD"),
)

/** Current currency symbol; backed by Compose state so the UI refreshes when it changes. */
object Cur {
    private val st = mutableStateOf("SGD")
    private val codeSt = mutableStateOf("SGD")
    val symbol: String get() = st.value
    val code: String get() = codeSt.value
    fun set(code: String) {
        val c = CURRENCIES.firstOrNull { it.first == code } ?: CURRENCIES[0]
        codeSt.value = c.first
        st.value = c.second
    }
    fun load(store: Store) {
        set(store.currency)
        Fx.load(store)
    }
}

fun symbolOf(code: String) = CURRENCIES.firstOrNull { it.first == code }?.second ?: code

/** Exchange rates, stored as "value of 1 unit in SGD". */
object Fx {
    private val rates = mutableStateOf(mapOf("SGD" to 1.0, "MYR" to 0.29, "USD" to 1.29))
    private val updated = mutableStateOf("")
    val updatedAt: String get() = updated.value
    fun sgdPer(code: String): Double = rates.value[code] ?: 1.0
    fun toBase(amount: Double, from: String): Double {
        val base = Cur.code
        if (from == base) return amount
        return amount * sgdPer(from) / sgdPer(base)
    }
    /** "1 USD = 1.29 SGD" style value, relative to the settlement currency. */
    fun perBase(code: String): Double = sgdPer(code) / sgdPer(Cur.code)
    fun load(store: Store) {
        rates.value = mapOf(
            "SGD" to 1.0,
            "MYR" to (store.getS("fx_MYR", "0.29").toDoubleOrNull() ?: 0.29),
            "USD" to (store.getS("fx_USD", "1.29").toDoubleOrNull() ?: 1.29),
        )
        updated.value = store.getS("fx_time", "")
    }
    /** Sets how many settlement-currency units 1 [code] is worth. */
    fun setPerBase(store: Store, code: String, perBase: Double) {
        val sgd = perBase * sgdPer(Cur.code)
        if (code == "SGD" || sgd <= 0) return
        store.setS("fx_$code", sgd.toString())
        load(store)
    }
    fun saveSgdRates(store: Store, myr: Double, usd: Double, time: String) {
        store.setS("fx_MYR", myr.toString())
        store.setS("fx_USD", usd.toString())
        store.setS("fx_time", time)
        load(store)
    }
}

/** Amount converted into the settlement currency. */
val Entry.base: Double get() = Fx.toBase(amount, currency)

fun moneyC(v: Double, code: String): String = symbolOf(code) + " " + String.format(Locale.US, "%,.2f", abs(v))

fun signedMoneyC(v: Double, code: String): String = (if (v < 0) "-" else "+") + moneyC(v, code)

fun money(v: Double): String = Cur.symbol + " " + String.format(Locale.US, "%,.2f", abs(v))

fun signedMoney(v: Double): String = (if (v < 0) "-" else "+") + money(v)

fun plainNumber(v: Double): String =
    if (v == floor(v)) v.toLong().toString()
    else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

fun compact(v: Double): String {
    val a = abs(v)
    val s = when {
        a >= 1_000_000 -> String.format(Locale.US, "%.1fm", a / 1_000_000)
        a >= 10_000 -> String.format(Locale.US, "%.0fk", a / 1000)
        a >= 1000 -> String.format(Locale.US, "%.1fk", a / 1000)
        a == floor(a) -> a.toLong().toString()
        else -> String.format(Locale.US, "%.1f", a)
    }
    return (if (v < 0) "-" else "+") + s
}

private val WEEK = arrayOf("一", "二", "三", "四", "五", "六", "日")

fun weekdayCn(d: LocalDate): String = "星期" + WEEK[d.dayOfWeek.value - 1]

fun weekHeaders(): List<String> = WEEK.toList()
