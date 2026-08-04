package com.kaandikec.u30plauncher.store

import android.content.Context

class Prefs(ctx: Context) {
    companion object {
        const val THEME_STACKED = 0
        const val THEME_ARC = 1
        const val THEME_BALANCED = 2
        const val THEME_COUNT = 3

        val REFRESH_OPTIONS = intArrayOf(500, 1000, 2000, 5000)

        /** Dil: sistem varsayilani, ya da acikca secilmis. */
        const val LANG_SYSTEM = 0
        const val LANG_EN = 1
        const val LANG_TR = 2
        const val LANG_COUNT = 3

        /** Kilit acma yontemi. */
        const val LOCK_HOLD = 0
        const val LOCK_PATTERN = 1
        const val LOCK_PIN = 2
        const val LOCK_COUNT = 3
        /** Donem baslangic gunu ust siniri; gerekce core/CyclePeriod'da. */
        const val CYCLE_DAY_MAX = com.kaandikec.u30plauncher.core.CyclePeriod.MAX_DAY

        /**
         * Veri limiti secenekleri (MB). 0 = SINIRSIZ.
         *
         * Varsayilan sinirsiz: cihaz bir MiFi ve cogu kullanim kotasiz; her
         * acilista uyari gostermek gurultu olurdu.
         */
        val DATA_LIMIT_OPTIONS = intArrayOf(
            0, 1024, 2048, 5120, 10240, 20480, 51200, 102400
        )

        const val NAME = "u30p"
    }

    private val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var theme: Int
        get() = sp.getInt("theme", THEME_STACKED).coerceIn(0, THEME_COUNT - 1)
        set(v) { sp.edit().putInt("theme", v).apply() }

    /** Faturalama doneminin basladigi ayin gunu (1..28). */
    var cycleDay: Int
        get() = sp.getInt("cycle_day", 1).coerceIn(1, CYCLE_DAY_MAX)
        set(v) { sp.edit().putInt("cycle_day", v.coerceIn(1, CYCLE_DAY_MAX)).apply() }

    /** Donem veri limiti (MB); 0 = sinirsiz. */
    var dataLimitMb: Int
        get() = sp.getInt("data_limit_mb", 0)
        set(v) { sp.edit().putInt("data_limit_mb", v).apply() }

    /**
     * Donem gunu cihazin politikasindan bir kez alindi mi?
     *
     * Yalnizca ilk seferde alinir; sonra kullanicinin secimi esastir.
     */
    var cycleDayAdopted: Boolean
        get() = sp.getBoolean("cycle_day_adopted", false)
        set(v) { sp.edit().putBoolean("cycle_day_adopted", v).apply() }

    var refreshMs: Int
        get() = sp.getInt("refresh_ms", 1000)
        set(v) { sp.edit().putInt("refresh_ms", v).apply() }

    var detailPage: Boolean
        get() = sp.getBoolean("page_detail", true)
        set(v) { sp.edit().putBoolean("page_detail", v).apply() }

    /**
     * Stok ZTE arayuzu bizim uzerimizden acildi mi?
     *
     * Kalici tutulmali: stok launcher one gelince Android bu Activity'yi yok
     * ediyor ve geri donuldugunde yeni bir ornek basliyor — bellekteki bayrak
     * kaybolup temizlik hic calismiyordu.
     */
    var stockUiOpen: Boolean
        get() = sp.getBoolean("stock_ui_open", false)
        set(v) { sp.edit().putBoolean("stock_ui_open", v).apply() }

    var engineeringPage: Boolean
        get() = sp.getBoolean("page_engineering", true)
        set(v) { sp.edit().putBoolean("page_engineering", v).apply() }

    /**
     * Mesajlar sayfasi SISTEM seridinde: INFO seridi kilitliyken de ciziliyor,
     * mesajlar kilit ekraninda gorunmemeli.
     */
    var smsPage: Boolean
        get() = sp.getBoolean("page_sms", true)
        set(v) { sp.edit().putBoolean("page_sms", v).apply() }

    var historyPage: Boolean
        get() = sp.getBoolean("page_history", true)
        set(v) { sp.edit().putBoolean("page_history", v).apply() }

    var language: Int
        get() = sp.getInt("language", LANG_SYSTEM).coerceIn(0, LANG_COUNT - 1)
        set(v) { sp.edit().putInt("language", v).apply() }

    /**
     * Dil degisiminden sonra ayarlar sayfasina donulsun mu?
     *
     * Dil degisimi Activity'yi yeniden olusturuyor; bayrak olmadan kullanici
     * kilitli ana ekrana dusuyor ve ayarlarda kaldigi yeri kaybediyor.
     */
    var returnToSettings: Boolean
        get() = sp.getBoolean("return_to_settings", false)
        set(v) { sp.edit().putBoolean("return_to_settings", v).apply() }

    var lockMode: Int
        get() = sp.getInt("lock_mode", LOCK_HOLD).coerceIn(0, LOCK_COUNT - 1)
        set(v) { sp.edit().putInt("lock_mode", v).apply() }

    /** Desen/PIN'in SHA-256 ozeti; duz metin saklanmaz. */
    var lockSecret: String
        get() = sp.getString("lock_secret", "") ?: ""
        set(v) { sp.edit().putString("lock_secret", v).apply() }

    /**
     * Saklanan sirrin ait oldugu kilit modu (-1 = sir yok).
     *
     * Desen ve PIN sirlari birbirinin yerine gecemez; hangi mod icin
     * kaydedildigini bilmek, mod degistirirken gereksiz yere yeniden kayit
     * istemeyi de gereksiz yere yanlis sirri kabul etmeyi de onler.
     */
    var lockSecretMode: Int
        get() = sp.getInt("lock_secret_mode", -1)
        set(v) { sp.edit().putInt("lock_secret_mode", v).apply() }

    var systemPage: Boolean
        get() = sp.getBoolean("page_system", true)
        set(v) { sp.edit().putBoolean("page_system", v).apply() }
}
