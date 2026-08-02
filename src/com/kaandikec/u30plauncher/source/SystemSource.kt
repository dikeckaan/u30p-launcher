package com.kaandikec.u30plauncher.source

import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import com.kaandikec.u30plauncher.core.CpuTimes
import com.kaandikec.u30plauncher.core.MemInfo
import com.kaandikec.u30plauncher.core.MemInfoParser
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
