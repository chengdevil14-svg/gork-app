package com.lkcheng.savings

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What we could read from a payment screenshot. Every field is a guess the user confirms. */
data class ScanResult(
    val amount: Double?,
    val currency: String?,
    val isIn: Boolean,
    val tag: String,
    val note: String,
    val date: LocalDate?,
    val accountHint: String, // e.g. "Touch 'n Go"
    val photo: String, // imported screenshot file name
    val rawText: String,
)

object Scan {

    /** Runs on-device OCR (Chinese + Latin), then parses. Callback is on the main thread. */
    fun recognize(ctx: Context, uri: Uri, photo: String, done: (ScanResult?) -> Unit) {
        val image = try {
            InputImage.fromFilePath(ctx, uri)
        } catch (e: Exception) {
            done(null)
            return
        }
        val client = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        val h = image.height.toFloat()
        client.process(image)
            .addOnSuccessListener { res ->
                // skip the phone status bar (clock, network speed, battery) at the very top
                val all = res.textBlocks.flatMap { b -> b.lines }
                    .filter { l -> (l.boundingBox?.top ?: 0) > h * 0.055f }
                    .sortedBy { it.boundingBox?.top ?: 0 }
                val lines = all.map { norm(it.text) }
                val bigSorted = all.sortedByDescending { it.boundingBox?.height() ?: 0 }
                val big = bigSorted.map { norm(it.text) }.toMutableList()
                // big amount split into two lines ("SGD2" + ".16"): glue the line right after it
                if (bigSorted.isNotEmpty()) {
                    val top = bigSorted[0]
                    val idx = all.indexOf(top)
                    val next = all.getOrNull(idx + 1)
                    if (next != null && Regex("""^\s*[.]?\s*\d{2}\b""").containsMatchIn(norm(next.text)) &&
                        !Regex("""\d[.]\d{2}""").containsMatchIn(norm(top.text))
                    ) {
                        big.add(0, norm(top.text) + "." + norm(next.text).trim().trimStart('.'))
                    }
                }
                done(parse(lines, photo, big))
                client.close()
            }
            .addOnFailureListener {
                done(null)
                client.close()
            }
    }

    private val TAG_RULES: List<Pair<String, List<String>>> = listOf(
        "餐饮" to listOf(
            "restaurant", "restoran", "kopitiam", "cafe", "coffee", "kopi", "mcdonald", "kfc", "burger", "pizza",
            "grabfood", "foodpanda", "deliveroo", "shopeefood", "food", "makan", "nasi", "mee", "bakery", "tealive",
            "starbucks", "zus", "chagee", "mixue", "sushi", "bbq", "kitchen", "dining", "hawker", "餐", "饭", "食", "咖啡", "茶", "面",
        ),
        "日常" to listOf(
            "99 speedmart", "speedmart", "mydin", "lotus", "tesco", "giant", "aeon", "jaya grocer", "village grocer",
            "ntuc", "fairprice", "sheng siong", "cold storage", "7-eleven", "7eleven", "familymart", "kk mart",
            "watsons", "guardian", "mr diy", "daiso", "grocer", "mart", "supermarket", "超市", "便利", "日用",
        ),
        "交通" to listOf(
            "grab ride", "grabcar", "gojek", "taxi", "mrt", "lrt", "bus", "rapidkl", "simplygo", "ez-link", "ezlink",
            "touch n go toll", "toll", "plus", "petronas", "shell", "caltex", "petron", "esso", "bhp", "parking",
            "park", "fuel", "petrol", "交通", "油", "停车",
        ),
        "购物" to listOf("shopee", "lazada", "taobao", "amazon", "zalora", "uniqlo", "h&m", "ikea", "mall", "store", "购物", "淘宝"),
        "水电" to listOf("tnb", "tenaga", "air selangor", "syabas", "sp group", "sp services", "utilities", "indah water", "水电", "电费", "水费"),
        "电话网络" to listOf("maxis", "celcom", "digi", "u mobile", "unifi", "time internet", "yes 4g", "singtel", "starhub", "m1", "circles", "hotlink", "reload", "prepaid", "话费", "网络"),
        "娱乐" to listOf("netflix", "spotify", "cinema", "gsc", "tgv", "golden village", "steam", "playstation", "game", "karaoke", "娱乐", "电影"),
        "医疗" to listOf("clinic", "klinik", "hospital", "pharmacy", "farmasi", "dental", "医疗", "诊所", "药"),
        "保险" to listOf("insurance", "takaful", "prudential", "aia", "great eastern", "allianz", "保险"),
    )

