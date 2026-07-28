package com.kaandikec.u30plauncher.source

import com.kaandikec.u30plauncher.core.Snapshot
import java.io.File

/**
 * SoC sicakligi `/sys/class/thermal` altindan okunur — root gerekmez.
 *
 * Bolge indeksi cihazdan cihaza degistigi icin dosya `type` icerigine gore bir
 * kez cozulup onbelleklenir. Degerler millidereke cinsindendir.
 */
class ThermalSource {
    companion object {
        private val PREFERRED = arrayOf("soc-thmzone", "cluster0-thmzone", "cpu-thmzone")
        private const val ROOT = "/sys/class/thermal"
    }

    private var resolved = false
    private var tempFile: File? = null

    private fun resolve() {
        resolved = true
        val zones = File(ROOT).listFiles() ?: return
        for (want in PREFERRED) {
            for (z in zones) {
                val typeFile = File(z, "type")
                if (!typeFile.canRead()) continue
                val type = try { typeFile.readText().trim() } catch (_: Throwable) { continue }
                if (type == want) {
                    val t = File(z, "temp")
                    if (t.canRead()) {
                        tempFile = t
                        return
                    }
                }
            }
        }
    }

    /** ondabir °C, okunamazsa UNKNOWN */
    fun readTenthsC(): Int {
        if (!resolved) resolve()
        val f = tempFile ?: return Snapshot.UNKNOWN
        return try {
            val milli = f.readText().trim().toLong()
            (milli / 100L).toInt()
        } catch (_: Throwable) {
            Snapshot.UNKNOWN
        }
    }
}
