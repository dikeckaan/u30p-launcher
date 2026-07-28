package com.kaandikec.u30plauncher.core

/** Tek bir arayuzun sayaclari. Yeniden kullanilir — her okumada ayrilmaz. */
class IfCounters {
    var rx: Long = 0
    var tx: Long = 0
}

/**
 * `/proc/net/dev` icerigini String uretmeden ayristirir.
 *
 * Satir duzeni (iki nokta sonrasi): rx alanlari 0..7, tx alanlari 8..15.
 * Yani rx bytes = alan 0, tx bytes = alan 8.
 */
object ProcNetParser {
    private const val SPACE = ' '.code.toByte()
    private const val COLON = ':'.code.toByte()
    private const val NL = '\n'.code.toByte()
    private const val ZERO = '0'.code.toByte()
    private const val NINE = '9'.code.toByte()

    fun parse(buf: ByteArray, len: Int, iface: String, out: IfCounters): Boolean {
        var i = 0
        while (i < len) {
            val lineEnd = lineEndFrom(buf, i, len)
            if (matchIface(buf, i, lineEnd, iface)) {
                return readCounters(buf, i, lineEnd, out)
            }
            i = lineEnd + 1
        }
        return false
    }

    private fun lineEndFrom(buf: ByteArray, start: Int, len: Int): Int {
        var e = start
        while (e < len && buf[e] != NL) e++
        return e
    }

    /** Satir basindaki bosluklari atlayip `iface:` tam eslesmesi arar. */
    private fun matchIface(buf: ByteArray, start: Int, end: Int, iface: String): Boolean {
        var p = start
        while (p < end && buf[p] == SPACE) p++
        for (k in iface.indices) {
            if (p + k >= end) return false
            if (buf[p + k] != iface[k].code.toByte()) return false
        }
        val after = p + iface.length
        return after < end && buf[after] == COLON
    }

    private fun readCounters(buf: ByteArray, start: Int, end: Int, out: IfCounters): Boolean {
        var p = start
        while (p < end && buf[p] != COLON) p++
        if (p >= end) return false
        p++

        var field = 0
        var rx = -1L
        var tx = -1L
        while (p < end) {
            while (p < end && buf[p] == SPACE) p++
            if (p >= end) break
            var v = 0L
            var digits = 0
            while (p < end) {
                val b = buf[p]
                if (b < ZERO || b > NINE) break
                v = v * 10 + (b - ZERO)
                digits++
                p++
            }
            if (digits == 0) return false  // sayi olmayan belirtec: bicim beklenmedik
            if (field == 0) rx = v
            if (field == 8) {
                tx = v
                break
            }
            field++
        }
        if (rx < 0 || tx < 0) return false
        out.rx = rx
        out.tx = tx
        return true
    }
}
