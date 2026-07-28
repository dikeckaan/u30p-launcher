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

    fun reboot(): Boolean = RootShell.exec("reboot") != null

    fun airplaneOn(): Boolean =
        RootShell.exec("cmd connectivity airplane-mode")?.contains("enabled") == true

    fun setAirplane(on: Boolean): Boolean =
        RootShell.exec("cmd connectivity airplane-mode " + if (on) "enable" else "disable") != null

    fun screenTimeout(): Int =
        RootShell.exec("settings get system screen_off_timeout")?.trim()?.toIntOrNull() ?: 60_000

    fun setScreenTimeout(ms: Int): Boolean =
        RootShell.exec("settings put system screen_off_timeout $ms") != null
}
