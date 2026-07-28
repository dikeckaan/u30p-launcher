package com.kaandikec.u30plauncher.core

/**
 * Kalici trafik sayaclarinin durumu.
 *
 * `lastTotal` = arayuzun en son okunan kumulatif byte degeri; deltayi bundan
 * cikariyoruz.
 */
class UsageState {
    var dayKey: Int = -1
    var monthKey: Int = -1
    var dayBytes: Long = 0
    var monthBytes: Long = 0
    var lastTotal: Long = -1
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
     */
    fun update(s: UsageState, total: Long, dayKey: Int, monthKey: Int) {
        if (s.lastTotal < 0) {
            s.lastTotal = total
            s.dayKey = dayKey
            s.monthKey = monthKey
            return
        }

        val delta = if (total < s.lastTotal) total else total - s.lastTotal
        s.lastTotal = total

        if (monthKey != s.monthKey) {
            s.monthKey = monthKey
            s.monthBytes = 0
            // yeni ay her zaman yeni gundur
            s.dayKey = dayKey
            s.dayBytes = 0
        } else if (dayKey != s.dayKey) {
            s.dayKey = dayKey
            s.dayBytes = 0
        }

        s.dayBytes += delta
        s.monthBytes += delta
    }
}
