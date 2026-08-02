package com.kaandikec.u30plauncher.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.core.BatteryHistory
import com.kaandikec.u30plauncher.core.TimeAverage

/**
 * `ACTION_BATTERY_CHANGED` sticky broadcast — yoklama yok, olay geldiginde
 * guncellenir. Yalnizca Activity ondeyken kayitlidir.
 */
class BatterySource(private val ctx: Context) {
    var pct: Int = Snapshot.UNKNOWN
        private set
    /** Ortalanmis akim (uA). Anlik deger cok dalgalandigi icin tahmin zipliyordu. */
    var currentUa: Int = 0
        private set

    /** Yakit gostergesinin olctugu kalan sarj (uAh). */
    var chargeUah: Int = 0
        private set

    var charging: Boolean = false
        private set

    /** 5 dakikalik zaman sabiti; ham akim kalan sureyi surekli ziplatiyordu. */
    private val currentAvg = TimeAverage()
    private var lastPollAt = 0L

    /** Gercek bosalma gozlemi — tahminin birincil kaynagi. */
    private val history = BatteryHistory()
    private val store = ctx.getSharedPreferences("u30p_battery", Context.MODE_PRIVATE)

    /** Gozlenen bosalma hizi (uAh/saat) veya UNKNOWN. */
    val drainUahPerHour: Int get() = history.drainUahPerHour()

    init {
        history.load(store.getString("history", "") ?: "", android.os.SystemClock.elapsedRealtime())
    }

    /** Surec olmeden once gecmisi sakla; gozlem saatler surdugu icin degerli. */
    fun persist() {
        store.edit().putString("history", history.serialize()).apply()
    }

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
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val nowCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            if (nowCharging != charging) {
                // Sarj/desarj gecisi: onceki orneklerin isareti bile farkli,
                // ortalamayi tasimak fisten cekince dakikalarca yanlis bir
                // "kalan sure" gostermeye yol aciyordu.
                currentAvg.reset()
                history.reset()
                lastPollAt = 0L
            }
            charging = nowCharging
            onChange?.invoke()
        }
    }

    /**
     * Akim ve kalan sarji donanimdan okur.
     *
     * Yayin (broadcast) seyrek geldigi icin bunlar poll doneminde okunur.
     * Cagiran ARKA PLAN thread'inde olmali: binder cagrisi.
     */
    fun poll() {
        val bm = try {
            ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        } catch (_: Throwable) {
            null
        } ?: return

        try {
            val now = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val t = android.os.SystemClock.elapsedRealtime()
            currentAvg.add(now, if (lastPollAt == 0L) 0L else t - lastPollAt)
            lastPollAt = t
            currentUa = currentAvg.value()
        } catch (_: Throwable) {
        }

        try {
            chargeUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            if (charging) history.reset() else {
                history.add(chargeUah, android.os.SystemClock.elapsedRealtime())
            }
        } catch (_: Throwable) {
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
