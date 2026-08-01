package com.kaandikec.u30plauncher

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import com.kaandikec.u30plauncher.core.CellIdentity
import com.kaandikec.u30plauncher.core.CellIdentityParser
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
        /** ARP tablosu ve hucre kimligi saniyede bir degismez; seyrek okunur. */
        private const val SLOW_INTERVAL_MS = 5000L
    }

    private val prefs = Prefs(ctx)
    private val usageStore = UsageStore(ctx)
    private val usage = usageStore.load()

    private val battery = BatterySource(ctx)
    private val telephony = TelephonySource(ctx)
    private val net = NetSource()
    private val thermal = ThermalSource()

    /**
     * Yoklama ve root komutlari ARKA PLANDA calisir.
     *
     * Once ana Looper'daydi; `RootShell` bloke okuma yaptigi icin `su` kabugu
     * bir an takildiginda tum arayuz doniyordu. Snapshot uretimi burada olur,
     * dinleyici ana thread'de cagrilir.
     */
    private var worker: HandlerThread? = null
    private var work: Handler? = null
    private val main = Handler(Looper.getMainLooper())
    private val cal: Calendar = Calendar.getInstance()

    private var listener: ((Snapshot) -> Unit)? = null
    private var running = false
    private var clientsCached = -1
    private var lastSlowAt = 0L
    private var rootChecked = false
    private var rootOk = false

    /**
     * Konum servisi kapaliyken Android hucre kimligini maskeler. Root varsa
     * ayni veriyi dumpsys'ten okuruz; boylece kullanicinin sistem ayarini
     * degistirmemiz gerekmez.
     */
    private val cell = CellIdentity()
    private var cellFromRoot = false

    /** Calisma thread'inde yazilir, ana thread'de okunur. */
    @Volatile
    var snapshot: Snapshot = Snapshot.EMPTY
        private set

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            net.sample(System.currentTimeMillis())
            refreshSlowIfDue()
            rebuild()
            work?.postDelayed(this, prefs.refreshMs.toLong())
        }
    }

    fun resume(l: (Snapshot) -> Unit) {
        if (running) return
        running = true
        listener = l
        val t = HandlerThread("u30p-data").apply { start() }
        worker = t
        work = Handler(t.looper)
        // Kaynak geri cagrilari ana thread'e gelir; isi calisma thread'ine at
        battery.start { work?.post(::rebuild) }
        telephony.start { work?.post(::rebuild) }
        work?.post(tick)
    }

    fun pause() {
        if (!running) return
        running = false
        battery.stop()
        telephony.stop()
        listener = null
        val t = worker
        val w = work
        worker = null
        work = null
        w?.removeCallbacksAndMessages(null)
        // Kapanis islerini de calisma thread'inde yap, sonra looper'i bitir
        w?.post {
            usageStore.save(usage)
            RootShell.close()
            t?.quitSafely()
        }
    }

    /** Root gerektiren, yavas degisen alanlar; 5 sn'de bir okunur. */
    private fun refreshSlowIfDue() {
        val now = SystemClock.elapsedRealtime()
        if (rootChecked && now - lastSlowAt < SLOW_INTERVAL_MS) return
        lastSlowAt = now

        if (!rootChecked) {
            rootOk = RootShell.isAvailable()
            rootChecked = true
        }
        if (!rootOk) {
            clientsCached = -1
            cellFromRoot = false
            return
        }

        // ARP tablosunda br0 (LAN kopru) uzerindeki girdiler
        clientsCached = RootShell.exec("grep -c ' br0\$' /proc/net/arp")?.trim()?.toIntOrNull() ?: -1

        // Hucre kimligi yalnizca API yolundan gelmiyorsa dumpsys'e bas
        if (telephony.pci == Snapshot.UNKNOWN && telephony.ci == Snapshot.UNKNOWN) {
            val dump = RootShell.exec(
                "dumpsys telephony.registry 2>/dev/null | grep -m1 -o 'CellIdentityLte:{[^}]*}'"
            )
            cellFromRoot = dump != null && CellIdentityParser.parse(dump, cell)
        } else {
            cellFromRoot = false
        }
    }

    private fun pickCell(apiValue: Int, rootValue: Int): Int =
        if (apiValue != Snapshot.UNKNOWN) apiValue
        else if (cellFromRoot) rootValue
        else Snapshot.UNKNOWN

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
            band = pickCell(telephony.band, cell.band),
            hasService = telephony.hasService,
            rsrp = telephony.rsrp,
            rsrq = telephony.rsrq,
            sinr = telephony.sinr,
            pci = pickCell(telephony.pci, cell.pci),
            earfcn = pickCell(telephony.earfcn, cell.earfcn),
            tac = pickCell(telephony.tac, cell.tac),
            ci = pickCell(telephony.ci, cell.ci),
            bandwidthKhz = pickCell(telephony.bandwidthKhz, cell.bandwidthKhz),
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
            val l = listener
            if (l != null) main.post { if (running) l(next) }
        }
    }
}
