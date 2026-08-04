package com.kaandikec.u30plauncher.core

/**
 * Sicak yolda allocation yapmadan sayi formatlar.
 *
 * `appendXxx` fonksiyonlari cagiranin verdigi StringBuilder'a yazar; ara String
 * uretmez. String donduren varyantlar yalnizca testler icindir.
 */
object Fmt {
    private val BYTE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB")
    private val BIT_UNITS = arrayOf("bps", "Kbps", "Mbps", "Gbps", "Tbps")

    /** Ondalik basamak sayisi buyudukce azalir; metin genisligi dar kalir. */
    private fun decimalsFor(v: Double, unitIndex: Int): Int =
        if (unitIndex == 0) 0 else if (v < 10.0) 2 else if (v < 100.0) 1 else 0

    private fun appendDec(sb: StringBuilder, scaled: Long, decimals: Int) {
        when (decimals) {
            0 -> sb.append(scaled)
            1 -> {
                sb.append(scaled / 10)
                sb.append('.')
                sb.append(scaled % 10)
            }
            else -> {
                sb.append(scaled / 100)
                sb.append('.')
                val f = scaled % 100
                if (f < 10) sb.append('0')
                sb.append(f)
            }
        }
    }

    private fun unitIndexOf(value: Long, base: Double, unitCount: Int): Int {
        var v = if (value > 0) value.toDouble() else 0.0
        var i = 0
        while (v >= base && i < unitCount - 1) {
            v /= base
            i++
        }
        return i
    }

    /** Degeri uygun birime indirger, sayiyi sb'ye yazar, birim indeksini dondurur. */
    private fun appendScaledValue(sb: StringBuilder, value: Long, base: Double, unitCount: Int): Int {
        var v = if (value > 0) value.toDouble() else 0.0
        var i = 0
        while (v >= base && i < unitCount - 1) {
            v /= base
            i++
        }
        val d = decimalsFor(v, i)
        var pow = 1L
        repeat(d) { pow *= 10 }
        appendDec(sb, Math.round(v * pow), d)
        return i
    }

    /**
     * "9.2/10 GB" — kullanim ve limit tek hucrede.
     *
     * Iki deger AYNI birimde yazilir (limitin birimi esas alinir); farkli
     * birimlerde yazmak "980 MB / 10 GB" gibi karsilastirmasi zor bir metin
     * uretiyordu. 240 px'de yer de yok.
     */
    fun appendBytesWithLimit(sb: StringBuilder, used: Long, limit: Long) {
        if (limit <= 0) {
            appendBytes(sb, used)
            return
        }
        val i = unitIndexOf(limit, 1024.0, BYTE_UNITS.size)
        var div = 1.0
        repeat(i) { div *= 1024.0 }
        val u = used / div
        // appendDec olceklenmis tam sayi bekler; basamak sayisina gore
        // olcegi de degistirmek gerek.
        if (u < 100.0) appendDec(sb, Math.round(u * 10), 1) else appendDec(sb, Math.round(u), 0)
        sb.append('/')
        appendDec(sb, Math.round(limit / div), 0)
        sb.append(' ')
        sb.append(BYTE_UNITS[i])
    }

    fun appendBytes(sb: StringBuilder, bytes: Long) {
        val i = appendScaledValue(sb, bytes, 1024.0, BYTE_UNITS.size)
        sb.append(' ')
        sb.append(BYTE_UNITS[i])
    }

    /** bytes/sn -> bit/sn cevirip yalnizca sayiyi yazar; birim ayri cizilir. */
    fun appendSpeedValue(sb: StringBuilder, bytesPerSec: Long) {
        val bits = if (bytesPerSec > 0) bytesPerSec * 8 else 0L
        appendScaledValue(sb, bits, 1000.0, BIT_UNITS.size)
    }

    /** `appendSpeedValue` ile ayni olcegi secer; deger ve birim tutarli kalir. */
    fun speedUnit(bytesPerSec: Long): String {
        val bits = if (bytesPerSec > 0) bytesPerSec * 8 else 0L
        return BIT_UNITS[unitIndexOf(bits, 1000.0, BIT_UNITS.size)]
    }

    /**
     * Kalan sureyi kisa yazar: "45 dk", "4.2 sa", "12 sa".
     *
     * 240x240'ta yer dar oldugu icin tek birim kullanilir; bir saatin altinda
     * dakika, uzerinde ondalikli saat.
     */
    fun appendDuration(sb: StringBuilder, minutes: Int, hourSuffix: String, minuteSuffix: String) {
        if (minutes < 60) {
            sb.append(minutes)
            sb.append(' ')
            sb.append(minuteSuffix)
            return
        }
        val h = minutes / 60
        if (h >= 10) {
            sb.append(h)
            sb.append(' ')
            sb.append(hourSuffix)
            return
        }
        sb.append(h)
        sb.append('.')
        sb.append((minutes % 60) * 10 / 60)
        sb.append(' ')
        sb.append(hourSuffix)
    }

    // --- yalnizca test icin ---
    fun bytes(b: Long): String = StringBuilder().also { appendBytes(it, b) }.toString()

    fun speedValueOf(bytesPerSec: Long): String =
        StringBuilder().also { appendSpeedValue(it, bytesPerSec) }.toString()

    fun speed(bytesPerSec: Long): String = speedValueOf(bytesPerSec) + " " + speedUnit(bytesPerSec)

    fun duration(minutes: Int, hourSuffix: String = "sa", minuteSuffix: String = "dk"): String =
        StringBuilder().also { appendDuration(it, minutes, hourSuffix, minuteSuffix) }.toString()
}
