package com.kaandikec.u30plauncher.core

import java.util.Calendar

/**
 * Faturalama doneminin baslangicini bulur.
 *
 * Once "bu ay" takvim ayi demekti; oysa operator donemi ayin 1'inde
 * baslamayabiliyor ve cihazin kendi Ayarlar ekrani da fatura donemini
 * kullaniyor. Ikisi ayrisinca launcher ile Ayarlar farkli rakam gosteriyordu.
 */
object CyclePeriod {
    /**
     * `cal` SIMDIYE damgalanmis gelmeli; cagri sonunda donem baslangicina
     * ayarlanmis olur (gun basi, saat 00:00).
     *
     * Ayin gunu donem gununden kucukse donem bir onceki ayda basladi demektir.
     */
    fun applyStart(cal: Calendar, cycleDay: Int) {
        val today = cal.get(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (today < cycleDay) {
            // Once ayin 1'ine cek: 31'inde bir ay geri gitmek Subat'ta
            // gunu sessizce kaydirirdi.
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.add(Calendar.MONTH, -1)
        }
        cal.set(Calendar.DAY_OF_MONTH, cycleDay)
    }

    /** Donemi tanimlayan kalici anahtar; devir tespitinde kullanilir. */
    fun key(cal: Calendar): Int = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
}
