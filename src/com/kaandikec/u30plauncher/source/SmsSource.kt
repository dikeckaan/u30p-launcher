package com.kaandikec.u30plauncher.source

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper

/**
 * Gelen kutusunu `content://sms/inbox` uzerinden okur.
 *
 * Bu bir MiFi: operator mesajlari (kota uyarisi, kampanya, bakiye) yalnizca
 * stok arayuzde gorunuyor ve launcher HOME oldugu icin oraya hic gidilmiyor.
 *
 * Sorgu ARKA PLANDA calisir. ContentResolver ilk cagride SMS saglayicisinin
 * surecini ayaga kaldiriyor; ana thread'den yapilirsa 30 Hz'lik arayuz gorunur
 * sekilde takiliyor. Sonuc ana thread'e geri postalanir, `messages` yalnizca
 * orada okunur/yazilir.
 */
class SmsSource(private val ctx: Context) {

    companion object {
        /** 240x240'ta 20 mesaj zaten uzun bir liste; eskisi cihazda duruyor. */
        const val LIMIT = 20

        const val STATE_IDLE = 0
        const val STATE_LOADING = 1
        const val STATE_READY = 2
        const val STATE_DENIED = 3
        const val STATE_FAILED = 4

        /**
         * `Telephony.Sms.Inbox.CONTENT_URI` ile ayni deger; sabit yazmak
         * saglayicinin ZTE tarafinda degistirilmis olma ihtimaline karsi
         * cihazda dogrulanan yolu birebir kullaniyor.
         */
        private val URI: Uri = Uri.parse("content://sms/inbox")

        private val COLUMNS = arrayOf("address", "date", "body", "read")

        private val worker: java.util.concurrent.ExecutorService =
            java.util.concurrent.Executors.newSingleThreadExecutor { r ->
                Thread(r, "u30p-sms").apply { isDaemon = true }
            }
    }

    class Message(
        val address: String,
        val body: String,
        val dateMs: Long,
        val unread: Boolean
    )

    /** Yalnizca ana thread'den erisilir; yukleme bitince liste topluca degisir. */
    var messages: List<Message> = emptyList()
        private set

    var state: Int = STATE_IDLE
        private set

    private val main = Handler(Looper.getMainLooper())
    private var grantTried = false

    /**
     * Gelen kutusunu tazeler; `onDone` her zaman ana thread'de cagrilir.
     *
     * Ust uste cagri yok sayilir — sayfa hem acilista hem tazele dugmesinden
     * cagirabiliyor.
     */
    fun load(onDone: () -> Unit) {
        if (state == STATE_LOADING) return
        if (!hasPermission()) {
            state = STATE_DENIED
            onDone()
            grantWithRoot(onDone)
            return
        }
        state = STATE_LOADING
        onDone()
        worker.execute {
            val rows = query()
            main.post {
                if (rows == null) {
                    state = STATE_FAILED
                } else {
                    messages = rows
                    state = STATE_READY
                }
                onDone()
            }
        }
    }

    private fun hasPermission(): Boolean = try {
        ctx.checkSelfPermission(android.Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /**
     * Izni root ile kendimize ver.
     *
     * 240 px yuvarlak ekranda sistem izin diyalogunun dugmeleri kismen kose
     * bolgesine dusuyor; `DataUsageSource` app-op icin ayni yolu kullaniyor.
     * Root yoksa veya reddedilirse sayfa "izin yok" der ve bir daha denenmez.
     */
    private fun grantWithRoot(onDone: () -> Unit) {
        if (grantTried) return
        grantTried = true
        RootShell.async(
            "pm grant ${ctx.packageName} android.permission.READ_SMS"
        ) {
            if (hasPermission()) load(onDone)
        }
    }

    /**
     * Siralamaya "LIMIT 20" eklemek her saglayicida calismiyor (kimi surum
     * ORDER BY dizesini dogruluyor); bunun yerine imlecten yalnizca ilk
     * `LIMIT` satir okunuyor — pencere zaten tembel dolduruluyor.
     */
    private fun query(): List<Message>? {
        var cur: Cursor? = null
        return try {
            cur = ctx.contentResolver.query(URI, COLUMNS, null, null, "date DESC")
            if (cur == null) {
                null
            } else {
                val ai = cur.getColumnIndex("address")
                val di = cur.getColumnIndex("date")
                val bi = cur.getColumnIndex("body")
                val ri = cur.getColumnIndex("read")
                val out = ArrayList<Message>(LIMIT)
                while (out.size < LIMIT && cur.moveToNext()) {
                    out.add(
                        Message(
                            address = if (ai >= 0) cur.getString(ai) ?: "" else "",
                            body = if (bi >= 0) cur.getString(bi) ?: "" else "",
                            dateMs = if (di >= 0) cur.getLong(di) else 0L,
                            unread = ri >= 0 && cur.getInt(ri) == 0
                        )
                    )
                }
                out
            }
        } catch (_: Throwable) {
            // SecurityException dahil: izin sonradan geri alinmis olabilir
            null
        } finally {
            try { cur?.close() } catch (_: Throwable) {}
        }
    }
}
