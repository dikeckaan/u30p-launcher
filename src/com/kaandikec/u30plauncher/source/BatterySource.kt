package com.kaandikec.u30plauncher.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.kaandikec.u30plauncher.core.Snapshot

/**
 * `ACTION_BATTERY_CHANGED` sticky broadcast — yoklama yok, olay geldiginde
 * guncellenir. Yalnizca Activity ondeyken kayitlidir.
 */
class BatterySource(private val ctx: Context) {
    var pct: Int = Snapshot.UNKNOWN
        private set
    var currentUa: Int = 0
        private set

    /** ondabir °C */
    var tempC: Int = Snapshot.UNKNOWN
        private set

    private var onChange: (() -> Unit)? = null
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            i ?: return
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            pct = if (level < 0 || scale <= 0) Snapshot.UNKNOWN else level * 100 / scale
            tempC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Snapshot.UNKNOWN)
            currentUa = try {
                val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
            } catch (_: Throwable) {
                0
            }
            onChange?.invoke()
        }
    }

    fun start(onChange: () -> Unit) {
        if (registered) return
        this.onChange = onChange
        try {
            ctx.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            registered = true
        } catch (_: Throwable) {
        }
    }

    fun stop() {
        if (!registered) return
        try { ctx.unregisterReceiver(receiver) } catch (_: Throwable) {}
        registered = false
        onChange = null
    }
}
