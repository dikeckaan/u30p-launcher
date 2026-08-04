package com.kaandikec.u30plauncher

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import com.kaandikec.u30plauncher.core.BatteryEstimate
import com.kaandikec.u30plauncher.core.CellIdentity
import com.kaandikec.u30plauncher.core.CellIdentityParser
import com.kaandikec.u30plauncher.core.CyclePeriod
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.core.UsageCalc
import com.kaandikec.u30plauncher.source.BatterySource
import com.kaandikec.u30plauncher.source.DataUsageSource
import com.kaandikec.u30plauncher.source.NetSource
import com.kaandikec.u30plauncher.source.RootShell
import com.kaandikec.u30plauncher.source.SystemSource
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

        /**
         * Trafik sayaclarinin diske yazilma araligi.
         *
         * Once yalnizca `pause()` aninda yaziliyordu. Launcher HOME oldugu
         * icin saatlerce hic duraklamiyor; surec oldugunde `lastTotal`
         * saatler oncesine donuyor ve bir sonraki acilista TEK bir dev delta
         * hesaplaniyordu. O delta gun sinirini assa bile tamami icinde
         * bulunulan gune yaziliyor, yani dunun trafigi bugun gorunuyordu.
         *
         * 30 sn'de bir yazmak yanlis atfi bu araliga indirir; SharedPreferences
         * `apply()` zaten asenkron ve yazilan alan bes tane.
         */
        private const val USAGE_SAVE_INTERVAL_MS = 30_000L
    }

    private val prefs = Prefs(ctx)
    private val usageStore = UsageStore(ctx)
    private val usage = usageStore.load()

    private val battery = BatterySource(ctx)
    private val telephony = TelephonySource(ctx)
    private val net = NetSource()
    private val dataUsage = DataUsageSource(ctx)
    private val thermal = ThermalSource()
    private val system = SystemSource()

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
            net.sample(SystemClock.elapsedRealtime())
            battery.poll()
            system.sample()
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
            battery.persist()
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

    /**
     * Once GOZLENEN bosalma hizindan hesapla; o gercek kullanimi icerdigi
     * icin cok daha kararli. Yeterli gozlem birikmediyse (ilk 10 dakika,
     * yeni acilis) anlik akima dus.
     */
    private fun batteryMinutesLeft(): Int {
        if (battery.charging) return Snapshot.UNKNOWN
        val fromDrain = BatteryEstimate.minutesLeftFromDrain(battery.chargeUah, battery.drainUahPerHour)
        if (fromDrain != Snapshot.UNKNOWN) return fromDrain
        return BatteryEstimate.minutesLeft(battery.chargeUah, battery.currentUa)
    }

    private fun pickCell(apiValue: Int, rootValue: Int): Int =
        if (apiValue != Snapshot.UNKNOWN) apiValue
        else if (cellFromRoot) rootValue
        else Snapshot.UNKNOWN

    private fun stampCalendar() {
        cal.timeInMillis = System.currentTimeMillis()
    }

    private var lastUsageSaveAt = 0L

    /** Calisma thread'inde cagrilir. `force` devir anlarinda kullanilir. */
    private fun saveUsageIfDue(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && lastUsageSaveAt != 0L && now - lastUsageSaveAt < USAGE_SAVE_INTERVAL_MS) {
            return
        }
        lastUsageSaveAt = now
        usageStore.save(usage)
        // Pil gecmisi de yalnizca pause'da yaziliyordu; ayni sebeple surec
        // oldugunde bosalma gozlemi sifirlaniyor ve kalan sure tahmini
        // bastan ogrenmek zorunda kaliyordu.
        battery.persist()
    }

    private fun rebuild() {
        if (!running) return

        stampCalendar()
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val dayKey = cal.get(Calendar.YEAR) * 366 + cal.get(Calendar.DAY_OF_YEAR)

        // Gun/donem sinirlarinin duvar saati karsiliklari; sinir asan bir delta
        // bunlara gore bolunuyor. `cal` burada geriye sariliyor, bir sonraki
        // rebuild yeniden damgaliyor.
        val nowMs = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStartMs = cal.timeInMillis

        // Donem ayin 1'inde baslamayabilir; cihazin Ayarlar ekrani da fatura
        // donemini kullaniyor, ayni pencereye bakalim.
        stampCalendar()
        CyclePeriod.applyStart(cal, prefs.cycleDay)
        val monthStartMs = cal.timeInMillis
        val monthKey = CyclePeriod.key(cal)

        // Ilk basarili ornekten once wanTotal 0'dir; onu gercek bir okuma sanip
        // sayaci sifirlamayalim.
        if (net.wanTotal > 0) {
            val prevDay = usage.dayKey
            val prevMonth = usage.monthKey
            UsageCalc.update(
                usage, net.wanTotal, dayKey, monthKey, nowMs, dayStartMs, monthStartMs
            )
            // Devir anini kacirmayalim: yeni gun sifirdan baslamis olmali ki
            // surec olurse eski gunun toplami geri gelmesin.
            val rolled = usage.dayKey != prevDay || usage.monthKey != prevMonth
            saveUsageIfDue(rolled)
        }

        // Cihazin kendi sayaci varsa o esas alinir: reboot'tan etkilenmiyor
        // ve launcher uykudayken akan trafigi de iceriyor. Okunamazsa kendi
        // biriktirdigimiz degere duseriz.
        dataUsage.sample(nowMs, dayStartMs, monthStartMs)
        val today = if (dataUsage.todayBytes >= 0) dataUsage.todayBytes else usage.dayBytes
        val month = if (dataUsage.monthBytes >= 0) dataUsage.monthBytes else usage.monthBytes

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
            todayBytes = today,
            monthBytes = month,
            cycleDay = prefs.cycleDay,
            batteryPct = battery.pct,
            batteryUa = battery.currentUa,
            batteryTempC = battery.tempC,
            batteryCharging = battery.charging,
            batteryMinutesLeft = batteryMinutesLeft(),
            cpuTempC = thermal.readTenthsC(),
            cpuPercent = system.cpuPercent,
            ramUsedKb = system.ramUsedKb,
            ramTotalKb = system.ramTotalKb,
            storageUsedBytes = system.storageUsedBytes,
            storageTotalBytes = system.storageTotalBytes,
            loadAvgX100 = system.loadAvgX100,
            // Saniye hassasiyeti yeterli; her tick degisip bosuna cizim
            // tetiklememesi icin dakikaya yuvarlanmiyor ama zaten yavas degisir
            uptimeSec = SystemClock.elapsedRealtime() / 1000,
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
