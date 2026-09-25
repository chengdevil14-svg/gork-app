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

    fun upsert(context: Context, url: String, e: Entry) {
        val body = JSONObject()
            .put("action", "upsert")
            .put("id", e.id.toString())
            .put("date", e.date.toString())
            .put("amount", e.amount)
            .put("note", e.note)
            .put("hasPhoto", e.photo.isNotEmpty())
        if (e.photo.isNotEmpty() && !e.photoSent) {
            val b64 = Photos.base64(context, e.photo)
            if (b64 != null) body.put("photo", b64)
        }
        call(url, body)
    }
}
