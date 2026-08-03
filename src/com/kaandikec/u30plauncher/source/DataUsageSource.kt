package com.kaandikec.u30plauncher.source

import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock

/**
 * Veri kullanimini CIHAZIN KENDI sayacindan okur.
 *
 * Daha once `/proc/net/dev`'den elle biriktiriyorduk. O sayac arayuz her
 * yeniden kuruldugunda sifirlaniyor ve yalnizca launcher calisirken
 * ilerliyor; cihaz bir router oldugu icin uykudaki trafik kaciyordu.
 * Android'in `NetworkStatsManager`'i ise Ayarlar > Veri kullanimi ile ayni
 * kaynak: saatlik kovalar halinde diskte tutulur, reboot'tan etkilenmez ve
 * yonlendirilen istemci trafigini de icerir.
 *
 * Erisim `android:get_usage_stats` app-op'una bagli. Kullaniciya ayar
 * ekraninda gezdirmemek icin root varsa kendimize veriyoruz; verilmemisse
 * sorgu sessizce basarisiz olur ve cagiran eski sayaca duser.
 */
class DataUsageSource(private val ctx: Context) {
    companion object {
        /**
         * Sorgu araligi. Gunluk toplam saniyede bir tazelenmeyi hak etmiyor;
         * her sorgu bir binder cagrisi ve disk okumasi demek.
         */
        private const val INTERVAL_MS = 15_000L

        private const val APP_OP = "android:get_usage_stats"
    }

    /** -1 = okunamadi; cagiran kendi sayacina duser. */
    var todayBytes: Long = -1; private set
    var monthBytes: Long = -1; private set

    private var manager: NetworkStatsManager? = null
    private var lastAt = 0L
    private var opRequested = false

    /**
     * `dayStartMs` / `monthStartMs` cagirandan gelir; takvim hesabini tek
     * yerde tutalim diye burada tekrar yapilmiyor.
     */
    fun sample(nowMs: Long, dayStartMs: Long, monthStartMs: Long) {
        val now = SystemClock.elapsedRealtime()
        if (lastAt != 0L && now - lastAt < INTERVAL_MS) return
        lastAt = now

        val m = manager ?: try {
            ctx.getSystemService(NetworkStatsManager::class.java)
        } catch (_: Throwable) {
            null
        } ?: return
        manager = m

        val today = query(m, dayStartMs, nowMs)
        val month = query(m, monthStartMs, nowMs)

        if (today < 0 && !opRequested) {
            // Ilk basarisizlikta bir kez izin istemeyi dene; root yoksa
            // veya reddedilirse bir daha ugrasma.
            opRequested = true
            grantAppOp()
            return
        }
        todayBytes = today
        monthBytes = month
    }

    /**
     * `querySummaryForDevice` cihaz genelini dondurur; UID bazli degil, bu
     * yuzden yonlendirilen trafik de sayilir. VPN arayuzu (tun0) mobil
     * sablonun disinda kaldigi icin cift sayim olmaz.
     *
     * `subscriberId` null: tum abonelikler. Deger vermek READ_PHONE_STATE'in
     * otesinde ayricalik istiyor ve tek SIM'li bir cihazda kazanci yok.
     */
    @Suppress("DEPRECATION")
    private fun query(m: NetworkStatsManager, startMs: Long, endMs: Long): Long = try {
        val b = m.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, startMs, endMs)
        if (b == null) -1L else b.rxBytes + b.txBytes
    } catch (_: Throwable) {
        -1L
    }

    /** App-op'u root ile kendimize ver; yoksa sessizce vazgec. */
    private fun grantAppOp() {
        RootShell.async("appops set ${ctx.packageName} $APP_OP allow") { }
    }
}
