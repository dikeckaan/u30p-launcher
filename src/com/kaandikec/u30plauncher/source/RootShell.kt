package com.kaandikec.u30plauncher.source

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Tek uzun omurlu `su` oturumu.
 *
 * Her komut icin ayri process acmak saniyede bir calisan bir yoklama icin
 * pahalidir; bunun yerine tek bir kabuk acip komutlari stdin'e yaziyoruz.
 * Her komuttan sonra bir isaretci yazdirip ona kadar okuyarak cikti sinirini
 * belirliyoruz.
 *
 * Oturum `close()` ile kapatilir — Activity duraklayinca cagrilir, boylece
 * ekran kapaliyken process ayakta kalmaz.
 */
object RootShell {
    private const val MARK = "__U30P_EOF__"

    private var proc: Process? = null
    private var writer: OutputStreamWriter? = null
    private var reader: BufferedReader? = null

    /** `su` bir kez basarisiz olduysa her cagride yeniden denemenin anlami yok. */
    private var unavailable = false

    @Synchronized
    private fun ensure(): Boolean {
        if (unavailable) return false
        proc?.let { if (it.isAlive) return true }
        return try {
            val p = ProcessBuilder("su").redirectErrorStream(true).start()
            proc = p
            writer = OutputStreamWriter(p.outputStream)
            reader = BufferedReader(InputStreamReader(p.inputStream))
            true
        } catch (t: Throwable) {
            unavailable = true
            false
        }
    }

    @Synchronized
    fun exec(cmd: String): String? {
        if (!ensure()) return null
        return try {
            val w = writer!!
            w.write(cmd)
            w.write("\necho $MARK\n")
            w.flush()
            val r = reader!!
            val sb = StringBuilder()
            while (true) {
                val line = r.readLine() ?: return null
                if (line == MARK) break
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(line)
            }
            sb.toString()
        } catch (t: Throwable) {
            closeInternal()
            null
        }
    }

    @Synchronized
    fun isAvailable(): Boolean = exec("id -u")?.trim() == "0"

    @Synchronized
    fun close() = closeInternal()

    private fun closeInternal() {
        try { writer?.close() } catch (_: Throwable) {}
        try { reader?.close() } catch (_: Throwable) {}
        try { proc?.destroy() } catch (_: Throwable) {}
        writer = null
        reader = null
        proc = null
    }
}
