package com.kaandikec.u30plauncher.core

/**
 * Kalici trafik sayaclarinin durumu.
 *
 * `lastTotal` = arayuzun en son okunan kumulatif byte degeri; deltayi bundan
 * cikariyoruz. `lastAtMs` = o okumanin DUVAR SAATI zamani; deltanin hangi gune
 * yazilacagina karar verirken gerekiyor.
 */
class UsageState {
    var dayKey: Int = -1
    var monthKey: Int = -1
    var dayBytes: Long = 0
    var monthBytes: Long = 0
    var lastTotal: Long = -1
    var lastAtMs: Long = 0
}

object UsageCalc {
    /**
     * Yeni kumulatif toplami isler.
     *
     * - Ilk cagride yalnizca taban kurulur; gecmis trafik bugune yazilmaz.
     * - `total < lastTotal` ise sayac sifirlanmistir (reboot veya arayuz yeniden
     *   kurulmus); delta ham `total` olur.
     * - Devir kontrolu delta EKLENMEDEN once yapilir; aksi halde yeni gunun ilk
     *   deltasi eski gune yazilirdi.
     *
     * Iki okuma arasi gun/ay sinirini asiyorsa delta zamana orantili bolunur ve
     * yalnizca sinirdan SONRAKI pay yeni doneme yazilir. Bu cihaz bir router:
     * launcher uykudayken de trafik akiyor ve gece boyunca biriken fark sabah
     * tek okumada goruluyor. Tamamini o gune yazmak "bugun 2.7 GB" gibi gercek
     * disi degerler uretiyordu.
     *
     * `nowMs` 0 verilirse bolme yapilmaz (zaman bilgisi yok); davranis eskisiyle
     * ayni kalir.
     */
    fun update(
        s: UsageState,
        total: Long,
        dayKey: Int,
        monthKey: Int,
        nowMs: Long = 0,
        dayStartMs: Long = 0,
        monthStartMs: Long = 0
    ) {
        if (s.lastTotal < 0) {
            s.lastTotal = total
            s.dayKey = dayKey
            s.monthKey = monthKey
            s.lastAtMs = nowMs
            return
        }

        val delta = if (total < s.lastTotal) total else total - s.lastTotal
        s.lastTotal = total
        val prevAt = s.lastAtMs
        s.lastAtMs = nowMs

        val monthRolled = monthKey != s.monthKey
        val dayRolled = dayKey != s.dayKey

        if (monthRolled) {
            s.monthKey = monthKey
            s.monthBytes = 0
            // yeni ay her zaman yeni gundur
            s.dayKey = dayKey
            s.dayBytes = 0
        } else if (dayRolled) {
            s.dayKey = dayKey
            s.dayBytes = 0
        }

        s.dayBytes += portionAfter(delta, prevAt, nowMs, dayStartMs, dayRolled || monthRolled)
        s.monthBytes += portionAfter(delta, prevAt, nowMs, monthStartMs, monthRolled)
    }

    /**
     * Deltanin `boundaryMs`'ten SONRAKI payi.
     *
     * Devir olmadiysa ya da zamanlar guvenilir degilse (ilk calisma, saat geri
     * gitmis) tamami dondurulur; eksik zaman bilgisi yuzunden trafik
     * kaybetmeyelim.
     */
    private fun portionAfter(
        delta: Long,
        prevAt: Long,
        nowMs: Long,
        boundaryMs: Long,
        rolled: Boolean
    ): Long {
        if (!rolled) return delta
        if (prevAt <= 0L || nowMs <= prevAt) return delta
        if (boundaryMs <= prevAt || boundaryMs >= nowMs) return delta
        return delta * (nowMs - boundaryMs) / (nowMs - prevAt)
    }
}
