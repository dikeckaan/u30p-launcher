package com.kaandikec.u30plauncher.core

/**
 * Kalan pil suresi tahmini.
 *
 * `BATTERY_PROPERTY_CHARGE_COUNTER` kalan sarji uAh cinsinden, `CURRENT_NOW`
 * anlik akimi uA cinsinden verir. Bu cihazda akim sarj ederken POZITIF,
 * desarjda NEGATIF (dogrulandi: sarjdayken +573000 uA).
 *
 * Sure = kalan sarj / akim. Akim dalgalandigi icin cagiran tarafin ortalanmis
 * bir deger vermesi beklenir; burada saf aritmetik yapilir.
 */
object BatteryEstimate {
    /** Cok kucuk akimlarda tahmin anlamsiz buyur; bu esigin altini elemeyiz. */
    private const val MIN_DRAW_UA = 15_000L

    /** Uzerinde gosterilmesi anlamsiz olan ust sinir (48 saat). */
    private const val MAX_MINUTES = 48 * 60

    /**
     * GOZLENEN bosalma hizindan kalan sureyi hesaplar.
     *
     * Tercih edilen yol budur: hiz, sarj sayacinin zaman icindeki gercek
     * dususunden gelir; o pencerede yasanan tum yuk dahildir.
     *
     * @param chargeUah kalan sarj (uAh)
     * @param drainUahPerHour gozlenen bosalma hizi; bilinmiyorsa UNKNOWN
     */
    fun minutesLeftFromDrain(chargeUah: Int, drainUahPerHour: Int): Int {
        if (chargeUah <= 0) return Snapshot.UNKNOWN
        if (drainUahPerHour == Snapshot.UNKNOWN || drainUahPerHour <= 0) return Snapshot.UNKNOWN
        val minutes = chargeUah.toLong() * 60L / drainUahPerHour.toLong()
        if (minutes <= 0L) return Snapshot.UNKNOWN
        return if (minutes > MAX_MINUTES) MAX_MINUTES else minutes.toInt()
    }

    /**
     * Anlik akimdan tahmin. Gozlem birikene kadarki YEDEK yol; o andaki
     * cekimi gelecege uzattigi icin daha oynak.
     *
     * @param chargeUah kalan sarj (uAh), bilinmiyorsa <= 0
     * @param currentUa anlik akim (uA); negatif = desarj
     * @return kalan dakika, hesaplanamiyorsa [Snapshot.UNKNOWN]
     */
    fun minutesLeft(chargeUah: Int, currentUa: Int): Int {
        if (chargeUah <= 0) return Snapshot.UNKNOWN
        // Pozitif akim sarj demek; "kalan sure" kavrami uygulanmaz
        if (currentUa >= 0) return Snapshot.UNKNOWN
        val draw = -currentUa.toLong()
        if (draw < MIN_DRAW_UA) return Snapshot.UNKNOWN
        val minutes = chargeUah.toLong() * 60L / draw
        if (minutes <= 0L) return Snapshot.UNKNOWN
        return if (minutes > MAX_MINUTES) MAX_MINUTES else minutes.toInt()
    }
}
