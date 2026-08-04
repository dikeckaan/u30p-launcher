package com.kaandikec.u30plauncher.core

/** Cihazin kendi veri politikasi: donem gunu ve uyari/limit esikleri. */
class NetPolicy {
    /** Ayin gunu (1..28) ya da UNKNOWN. */
    var cycleDay: Int = Snapshot.UNKNOWN

    /** Bayt; -1 = tanimsiz. */
    var warningBytes: Long = -1
    var limitBytes: Long = -1

    val hasCycleDay: Boolean get() = cycleDay in 1..CyclePeriod.MAX_DAY
}

/**
 * `dumpsys netpolicy` ciktisindan mobil veri politikasini okur.
 *
 * Neden: kullaniciya donem gununu elle sordurmak yerine cihazin bildiginden
 * baslamak. Ayni deger Ayarlar > Veri kullanimi ekranini da suruyor, yani
 * launcher ile Ayarlar ayni pencereye bakar.
 *
 * Beklenen bicim (tek satir):
 * `NetworkPolicy{template=... matchRule=CARRIER, ... cycleRule=RecurrenceRule{
 *  start=2025-01-12T00:00+03:00[Europe/Istanbul] end=null period=P1M}
 *  warningBytes=2147483648 limitBytes=-1 ...}`
 */
object NetPolicyParser {
    fun parse(dump: String, out: NetPolicy): Boolean {
        out.cycleDay = Snapshot.UNKNOWN
        out.warningBytes = -1
        out.limitBytes = -1

        // Birden fazla politika olabilir (WiFi, tasiyici). Mobil olani sec:
        // yalnizca tasiyici/mobil kurallarda abone kimligi bulunur.
        val line = pickMobileLine(dump) ?: return false

        out.cycleDay = dayOfStart(line)
        out.warningBytes = longAfter(line, "warningBytes=")
        out.limitBytes = longAfter(line, "limitBytes=")
        return out.hasCycleDay || out.warningBytes > 0 || out.limitBytes > 0
    }

    private fun pickMobileLine(dump: String): String? {
        var best: String? = null
        for (raw in dump.split('\n')) {
            if (!raw.contains("NetworkPolicy{")) continue
            if (!raw.contains("matchSubscriberIds=[") ) continue
            // Abone kimligi bos olan sablonlar gercek bir aboneligi temsil
            // etmiyor; onlari atla.
            if (raw.contains("matchSubscriberIds=[]")) continue
            best = raw
            if (raw.contains("matchRule=CARRIER") || raw.contains("matchRule=MOBILE")) break
        }
        return best
    }

    /** `start=2025-01-12T...` icinden gun. */
    private fun dayOfStart(line: String): Int {
        val key = "start="
        val at = line.indexOf(key)
        if (at < 0) return Snapshot.UNKNOWN
        // yyyy-MM-dd bicimi: 4+1+2+1+2
        val s = at + key.length
        if (s + 10 > line.length) return Snapshot.UNKNOWN
        if (line[s + 4] != '-' || line[s + 7] != '-') return Snapshot.UNKNOWN
        val d = twoDigits(line, s + 8)
        return if (d in 1..CyclePeriod.MAX_DAY) d else Snapshot.UNKNOWN
    }

    private fun twoDigits(s: String, at: Int): Int {
        val a = s[at]
        val b = s[at + 1]
        if (a < '0' || a > '9' || b < '0' || b > '9') return Snapshot.UNKNOWN
        return (a - '0') * 10 + (b - '0')
    }

    private fun longAfter(line: String, key: String): Long {
        val at = line.indexOf(key)
        if (at < 0) return -1
        var i = at + key.length
        var neg = false
        if (i < line.length && line[i] == '-') {
            neg = true
            i++
        }
        var v = 0L
        var digits = 0
        while (i < line.length && line[i] in '0'..'9') {
            v = v * 10 + (line[i] - '0')
            i++
            digits++
        }
        if (digits == 0) return -1
        return if (neg) -v else v
    }
}
