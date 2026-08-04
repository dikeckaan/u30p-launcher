package com.kaandikec.u30plauncher.core

/**
 * Son N orneklik sabit boyutlu halka tampon.
 *
 * Sinyal ve hiz egrisini cizmek icin kullanilir; anlik deger kapsama/hiz
 * sorununu teshise yetmiyor, son birkac dakikanin sekli lazim. Tek bir
 * LongArray uzerinde calisir, hicbir cizim yolunda allocation uretmez —
 * ne iterator ne kutu (boxing). Bu yuzden tek is parcacigindan (UI thread)
 * kullanilmalidir.
 *
 * Deger tipi Long: hiz (bayt/sn) zaten Long, RSRP gibi Int'ler de sorunsuz
 * genisler. Bilinmeyen ornek [UNKNOWN] ile isaretlenir; min/max onu atlar ve
 * grafik o araligi bosluk birakabilir.
 */
class History(val capacity: Int = 120) {
    companion object {
        /**
         * Bosluk isareti. Snapshot.UNKNOWN (Int.MIN_VALUE) ile karismasin diye
         * ayri bir Long deger secildi; cagiran Int bir alani eklerken
         * Snapshot.UNKNOWN'i bu sabite kendisi cevirir.
         */
        const val UNKNOWN = Long.MIN_VALUE
    }

    private val buf = LongArray(capacity)

    /** Siradaki yazma konumu; tampon dolunca ayni zamanda en eski ornek. */
    private var head = 0
    private var count = 0

    val size: Int get() = count

    fun add(v: Long) {
        buf[head] = v
        head = (head + 1) % capacity
        if (count < capacity) count++
    }

    /** i = 0 en eski, i = size - 1 en yeni. Aralik disi cagri UNKNOWN doner. */
    fun get(i: Int): Long {
        if (i < 0 || i >= count) return UNKNOWN
        val start = if (count < capacity) 0 else head
        return buf[(start + i) % capacity]
    }

    /** En yeni ornek; hic yoksa UNKNOWN. */
    fun newest(): Long = if (count == 0) UNKNOWN else get(count - 1)

    /** Bilinen orneklerin en kucugu; hic bilinen yoksa UNKNOWN. */
    fun min(): Long {
        var m = UNKNOWN
        for (i in 0 until count) {
            val v = get(i)
            if (v == UNKNOWN) continue
            if (m == UNKNOWN || v < m) m = v
        }
        return m
    }

    /** Bilinen orneklerin en buyugu; hic bilinen yoksa UNKNOWN. */
    fun max(): Long {
        var m = UNKNOWN
        for (i in 0 until count) {
            val v = get(i)
            if (v == UNKNOWN) continue
            if (m == UNKNOWN || v > m) m = v
        }
        return m
    }

    fun clear() {
        head = 0
        count = 0
    }
}
