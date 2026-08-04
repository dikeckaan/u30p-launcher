package com.kaandikec.u30plauncher.source

import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import com.kaandikec.u30plauncher.core.CpuTimes
import com.kaandikec.u30plauncher.core.MemInfo
import com.kaandikec.u30plauncher.core.MemInfoParser
import com.kaandikec.u30plauncher.core.ProcPidStat
import com.kaandikec.u30plauncher.core.ProcPidStatParser
import com.kaandikec.u30plauncher.core.ProcStatParser
import com.kaandikec.u30plauncher.core.Snapshot
import java.io.File
import java.io.FileInputStream

/**
 * Cihaz durumu: CPU, bellek, depolama, calisma suresi, yuk.
 *
 * `/proc` dosyalari yeniden kullanilan bir tampona okunur; String uretilmez.
 * Depolama `StatFs` ile okunur ve nadiren degistigi icin seyrek yenilenir —
 * her saniye dosya sistemi sorgulamanin anlami yok.
 */
class SystemSource {
    companion object {
        private const val BUF = 8192
        private const val STORAGE_INTERVAL_MS = 60_000L

        // Tum /proc'u taramak pahali; loadavg gibi saniyede bir gerekmiyor.
    }

    private val buf = ByteArray(BUF)
    private val statFile = File("/proc/stat")
    private val memFile = File("/proc/meminfo")
    private val loadFile = File("/proc/loadavg")

    private val prevCpu = CpuTimes()
    private val nowCpu = CpuTimes()
    private var havePrev = false
    private val mem = MemInfo()

    var cpuPercent: Int = Snapshot.UNKNOWN; private set
    var ramUsedKb: Long = 0; private set
    var ramTotalKb: Long = 0; private set
    var storageUsedBytes: Long = 0; private set
    var storageTotalBytes: Long = 0; private set

    /** Yuz katı: 1048 = 10.48 */
    var loadAvgX100: Int = Snapshot.UNKNOWN; private set

    /** En cok CPU yiyen kullanici surecinin adi; yoksa bos. */
    var topProcName: String = ""; private set

    /** O surecin toplam CPU icindeki yuzdesi (cpuPercent ile ayni olcek). */
    var topProcPercent: Int = Snapshot.UNKNOWN; private set

    private val pidStat = ProcPidStat()

    // pid -> onceki (utime+stime). Iki harita swap edilir; boylece olen
    // pid'ler birikmez. Boxing var ama tarama 5 sn'de bir ve cizim yolunda
    // degil, GC yuku ihmal edilebilir.
    private var prevTicks = HashMap<Int, Long>(256)
    private var curTicks = HashMap<Int, Long>(256)
    private var prevProcTotalCpu = 0L
    private var haveProcBaseline = false

    private var lastStorageAt = 0L

    private fun read(f: File): Int = try {
        FileInputStream(f).use { s ->
            var n = 0
            while (n < BUF) {
                val r = s.read(buf, n, BUF - n)
                if (r <= 0) break
                n += r
            }
            n
        }
    } catch (_: Throwable) {
        0
    }

    fun sample() {
        val statLen = read(statFile)
        if (statLen > 0 && ProcStatParser.parse(buf, statLen, nowCpu)) {
            if (havePrev) cpuPercent = ProcStatParser.usagePercent(prevCpu, nowCpu)
            prevCpu.total = nowCpu.total
            prevCpu.idle = nowCpu.idle
            havePrev = true
        }

        val memLen = read(memFile)
        if (memLen > 0 && MemInfoParser.parse(buf, memLen, mem)) {
            ramTotalKb = mem.totalKb
            ramUsedKb = mem.usedKb
        }

        readLoad()
        readStorageIfDue()
    }