    private val TRANSFER_WORDS = listOf("transfer", "duitnow", "paynow", "fund transfer", "ibg", "转账", "转帐", "汇款")
    private val IN_WORDS = listOf(
        "received", "receive money", "money in", "refund", "cashback", "credited", "incoming", "you got",
        "收到", "收款", "已收", "退款", "入账", "返现",
    )
    private val SKIP_WORDS = listOf(
        "transaction", "details", "detail", "successful", "success", "payment", "paid", "amount", "total", "date",
        "time", "reference", "ref", "status", "wallet", "ewallet", "balance", "receipt", "share", "done", "back",
        "touch 'n go", "touch n go", "tng", "grabpay", "boost", "completed", "merchant", "method", "交易", "详情", "成功",
        "付款", "金额", "日期", "时间", "状态", "余额", "参考", "完成", "收据",
    )
    private val MERCHANT_LABELS = listOf(
        "对账单名称", "statement name", "merchant", "paid to", "pay to", "to:", "recipient", "payee",
        "商家", "收款方", "付款给", "转给", "商户",
    )

    private val bigAmountRe = Regex("""(RM|MYR|S\$|SGD|US\$|USD)\s*([+\-−]?)\s*(\d{1,3}(?:,\d{3})+|\d+)(?:[.,](\d{2}))?(?!\d)""", RegexOption.IGNORE_CASE)

    private val amountRe = Regex(
        """(RM|MYR|S\$|SGD|US\$|USD|\$)?\s*([+\-−]?)\s*(\d{1,3}(?:,\d{3})+|\d+)\.(\d{2})(?!\d)""",
        RegexOption.IGNORE_CASE,
    )

    /** Normalise OCR quirks: full-width chars, odd decimal points, digits split by spaces. */
    fun norm(s0: String): String {
        val sb = StringBuilder()
        for (ch in s0) {
            sb.append(
                when (ch) {
                    in '\uFF10'..'\uFF19' -> '0' + (ch - '\uFF10')
                    in '\uFF21'..'\uFF3A' -> 'A' + (ch - '\uFF21')
                    in '\uFF41'..'\uFF5A' -> 'a' + (ch - '\uFF41')
                    '。', '．', '·', '∙', '•', '・', '˙' -> '.'
                    '，' -> ','
                    '＄' -> '$'
                    else -> ch
                }
            )
        }
        var s = sb.toString()
        s = s.replace(Regex("""(\d)\s*\.\s*(\d)"""), "$1.$2") // "2 . 16" → "2.16"
        // currency-marked amounts: decimal point misread as ":" / "：" / space → "SGD2:16" = 2.16
        s = s.replace(Regex("""((?:SGD|MYR|USD|RM|S\$|US\$)\s*\d+)\s*[:：∶;]\s*(\d{2})(?!\d)""", RegexOption.IGNORE_CASE), "$1.$2")
        s = s.replace(Regex("""((?:SGD|MYR|USD|RM|S\$|US\$)\s*\d+)\s+(\d{2})(?!\d)""", RegexOption.IGNORE_CASE), "$1.$2") // "SGD2 16"
        return s
    }

