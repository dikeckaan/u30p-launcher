package com.kaandikec.u30plauncher

import android.content.Context
import android.content.Intent
import java.io.File
import java.io.PrintWriter

/**
 * Bir launcher cokerse cihaz kullanilamaz hale gelir. Bu yuzden hata diske
 * yazilip Activity yeniden baslatilir.
 *
 * Yeniden baslatma dongusune girmemek icin 10 saniye icinde ikinci bir cokme
 * olursa varsayilan davranisa birakilir.
 */
object CrashGuard {
    private const val LOG = "crash.log"
    private var lastCrashAt = 0L

    fun install(ctx: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val now = System.currentTimeMillis()
            val looping = now - lastCrashAt < 10_000L
            lastCrashAt = now

            try {
                PrintWriter(File(ctx.filesDir, LOG)).use { w ->
                    w.println(now.toString())
                    error.printStackTrace(w)
                }
            } catch (_: Throwable) {
            }

            if (looping) {
                prev?.uncaughtException(thread, error)
                return@setDefaultUncaughtExceptionHandler
            }

            try {
                val i = Intent(ctx, LauncherActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                ctx.startActivity(i)
            } catch (_: Throwable) {
            }

            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