    /**
     * En cok CPU yiyen sureci ROOT DOKUMUNDEN hesaplar.
     *
     * Dogrudan /proc taramasi bu cihazda ISE YARAMIYOR: /proc
     * `hidepid=invisible,gid=3009` ile bagli ve launcher normal bir uygulama
     * olarak (AID_READPROC olmadan) yalnizca KENDI surecini gorebiliyor —
     * olcum her zaman launcher'in kendisini gosterirdi. Bu yuzden dokumu
     * DataHub root kabugundan alip buraya veriyor.
     *
     * `dump` = surec basina bir satir olacak sekilde toplanmis
     * /proc/<pid>/stat ciktisi.
     * Olculdu: 507 surecte 0.03 sn, 5 sn'lik aralik icin kabul edilebilir.
     */
    fun applyProcDump(dump: String, totalCpuNow: Long) {
        curTicks.clear()
        val dTotal = totalCpuNow - prevProcTotalCpu

        var bestName = ""
        var bestDelta = 0L
        var bestPid = -1

        var i = 0
        val n = dump.length
        while (i < n) {
            var e = dump.indexOf('\n', i)
            if (e < 0) e = n
            // Satiri yeniden kullanilan tampona kopyala; stat satirlari ASCII.
            var len = e - i
            if (len > buf.size) len = buf.size
            for (k in 0 until len) buf[k] = dump[i + k].code.toByte()
            i = e + 1
            if (len <= 0) continue
            if (!ProcPidStatParser.parse(buf, len, pidStat)) continue

            // Cekirdek parcaciklari kthreadd'nin (pid 2) cocugudur; onlari
            // "kullanici sureci" olarak gostermek yaniltici olur. Zaten
            // yaniltici LOAD olcusunu bu yuzden kaldirdik.
            if (pidStat.ppid == 2) continue
            val pid = pidStat.pid
            if (pid <= 2) continue

            val ticks = pidStat.cpuTicks
            curTicks[pid] = ticks
            if (!haveProcBaseline) continue
            val prev = prevTicks[pid] ?: continue
            val d = ticks - prev
            if (d > bestDelta) {
                bestDelta = d
                bestName = pidStat.comm
                bestPid = pid
            }
        }

        if (haveProcBaseline && dTotal > 0 && bestPid >= 0) {
            // ONDA BIRLIK: paydada 8 cekirdegin toplami var, tek bir surec
            // nadiren %1'i asiyor ve tam sayida her zaman "0%" gorunuyordu.
            val pct = bestDelta * 1000L / dTotal
            topProcPercent = if (pct > 1000L) 1000 else pct.toInt()
            topProcName = bestName
        } else if (!haveProcBaseline) {
            topProcPercent = Snapshot.UNKNOWN
            topProcName = ""
        }
        // Aksi halde (aday yok / pencere bos) onceki deger korunur

        val tmp = prevTicks
        prevTicks = curTicks
        curTicks = tmp
        prevProcTotalCpu = totalCpuNow
        haveProcBaseline = true
    }

    /** DataHub dokumu isterken toplam CPU'yu da vermeli. */
    val cpuTotalTicks: Long get() = nowCpu.total


    /** `/proc/loadavg` ilk alani: "10.48 10.49 11.02 1/1754 14723" */
    private fun readLoad() {
        val len = read(loadFile)
        if (len <= 0) return
        var whole = 0
        var frac = 0
        var p = 0
        while (p < len && buf[p] >= '0'.code.toByte() && buf[p] <= '9'.code.toByte()) {
            whole = whole * 10 + (buf[p] - '0'.code.toByte())
            p++
        }
        if (p < len && buf[p] == '.'.code.toByte()) {
            p++
            var digits = 0
            while (p < len && digits < 2 &&
                buf[p] >= '0'.code.toByte() && buf[p] <= '9'.code.toByte()
            ) {
                frac = frac * 10 + (buf[p] - '0'.code.toByte())
                digits++
                p++
            }
            if (digits == 1) frac *= 10
        }
        loadAvgX100 = whole * 100 + frac
    }

    private fun readStorageIfDue() {
        val now = SystemClock.elapsedRealtime()
        if (lastStorageAt != 0L && now - lastStorageAt < STORAGE_INTERVAL_MS) return
        lastStorageAt = now
        try {
            val fs = StatFs(Environment.getDataDirectory().path)
            val block = fs.blockSizeLong
            storageTotalBytes = fs.blockCountLong * block
            storageUsedBytes = storageTotalBytes - fs.availableBlocksLong * block
        } catch (_: Throwable) {
        }
    }
}