    fun parse(lines: List<String>, photo: String, big: List<String> = emptyList()): ScanResult {
        val all = lines.joinToString("\n")
        val lower = all.lowercase(Locale.ROOT)

        // Amount: prefer one with a currency mark, then the largest number.
        data class Amt(val v: Double, val cur: String?, val sign: String, val marked: Boolean)
        val amts = amountRe.findAll(all).mapNotNull { m ->
            val v = (m.groupValues[3].replace(",", "") + "." + m.groupValues[4]).toDoubleOrNull() ?: return@mapNotNull null
            val c = m.groupValues[1].uppercase(Locale.ROOT)
            val code = when {
                c == "RM" || c == "MYR" -> "MYR"
                c == "S$" || c == "SGD" -> "SGD"
                c == "US$" || c == "USD" -> "USD"
                else -> null
            }
            Amt(v, code, m.groupValues[2], c.isNotEmpty())
        }.filter { it.v > 0 && it.v < 1_000_000 }.toList()
        // 1) the biggest text on screen is usually the amount
        var bigAmt: Amt? = null
        for (l in big.take(4)) {
            val m1 = amountRe.find(l)
            if (m1 != null) {
                val v = (m1.groupValues[3].replace(",", "") + "." + m1.groupValues[4]).toDoubleOrNull()
                if (v != null && v > 0) {
                    val c = m1.groupValues[1].uppercase(Locale.ROOT)
                    bigAmt = Amt(v, codeOf(c), m1.groupValues[2], c.isNotEmpty())
                    break
                }
            }
            val m2 = bigAmountRe.find(l)
            if (m2 != null) {
                val digits = m2.groupValues[3].replace(",", "")
                val cents = m2.groupValues[4]
                // big font often loses the decimal point: "SGD216" → 2.16
                val v = if (cents.isNotEmpty()) "$digits.$cents".toDoubleOrNull()
                else if (digits.length >= 3) digits.toDouble() / 100.0
                else digits.toDoubleOrNull()
                if (v != null && v > 0) {
                    bigAmt = Amt(v, codeOf(m2.groupValues[1].uppercase(Locale.ROOT)), m2.groupValues[2], true)
                    break
                }
            }
        }
        val best = bigAmt ?: amts.firstOrNull { it.marked } ?: amts.maxByOrNull { it.v }

        var currency = best?.cur
        if (currency == null) {
            currency = when {
                "rm" in lower.split(Regex("[^a-z]")) || "myr" in lower || "touch 'n go" in lower || "duitnow" in lower -> "MYR"
                "sgd" in lower || "paynow" in lower || "paylah" in lower -> "SGD"
                "usd" in lower -> "USD"
                else -> null
            }
        }

        val isTransfer = TRANSFER_WORDS.any { it in lower }
        val isIn = best?.sign == "+" || IN_WORDS.any { it in lower }

        // Category by keywords
        var tag = TAG_RULES.firstOrNull { (_, words) -> words.any { w -> w in lower } }?.first ?: ""
        if (isTransfer && tag.isEmpty()) tag = if (isIn) "转账收入" else "转账"
        if (isIn && tag.isEmpty()) tag = if ("refund" in lower || "退款" in lower) "退款" else "其他"
        if (tag.isEmpty()) tag = "日常"

        // Merchant / payee: the line after a label, else first "name-like" line
        var note = ""
        for ((i, l) in lines.withIndex()) {
            val ll = l.lowercase(Locale.ROOT).trim()
            val label = MERCHANT_LABELS.firstOrNull { ll.startsWith(it) }
            if (label != null) {
                val rest = l.substring(minOf(l.length, label.length)).trim(' ', ':', '：')
                note = if (rest.length >= 2) rest else lines.getOrNull(i + 1)?.trim() ?: ""
                break
            }
        }
        if (note.isEmpty()) {
            note = big.map { it.trim() }.take(4).firstOrNull { l ->
                val ll = l.lowercase(Locale.ROOT)
                l.length in 3..40 && l.count { it.isLetter() } >= 3 && amountRe.find(l) == null &&
                    bigAmountRe.find(l) == null && SKIP_WORDS.none { w -> ll == w }
            } ?: ""
        }
        if (note.isEmpty()) {
            note = lines.map { it.trim() }.firstOrNull { l ->
                val ll = l.lowercase(Locale.ROOT)
                l.length in 3..40 &&
                    l.any { it.isLetter() } &&
                    amountRe.find(l) == null &&
                    parseDateIn(l) == null &&
                    !Regex("""\d{1,2}:\d{2}""").containsMatchIn(l) &&
                    SKIP_WORDS.none { w -> ll == w || ll.startsWith("$w ") || ll.startsWith(w) && ll.length <= w.length + 3 }
            } ?: ""
        }

        val date = lines.firstNotNullOfOrNull { parseDateIn(it) }
        val accountHint = when {
            "touch 'n go" in lower || "touch n go" in lower || "tng" in lower -> "Touch 'n Go"
            "grabpay" in lower -> "GrabPay"
            "shopeepay" in lower -> "ShopeePay"
            "boost" in lower -> "Boost"
            "paylah" in lower -> "PayLah!"
            "paynow" in lower -> "PayNow"
            else -> ""
        }
        return ScanResult(best?.v, currency, isIn, tag, note.take(40), date, accountHint, photo, all)
    }

    private fun codeOf(c: String): String? = when (c) {
        "RM", "MYR" -> "MYR"
        "S$", "SGD" -> "SGD"
        "US$", "USD" -> "USD"
        else -> null
    }

    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    fun parseDateIn(s: String): LocalDate? {
        val t = s.lowercase(Locale.ROOT)
        try {
            Regex("""(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})""").find(t)?.let {
                return LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
            }
            Regex("""(\d{1,2})[-/.](\d{1,2})[-/.](20\d{2})""").find(t)?.let {
                return LocalDate.of(it.groupValues[3].toInt(), it.groupValues[2].toInt(), it.groupValues[1].toInt())
            }
            Regex("""(\d{1,2})\s+([a-z]{3})[a-z]*,?\s+(20\d{2})""").find(t)?.let {
                val m = MONTHS[it.groupValues[2]] ?: return@let
                return LocalDate.of(it.groupValues[3].toInt(), m, it.groupValues[1].toInt())
            }
            Regex("""([a-z]{3})[a-z]*\s+(\d{1,2}),?\s+(20\d{2})""").find(t)?.let {
                val m = MONTHS[it.groupValues[1]] ?: return@let
                return LocalDate.of(it.groupValues[3].toInt(), m, it.groupValues[2].toInt())
            }
            Regex("""(20\d{2})年(\d{1,2})月(\d{1,2})日""").find(t)?.let {
                return LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
            }
            Regex("""(\d{1,2})月(\d{1,2})日""").find(t)?.let {
                val now = LocalDate.now()
                var d = LocalDate.of(now.year, it.groupValues[1].toInt(), it.groupValues[2].toInt())
                if (d.isAfter(now.plusDays(1))) d = d.minusYears(1)
                return d
            }
        } catch (e: Exception) {
            return null
        }
        return null
    }

    @Suppress("unused")
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE
}
