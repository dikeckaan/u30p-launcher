package com.kaandikec.u30plauncher.store

import android.content.Context
import com.kaandikec.u30plauncher.core.UsageState

/** Trafik sayaclarinin kalici saklanmasi; reboot sonrasi devam icin gerekli. */
class UsageStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("u30p_usage", Context.MODE_PRIVATE)

    fun load(): UsageState = UsageState().apply {
        dayKey = sp.getInt("day_key", -1)
        monthKey = sp.getInt("month_key", -1)
        dayBytes = sp.getLong("day_bytes", 0)
        monthBytes = sp.getLong("month_bytes", 0)
        lastTotal = sp.getLong("last_total", -1)
        lastAtMs = sp.getLong("last_at_ms", 0)
    }

    fun save(s: UsageState) {
        sp.edit()
            .putInt("day_key", s.dayKey)
            .putInt("month_key", s.monthKey)
            .putLong("day_bytes", s.dayBytes)
            .putLong("month_bytes", s.monthBytes)
            .putLong("last_total", s.lastTotal)
            .putLong("last_at_ms", s.lastAtMs)
            .apply()
    }
}
