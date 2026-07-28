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

    fun speedUnit(bytesPerSec: Long): String = BIT_UNITS[speedUnitIndex(bytesPerSec)]

    fun speedUnitIndex(bytesPerSec: Long): Int {
        val bits = if (bytesPerSec > 0) bytesPerSec * 8 else 0L
        return unitIndexOf(bits, 1000.0, BIT_UNITS.size)
    }

    fun unitName(index: Int): String = BIT_UNITS[index.coerceIn(0, BIT_UNITS.size - 1)]

    /**
     * Degeri VERILEN birimde yazar.
     *
     * Indirme ve yukleme tek bir birim etiketi paylastigi icin ikisinin de ayni
     * olcekte olmasi sart; aksi halde "12.4" ve "4.32" yan yana gorunur ama tek
     * etiket ikisinden yalnizca birini dogru tanimlar.
     */
    fun appendSpeedValueAt(sb: StringBuilder, bytesPerSec: Long, unitIndex: Int) {
        val bits = if (bytesPerSec > 0) bytesPerSec * 8 else 0L
        val idx = unitIndex.coerceIn(0, BIT_UNITS.size - 1)
        var div = 1.0
        repeat(idx) { div *= 1000.0 }
        val v = bits / div
        val d = decimalsFor(v, idx)
        var pow = 1L
        repeat(d) { pow *= 10 }
        appendDec(sb, Math.round(v * pow), d)
    }

    // --- yalnizca test icin ---
    fun bytes(b: Long): String = StringBuilder().also { appendBytes(it, b) }.toString()

    fun speedValueOf(bytesPerSec: Long): String =
        StringBuilder().also { appendSpeedValue(it, bytesPerSec) }.toString()

    fun speedValueAtOf(bytesPerSec: Long, unitIndex: Int): String =
        StringBuilder().also { appendSpeedValueAt(it, bytesPerSec, unitIndex) }.toString()

    fun speed(bytesPerSec: Long): String = speedValueOf(bytesPerSec) + " " + speedUnit(bytesPerSec)
}
