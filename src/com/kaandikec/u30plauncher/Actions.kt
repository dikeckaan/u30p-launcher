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

    /**
     * Stok ZTE arayuzunu acar.
     *
     * Paket normalde `pm disable-user` ile kapali tutuluyor (acilista home
     * task'ini kapip bizi eziyordu), o yuzden once etkinlestirmek gerek.
     * Varsayilan HOME degistirilmez; yalnizca aktivite baslatilir.
     */
    fun openStockUiAsync() = RootShell.async(
        "pm enable $STOCK_PKG; am start -n $STOCK_PKG/.HomeMainActivity"
    ) {}

    /** Geri donuldugunde tamamen kapat: once oldur, sonra yeniden devre disi birak. */
    fun killStockUiAsync() = RootShell.async(
        "am force-stop $STOCK_PKG; pm disable-user --user 0 $STOCK_PKG"
    ) {}

    private const val STOCK_PKG = "com.zte.mifavor.ufi.home"

    /** Elle parlaklik kademeleri (Android olcegi 0-255). */
    val BRIGHTNESS_LEVELS = intArrayOf(26, 64, 128, 191, 255)

    /** -1 = otomatik, degilse BRIGHTNESS_LEVELS icindeki indeks. */
    fun brightnessAsync(cb: (Int) -> Unit) = RootShell.async(
        "settings get system screen_brightness_mode; settings get system screen_brightness"
    ) { out ->
        val lines = out?.trim()?.lines() ?: emptyList()
        val mode = lines.getOrNull(0)?.trim()?.toIntOrNull() ?: 1
        val value = lines.getOrNull(1)?.trim()?.toIntOrNull() ?: 128
        if (mode == 1) {
            cb(-1)
        } else {
            // En yakin kademeyi bul; deger disaridan degismis olabilir
            var best = 0
            var bestD = Int.MAX_VALUE
            for (i in BRIGHTNESS_LEVELS.indices) {
                val d = Math.abs(BRIGHTNESS_LEVELS[i] - value)
                if (d < bestD) { bestD = d; best = i }
            }
            cb(best)
        }
    }

    /**
     * @param level -1 otomatik, degilse [BRIGHTNESS_LEVELS] indeksi.
     *
     * Elle deger verilirken otomatik mod kapatilmali; acik kalirsa isik
     * sensoru birkac saniye icinde ayari geri aliyor.
     */
    fun setBrightnessAsync(level: Int) {
        if (level < 0) {
            RootShell.async("settings put system screen_brightness_mode 1") {}
        } else {
            val v = BRIGHTNESS_LEVELS[level.coerceIn(0, BRIGHTNESS_LEVELS.size - 1)]
            RootShell.async(
                "settings put system screen_brightness_mode 0; " +
                    "settings put system screen_brightness $v"
            ) {}
        }
    }

    fun setScreenTimeoutAsync(ms: Int) =
        RootShell.async("settings put system screen_off_timeout $ms") {}

}
