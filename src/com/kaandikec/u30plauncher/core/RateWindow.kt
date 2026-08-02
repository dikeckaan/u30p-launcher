package com.kaandikec.u30plauncher.core

/**
 * Kayan pencere hiz ortalamasi.
 *
 * Modem arayuzunun sayaclari SUREKLI DEGIL, topaklar halinde guncelleniyor:
 * cihazda 1 Hz'de alinan 12 ornegin 5'i tam sifir cikti, arada 13 KB'lik
 * sicramalar vardi. Ardisik iki okumanin farkini dogrudan gostermek bu yuzden
 * indirme sirasinda ara ara 0 yaziyordu.
 *
 * Cozum: son orneklerin toplamini toplam sureye bolmek. Pencere SURE bazli,
 * ornek sayisi bazli degil — boylece yenileme araligi 0.5 sn de olsa 5 sn de
 * olsa ayni yumusaklikta calisir.
 *
 * Sabit dizilerle calisir; sicak yolda allocation yapmaz.
 */
class RateWindow(
    private val capacity: Int = 8,
    private val minWindowMs: Long = 2500L
) {
    private val bytes = LongArray(capacity)
    private val millis = LongArray(capacity)

    /** Bir sonraki yazma konumu. */
    private var head = 0
    private var size = 0

    fun add(deltaBytes: Long, deltaMs: Long) {
        if (deltaMs <= 0L) return
        bytes[head] = if (deltaBytes > 0L) deltaBytes else 0L
        millis[head] = deltaMs
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    /**
     * En yeni ornekten geriye dogru, pencere `minWindowMs`'i doldurana kadar
     * toplar. Yeterli ornek yoksa eldekilerin tamamini kullanir.
     */
    fun bytesPerSec(): Long {
        var b = 0L
        var m = 0L
        var n = 0
        var i = head - 1
        while (n < size) {
            if (i < 0) i += capacity
            b += bytes[i]
            m += millis[i]
            n++
            if (m >= minWindowMs) break
            i--
        }
        return if (m <= 0L) 0L else b * 1000L / m
    }

    fun reset() {
        head = 0
        size = 0
    }
}
