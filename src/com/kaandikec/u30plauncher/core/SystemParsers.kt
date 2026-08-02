package com.kaandikec.u30plauncher.core

/** `/proc/stat` toplam CPU sayaclari (jiffies). */
class CpuTimes {
    var total: Long = 0
    var idle: Long = 0
}

/**
 * `/proc/stat` ilk satirini ("cpu  ...") ayristirir.
 *
 * Alanlar: user nice system idle iowait irq softirq steal guest guest_nice.
 * Bosta gecen sure = idle + iowait; kullanim iki ornek arasindaki farktan
 * cikar, cunku sayaclar acilistan beri kumulatiftir.
 */
object ProcStatParser {
    private const val SPACE = ' '.code.toByte()
    private const val NL = '\n'.code.toByte()
    private const val ZERO = '0'.code.toByte()
    private const val NINE = '9'.code.toByte()

    fun parse(buf: ByteArray, len: Int, out: CpuTimes): Boolean {
        // Ilk satir "cpu" ile baslamali (cekirdek bazli "cpu0" degil)
        if (len < 4) return false
        if (buf[0] != 'c'.code.toByte() || buf[1] != 'p'.code.toByte() ||
            buf[2] != 'u'.code.toByte() || buf[3] != SPACE
        ) return false

        var p = 3
        var field = 0
        var total = 0L
        var idle = 0L
        while (p < len && buf[p] != NL) {
            while (p < len && buf[p] == SPACE) p++
            if (p >= len || buf[p] == NL) break
            var v = 0L
            var digits = 0
            while (p < len) {
                val b = buf[p]
                if (b < ZERO || b > NINE) break
                v = v * 10 + (b - ZERO)
                digits++
                p++
            }
            if (digits == 0) break
            total += v
            // idle (3) ve iowait (4) bosta gecen suredir
            if (field == 3 || field == 4) idle += v
            field++
        }
        if (field < 4) return false
        out.total = total
        out.idle = idle
        return true
    }

    /**
     * Iki ornek arasindaki CPU kullanimi (yuzde 0..100).
     *
     * Sayaclar sifirlanmis veya fark yoksa [Snapshot.UNKNOWN].
     */
    fun usagePercent(prev: CpuTimes, now: CpuTimes): Int {
        val dTotal = now.total - prev.total
        val dIdle = now.idle - prev.idle
        if (dTotal <= 0L || dIdle < 0L) return Snapshot.UNKNOWN
        val busy = dTotal - dIdle
        val pct = busy * 100L / dTotal
        return when {
            pct < 0L -> 0
            pct > 100L -> 100
            else -> pct.toInt()
        }
    }
}

/** `/proc/meminfo` degerleri (kB). */
class MemInfo {
    var totalKb: Long = 0
    var availableKb: Long = 0

    val usedKb: Long get() = if (totalKb > availableKb) totalKb - availableKb else 0
}

/**
 * `/proc/meminfo` icinden MemTotal ve MemAvailable okur.
 *
 * `MemAvailable` cekirdegin "yeni bir uygulama icin gercekten ayirabilecegim"
 * tahminidir; MemFree'den daha dogru olcudur cunku geri alinabilir onbellegi
 * de sayar.
 */
object MemInfoParser {
    fun parse(buf: ByteArray, len: Int, out: MemInfo): Boolean {
        out.totalKb = 0
        out.availableKb = 0
        var i = 0
        while (i < len) {
            var e = i
            while (e < len && buf[e] != '\n'.code.toByte()) e++
            when {
                matches(buf, i, e, "MemTotal:") -> out.totalKb = firstNumber(buf, i, e)
                matches(buf, i, e, "MemAvailable:") -> out.availableKb = firstNumber(buf, i, e)
            }
            if (out.totalKb > 0 && out.availableKb > 0) break
            i = e + 1
        }
        return out.totalKb > 0
    }

    private fun matches(buf: ByteArray, start: Int, end: Int, key: String): Boolean {
        if (start + key.length > end) return false
        for (k in key.indices) if (buf[start + k] != key[k].code.toByte()) return false
        return true
    }

    private fun firstNumber(buf: ByteArray, start: Int, end: Int): Long {
        var p = start
        while (p < end && (buf[p] < '0'.code.toByte() || buf[p] > '9'.code.toByte())) p++
        var v = 0L
        var digits = 0
        while (p < end) {
            val b = buf[p]
            if (b < '0'.code.toByte() || b > '9'.code.toByte()) break
            v = v * 10 + (b - '0'.code.toByte())
            digits++
            p++
        }
        return if (digits == 0) 0L else v
    }
}
