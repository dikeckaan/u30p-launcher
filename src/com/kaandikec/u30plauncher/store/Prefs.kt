package com.kaandikec.u30plauncher.store

import android.content.Context

class Prefs(ctx: Context) {
    companion object {
        const val THEME_STACKED = 0
        const val THEME_ARC = 1
        const val THEME_BALANCED = 2
        const val THEME_COUNT = 3

        val REFRESH_OPTIONS = intArrayOf(500, 1000, 2000, 5000)
        const val NAME = "u30p"
    }

    private val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var theme: Int
        get() = sp.getInt("theme", THEME_STACKED).coerceIn(0, THEME_COUNT - 1)
        set(v) { sp.edit().putInt("theme", v).apply() }

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

    var systemPage: Boolean
        get() = sp.getBoolean("page_system", true)
        set(v) { sp.edit().putBoolean("page_system", v).apply() }
}
