package com.kaandikec.u30plauncher

import com.kaandikec.u30plauncher.source.RootShell

/**
 * Ayricalikli islemler. Hepsi tek root kabugundan gecer; uygulamanin
 * WRITE_SETTINGS veya MODIFY_PHONE_STATE gibi ozel izinlere ihtiyaci olmaz.
 *
 * Bu cihazda `svc data` ve `svc wifi` alt komutlari yok; veriyi kesmenin tek
 * temiz yolu ucak modu. WiFi AP'yi kapatmak bilincli olarak kapsam disi:
 * uzaktan erisim de o AP uzerinden geliyor.
 */
object Actions {
    val TIMEOUTS = intArrayOf(15_000, 60_000, 300_000, Int.MAX_VALUE)

    // Hepsi asenkron: bloke root okumasi ana thread'de arayuzu dondurur.

    fun rebootAsync() = RootShell.async("reboot") {}

    fun setAirplaneAsync(on: Boolean) =
        RootShell.async("cmd connectivity airplane-mode " + if (on) "enable" else "disable") {}

    fun airplaneOnAsync(cb: (Boolean) -> Unit) =
        RootShell.async("cmd connectivity airplane-mode") { cb(it?.contains("enabled") == true) }

    fun screenTimeoutAsync(cb: (Int) -> Unit) =
        RootShell.async("settings get system screen_off_timeout") {
            cb(it?.trim()?.toIntOrNull() ?: 60_000)
        }

    fun setScreenTimeoutAsync(ms: Int) =
        RootShell.async("settings put system screen_off_timeout $ms") {}

}
