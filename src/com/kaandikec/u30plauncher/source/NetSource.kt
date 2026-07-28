package com.kaandikec.u30plauncher.source

import com.kaandikec.u30plauncher.core.IfCounters
import com.kaandikec.u30plauncher.core.ProcNetParser
import java.io.File
import java.io.FileInputStream

/**
 * `/proc/net/dev`'i yeniden kullanilan bir tampona okur ve delta hizlari
 * hesaplar. String uretmez.
 *
 * WAN = `sipa_eth0` (modem arayuzu), VPN = `tun0`.
 */
class NetSource {
    companion object {
        private const val WAN = "sipa_eth0"
        private const val VPN = "tun0"
        private const val BUF = 8192
    }

    private val buf = ByteArray(BUF)
    private val counters = IfCounters()
    private val file = File("/proc/net/dev")

    private var lastRx = -1L
    private var lastTx = -1L
    private var lastAt = 0L

    var rxSpeed: Long = 0; private set
    var txSpeed: Long = 0; private set
    var wanTotal: Long = 0; private set
    var vpnUp: Boolean = false; private set

    private fun read(): Int = try {
        FileInputStream(file).use { s ->
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

    /** Bir ornek alir; hizlari onceki ornege gore gunceller. */
    fun sample(nowMs: Long) {
        val len = read()
        if (len <= 0) return

        // tun0 hem var hem trafik gormus olmali; arayuz kalintisi VPN sayilmasin
        vpnUp = ProcNetParser.parse(buf, len, VPN, counters) &&
            (counters.rx > 0 || counters.tx > 0)

        if (!ProcNetParser.parse(buf, len, WAN, counters)) return
        val rx = counters.rx
        val tx = counters.tx
        wanTotal = rx + tx

        if (lastRx >= 0 && nowMs > lastAt) {
            val dt = nowMs - lastAt
            val dRx = if (rx < lastRx) rx else rx - lastRx
            val dTx = if (tx < lastTx) tx else tx - lastTx
            rxSpeed = dRx * 1000L / dt
            txSpeed = dTx * 1000L / dt
        }
        lastRx = rx
        lastTx = tx
        lastAt = nowMs
    }
}
