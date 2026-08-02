package com.kaandikec.u30plauncher.core

/**
 * Pilin GERCEKTE ne hizla bosaldigini olcer.
 *
 * Anlik akimdan (`CURRENT_NOW`) yapilan tahmin, o andaki cekimi gelecege
 * uzatir; bir MiFi'de yuk surekli degistigi icin sonuc oynak olur. Burada
 * bunun yerine yakit gostergesinin kalan sarj degeri (`CHARGE_COUNTER`)
 * zaman icinde izlenir ve gozlenen dususten hiz cikarilir. Bu olcum
 * kullanimin kendisini icerir — modem, WiFi istemcileri, ekran, hepsi.
 *
 * Ornekler seyrek (varsayilan 60 sn) alinir; 48 ornek ~48 dakikayi kapsar.
 * Ekran kapaliyken yoklama durdugu icin araya uzun bosluklar girebilir —
 * sorun degil: sayac kumulatif oldugundan bosluk boyunca olan dusus de
 * olcume dahildir. Aslinda en temsili veri odur.
 */
class BatteryHistory(
    private val capacity: Int = 48,
    private val minSpacingMs: Long = 60_000L,
    private val minSpanMs: Long = 600_000L,
    /** Guvenilir bir hiz icin gereken en kucuk dusus (uAh). */
    private val minDropUah: Int = 20_000
) {
    private val charge = IntArray(capacity)
    private val at = LongArray(capacity)
    private var head = 0
    private var size = 0

    val sampleCount: Int get() = size

    /**
     * Ornek ekler. Son ornekten `minSpacingMs` gecmediyse yok sayar.
     *
     * Sarj artmissa (fise takilmis) gecmis anlamsizdir ve temizlenir.
     */
    fun add(chargeUah: Int, elapsedMs: Long) {
        if (chargeUah <= 0) return
        if (size > 0) {
            val lastIdx = (head - 1 + capacity) % capacity
            // Saat geri gitmis (yeniden baslatma): gecmis gecersiz
            if (elapsedMs < at[lastIdx]) {
                reset()
            } else {
                if (elapsedMs - at[lastIdx] < minSpacingMs) return
                // Sarj artti: cihaz sarj ediliyor, bosalma gecmisi anlamsiz
                if (chargeUah > charge[lastIdx]) {
                    reset()
                }
            }
        }
        charge[head] = chargeUah
        at[head] = elapsedMs
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    /** Gozlenen bosalma hizi (uAh/saat); guvenilir degilse [Snapshot.UNKNOWN]. */
    fun drainUahPerHour(): Int {
        if (size < 2) return Snapshot.UNKNOWN
        val newestIdx = (head - 1 + capacity) % capacity
        val oldestIdx = if (size < capacity) 0 else head
        val span = at[newestIdx] - at[oldestIdx]
        if (span < minSpanMs) return Snapshot.UNKNOWN
        val drop = charge[oldestIdx] - charge[newestIdx]
        if (drop < minDropUah) return Snapshot.UNKNOWN
        return (drop.toLong() * 3_600_000L / span).toInt()
    }

    fun reset() {
        head = 0
        size = 0
    }

    /** "sarj:zaman;sarj:zaman;..." — SharedPreferences'a sigacak sade bicim. */
    fun serialize(): String {
        if (size == 0) return ""
        val sb = StringBuilder(size * 20)
        val start = if (size < capacity) 0 else head
        for (n in 0 until size) {
            val i = (start + n) % capacity
            if (n > 0) sb.append(';')
            sb.append(charge[i])
            sb.append(':')
            sb.append(at[i])
        }
        return sb.toString()
    }

    /**
     * Kayitli gecmisi yukler.
     *
     * `nowElapsedMs` su anki `elapsedRealtime`; kayitli zamanlar bundan
     * buyukse cihaz yeniden baslamistir ve gecmis atilir (elapsedRealtime
     * her acilista sifirlanir).
     */
    fun load(data: String, nowElapsedMs: Long) {
        reset()
        if (data.isEmpty()) return
        for (part in data.split(';')) {
            val sep = part.indexOf(':')
            if (sep <= 0) continue
            val c = part.substring(0, sep).toIntOrNull() ?: continue
            val t = part.substring(sep + 1).toLongOrNull() ?: continue
            if (t > nowElapsedMs) {
                reset()
                return
            }
            charge[head] = c
            at[head] = t
            head = (head + 1) % capacity
            if (size < capacity) size++
        }
    }
}
