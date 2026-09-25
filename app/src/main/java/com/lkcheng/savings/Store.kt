package com.lkcheng.savings

import android.content.Context
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
            )
        }
        sp.edit().putString("entries", arr.toString()).apply()
    }

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

fun money(v: Double): String = "$" + String.format(Locale.US, "%,.2f", abs(v))

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
