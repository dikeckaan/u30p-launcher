package com.kaandikec.u30plauncher.store

import android.content.Context

/**
 * Uygulama acilis sayaclari.
 *
 * Bir MiFi'de kurulu uygulamalarin cogu (takvim, rehber, FM radyo) hic
 * acilmaz; alfabetik siralama en cok kullanilani en dibe atabiliyor. Kendi
 * sayacimizi tutmak izin gerektirmez ve ilk birkac kullanimdan sonra listeyi
 * kullanisli hale getirir.
 */
class AppUsage(ctx: Context) {
    private val sp = ctx.getSharedPreferences("u30p_appusage", Context.MODE_PRIVATE)

    fun count(component: String): Int = sp.getInt(component, 0)

    fun record(component: String) {
        sp.edit().putInt(component, count(component) + 1).apply()
    }
}
