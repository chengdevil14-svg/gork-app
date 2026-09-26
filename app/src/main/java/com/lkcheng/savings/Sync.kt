package com.lkcheng.savings

import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Talks to the Google Apps Script web app that writes rows into Google Sheet. */
object Sync {

    /** POSTs JSON; follows Apps Script's redirect to read the reply. Throws on any failure. */
    fun call(url: String, body: JSONObject): JSONObject {
        if (!url.startsWith("https://")) throw IOException("网址要以 https:// 开头")
        var conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 20000
        conn.readTimeout = 90000
        conn.setRequestProperty("Content-Type", "text/plain;charset=utf-8")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        var code = conn.responseCode
        var hops = 0
        while (code in 300..399 && hops < 5) {
            val loc = conn.getHeaderField("Location") ?: break
            conn.disconnect()
            conn = URL(loc).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 20000
            conn.readTimeout = 90000
            code = conn.responseCode
            hops++
        }
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) throw IOException("连接失败 (HTTP $code)，请检查网址")
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IOException("网址不对，或部署时「谁可以访问」没选「任何人」")
        }
        if (!json.optBoolean("ok", false)) {
            throw IOException(json.optString("error", "脚本返回错误"))
        }
        return json
    }

    fun ping(url: String): JSONObject = call(url, JSONObject().put("action", "ping"))

    fun delete(url: String, id: Long) {
        call(url, JSONObject().put("action", "delete").put("id", id.toString()))
    }

    fun upsert(context: Context, url: String, e: Entry, goalName: String, accountName: String, toAccountName: String, stat: Double) {
        val body = JSONObject()
            .put("action", "upsert")
            .put("id", e.id.toString())
            .put("date", e.date.toString())
            .put("amount", e.amount)
            .put("currency", e.currency)
            .put("base", e.base)
            .put("baseCode", Cur.code)
            .put("note", e.note)
            .put("tag", e.tag)
            .put("goal", goalName)
            .put("account", accountName)
            .put("toAccount", toAccountName)
            .put("transfer", e.isTransfer)
            .put("stat", stat)
            .put("hasPhoto", e.photo.isNotEmpty())
        if (e.photo.isNotEmpty() && !e.photoSent) {
            val b64 = Photos.base64(context, e.photo)
            if (b64 != null) body.put("photo", b64)
        }
        call(url, body)
    }

    fun goals(
        url: String,
        list: List<Pair<Goal, GoalStat>>,
        emailOn: Boolean,
        subs: List<Sub>,
        subEmail: Boolean,
        accounts: List<Pair<Account, Double>> = emptyList(),
        deposits: List<Deposit> = emptyList(),
    ) {
        val aArr = org.json.JSONArray()
        for ((a, bal) in accounts) {
            aArr.put(JSONObject().put("name", a.name).put("type", a.type).put("currency", a.currency).put("balance", round2(bal)))
        }
        val dArr = org.json.JSONArray()
        for (d in deposits.filter { it.active }) {
            val st = calcDeposit(d)
            dArr.put(
                JSONObject().put("name", d.name).put("principal", d.principal).put("rate", d.rate)
                    .put("maturity", st.maturity?.toString() ?: "").put("accrued", round2(st.accrued))
            )
        }
        val sArr = org.json.JSONArray()
        for (x in subs) {
            sArr.put(
                JSONObject()
                    .put("name", x.name)
                    .put("amount", x.amount)
                    .put("currency", x.currency)
                    .put("cycle", CYCLE_NAMES.getOrElse(x.cycle) { "每月" })
                    .put("next", nextBilling(x)?.toString() ?: "")
                    .put("remindBefore", x.remindBefore)
                    .put("wantCancel", x.wantCancel)
                    .put("active", x.active)
                    .put("monthlyBase", monthlyCost(x))
                    .put("isIncome", x.isIncome)
            )
        }
        val arr = org.json.JSONArray()
        for ((g, st) in list) {
            arr.put(
                JSONObject()
                    .put("name", g.name)
                    .put("type", if (g.debt) "欠款" else "存款目标")
                    .put("currency", g.currency)
                    .put("target", g.target)
                    .put("done", st.done)
                    .put("remaining", st.remaining)
                    .put("pct", st.pct)
                    .put("monthly", g.monthly)
                    .put("thisMonth", st.thisMonth)
                    .put("finish", if (st.remaining <= 0) "已完成" else st.finish?.toString() ?: "")
                    .put("deadline", g.deadline)
                    .put("need", st.needPerMonth ?: 0.0)
                    .put("dueDay", g.dueDay)
                    .put("remindBefore", g.remindBefore)
            )
        }
        call(
            url,
            JSONObject().put("action", "goals").put("goals", arr).put("emailOn", emailOn)
                .put("subs", sArr).put("subEmail", subEmail).put("baseCode", Cur.code)
                .put("accounts", aArr).put("deposits", dArr),
        )
    }
}
