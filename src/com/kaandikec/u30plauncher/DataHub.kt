package com.kaandikec.u30plauncher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.core.UsageCalc
import com.kaandikec.u30plauncher.source.BatterySource
import com.kaandikec.u30plauncher.source.NetSource
import com.kaandikec.u30plauncher.source.RootShell
import com.kaandikec.u30plauncher.source.TelephonySource
import com.kaandikec.u30plauncher.source.ThermalSource
import com.kaandikec.u30plauncher.store.Prefs
import com.kaandikec.u30plauncher.store.UsageStore
import java.util.Calendar

/**
 * Kaynaklari toplayip `Snapshot` uretir.
 *
 * Yalnizca `resume()` ile `pause()` arasinda calisir; ekran kapaliyken hicbir
 * timer, receiver veya process ayakta kalmaz.
 *
 * Snapshot bir oncekine esitse dinleyici cagrilmaz — cizim tetiklenmez.
 */
class DataHub(ctx: Context) {
    companion object {
        /** ARP tablosu saniyede bir degismez; istemci sayisi seyrek okunur. */
        private const val CLIENTS_INTERVAL_MS = 5000L
    }

    private val prefs = Prefs(ctx)
    private val usageStore = UsageStore(ctx)
    private val usage = usageStore.load()

    private val battery = BatterySource(ctx)
    private val telephony = TelephonySource(ctx)
    private val net = NetSource()
    private val thermal = ThermalSource()

    private val handler = Handler(Looper.getMainLooper())
    private val cal: Calendar = Calendar.getInstance()

    private var listener: ((Snapshot) -> Unit)? = null
    private var running = false
    private var clientsCached = -1
    private var lastClientsAt = 0L
    private var rootChecked = false
    private var rootOk = false

    var snapshot: Snapshot = Snapshot.EMPTY
        private set

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            net.sample(System.currentTimeMillis())
            refreshClientsIfDue()
            rebuild()
            handler.postDelayed(this, prefs.refreshMs.toLong())
        }
    }

    fun resume(l: (Snapshot) -> Unit) {
        if (running) return
        running = true
        listener = l
        battery.start(::rebuild)
        telephony.start(::rebuild)
        handler.post(tick)
    }

    fun pause() {
        if (!running) return
        running = false
        handler.removeCallbacks(tick)
        battery.stop()
        telephony.stop()
        usageStore.save(usage)
        RootShell.close()
        listener = null
    }

    private fun refreshClientsIfDue() {
        val now = SystemClock.elapsedRealtime()
        if (rootChecked && now - lastClientsAt < CLIENTS_INTERVAL_MS) return
        lastClientsAt = now
        if (!rootChecked) {
            rootOk = RootShell.isAvailable()
            rootChecked = true
        }
        if (!rootOk) {
            clientsCached = -1
            return
        }
        // ARP tablosunda br0 (LAN kopru) uzerindeki tamamlanmis girdiler
        val out = RootShell.exec("grep -c ' br0\$' /proc/net/arp")
        clientsCached = out?.trim()?.toIntOrNull() ?: -1
    }

    private fun stampCalendar() {
        cal.timeInMillis = System.currentTimeMillis()
    }

    private fun rebuild() {
        if (!running) return

        stampCalendar()
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val dayKey = cal.get(Calendar.YEAR) * 366 + cal.get(Calendar.DAY_OF_YEAR)
        val monthKey = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)

        // Ilk basarili ornekten once wanTotal 0'dir; onu gercek bir okuma sanip
        // sayaci sifirlamayalim.
        if (net.wanTotal > 0) UsageCalc.update(usage, net.wanTotal, dayKey, monthKey)

        val next = Snapshot(
            operator = telephony.operator,
            netType = telephony.netType,
            band = telephony.band,
            hasService = telephony.hasService,
            rsrp = telephony.rsrp,
            rsrq = telephony.rsrq,
            sinr = telephony.sinr,
            pci = telephony.pci,
            earfcn = telephony.earfcn,
            tac = telephony.tac,
            ci = telephony.ci,
            bandwidthKhz = telephony.bandwidthKhz,
            signalLevel = telephony.level,
            rxSpeed = net.rxSpeed,
            txSpeed = net.txSpeed,
            todayBytes = usage.dayBytes,
            monthBytes = usage.monthBytes,
            batteryPct = battery.pct,
            batteryUa = battery.currentUa,
            batteryTempC = battery.tempC,
            cpuTempC = thermal.readTenthsC(),
            vpnUp = net.vpnUp,
            clients = clientsCached,
            clockMinuteOfDay = minute,
            rootAvailable = rootOk,
            phonePermission = telephony.permitted
        )

        if (next != snapshot) {
            snapshot = next
            listener?.invoke(next)
        }
    }
}
