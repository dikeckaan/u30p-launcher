package com.kaandikec.u30plauncher.core

/** Bir `/proc/[pid]/stat` satirindan cikarilan alanlar. */
class ProcPidStat {
    /** stat satirinin ILK alani. Eskiden dizin adindan geliyordu; dokum
     *  uzerinden calisirken tek kaynak bu. */
    var pid: Int = 0
    var comm: String = ""
    var ppid: Int = 0
    /** utime + stime (jiffies) */
    var cpuTicks: Long = 0
}

/**
 * `/proc/[pid]/stat` ayristirir: surec adi, ppid ve CPU tiklari.
 *
 * comm ikinci alandir ve parantez icindedir; parantez icinde bosluk ya da
 * baska parantez olabildigi icin (ornek: "(Binder:700_2)", "(foo (bar))")
 * ilk '(' ile SON ')' arasi comm sayilir — aradaki alanlar bosluk sayisiyla
 * belirlenemez. ')' sonrasi alanlar bosluk ayrikli: state(1) ppid(2) ...
 * utime(12) stime(13).
 */
object ProcPidStatParser {
    private const val SPACE = ' '.code.toByte()
    private const val NL = '\n'.code.toByte()
    private const val OPEN = '('.code.toByte()
    private const val CLOSE = ')'.code.toByte()
    private const val ZERO = '0'.code.toByte()
    private const val NINE = '9'.code.toByte()

    fun parse(buf: ByteArray, len: Int, out: ProcPidStat): Boolean {
        if (len <= 0) return false

        var open = -1
        var i = 0
        var pid = 0
        var pidDigits = 0
        while (i < len) {
            val b = buf[i]
            if (b == OPEN) { open = i; break }
            if (b >= ZERO && b <= NINE) {
                pid = pid * 10 + (b - ZERO)
                pidDigits++
            } else if (pidDigits > 0) {
                // Rakamlardan sonra rakam disi bir sey geldiyse ilk alan bitti
                break
            }
            i++
        }
        if (open < 0) {
            // '(' bulunamadan cikildiysa satiri yeniden tara
            while (i < len && buf[i] != OPEN) i++
            if (i >= len) return false
            open = i
        }
        if (pidDigits == 0) return false
        out.pid = pid

        var close = -1
        i = len - 1
        while (i > open) {
            if (buf[i] == CLOSE) { close = i; break }
            i--
        }
        if (close <= open) return false

        // comm burada String uretir; bu ayristirici yalnizca 5 sn'de bir
        // (SystemSource) cagrildigi ve cizim yolunda olmadigi icin kabul.
        out.comm = String(buf, open + 1, close - open - 1)

        var p = close + 1
        var field = 0
        var ppid = 0
        var utime = 0L
        var stime = 0L
        while (p < len && buf[p] != NL) {
            while (p < len && buf[p] == SPACE) p++
            if (p >= len || buf[p] == NL) break
            field++
            var v = 0L
            while (p < len && buf[p] != SPACE && buf[p] != NL) {
                val b = buf[p]
                // isaret vb. sayisal olmayan karakterleri yok say; ihtiyac
                // duydugumuz alanlar (ppid, utime, stime) daima pozitif
                if (b in ZERO..NINE) v = v * 10 + (b - ZERO)
                p++
            }
            when (field) {
                2 -> ppid = v.toInt()
                12 -> utime = v
                13 -> { stime = v; break }
            }
        }
        if (field < 13) return false
        out.ppid = ppid
        out.cpuTicks = utime + stime
        return true
    }
}
