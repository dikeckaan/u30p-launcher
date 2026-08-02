package com.kaandikec.u30plauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import com.kaandikec.u30plauncher.core.ArpParser
import com.kaandikec.u30plauncher.core.Client
import com.kaandikec.u30plauncher.core.SoftApInfo
import com.kaandikec.u30plauncher.core.SoftApParser
import com.kaandikec.u30plauncher.R
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.source.RootShell
import com.kaandikec.u30plauncher.ui.theme.ThemeUtil

/**
 * WiFi bilgisi: SSID, parola ve bagli istemciler.
 *
 * Bir MiFi'de en sik ihtiyac duyulan ekran budur — misafire ag adini ve
 * parolayi gostermek. Parola yalnizca root ile okunabilir; framework API'si
 * imza izni olmadan vermez.
 *
 * Kilit katmanindadir: parola sürekli ekranda durmasin.
 */
class WifiPage(ctx: Context) : PageView(ctx) {
    companion object {
        private const val CLIENT_REFRESH_MS = 5000L
        private const val CONF =
            "/data/misc/apexdata/com.android.wifi/WifiConfigStoreSoftAp.xml"
    }

    private val title = ThemeUtil.text(10f, Palette.DIM2)
    private val label = ThemeUtil.text(9f, Palette.DIM2)
    private val ssidPaint = ThemeUtil.text(16f, Palette.FG)
    private val passPaint = ThemeUtil.text(15f, Palette.DOWN)
    private val clientPaint = ThemeUtil.text(11f, Palette.FG)
    private val hint = ThemeUtil.text(9f, Palette.DIM2)
    private val hair = ThemeUtil.hairline(Palette.HAIRLINE)

    private val ap = SoftApInfo()
    private val clients = ArrayList<Client>(8)
    private var lastClientsAt = 0L
    private var loaded = false
    private val sb = StringBuilder(24)

    override val showDots: Boolean get() = false

    /** Kilit acildiginda cagrilir. SSID ve parola nadiren degisir, bir kez okunur. */
    fun refreshState() {
        if (!snapshot.rootAvailable) return
        if (!loaded) {
            RootShell.async("cat $CONF 2>/dev/null") { xml ->
                if (xml != null && SoftApParser.parse(xml, ap)) loaded = true
                invalidate()
            }
        }
        refreshClients(force = true)
    }

    /** Root okumasi asenkron: ana thread'de bloke okuma arayuzu dondururdu. */
    private fun refreshClients(force: Boolean) {
        if (!snapshot.rootAvailable) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastClientsAt < CLIENT_REFRESH_MS) return
        lastClientsAt = now
        RootShell.async("cat /proc/net/arp") { arp ->
            if (arp != null) {
                ArpParser.parse(arp, "br0", clients)
                invalidate()
            }
        }
    }

    override fun draw(c: Canvas, s: Snapshot) {
        Geom.centerText(c, str(R.string.wifi_title), 18f, title)
        Geom.hairline(c, 32f, 40f, hair)

        if (!s.rootAvailable) {
            Geom.centerText(c, str(R.string.no_root), 110f, hint)
            return
        }
        if (!loaded) {
            Geom.centerText(c, str(R.string.wifi_unreadable), 110f, hint)
            return
        }

        refreshClients(force = false)

        Geom.centerText(c, str(R.string.wifi_network), 44f, label)
        Geom.centerText(c, ap.ssid, 56f, ssidPaint)

        Geom.centerText(c, str(R.string.wifi_password), 84f, label)
        Geom.centerText(c, ap.passphrase, 96f, passPaint)

        Geom.hairline(c, 124f, 74f, hair)

        sb.setLength(0)
        sb.append(str(R.string.wifi_connected_count))
        sb.append("  ")
        sb.append(clients.size)
        if (ap.maxClients != Snapshot.UNKNOWN) {
            sb.append(" / ")
            sb.append(ap.maxClients)
        }
        Geom.centerText(c, sb, 132f, label)

        // En fazla uc istemci sigar; fazlasi sayidan zaten anlasiliyor
        var y = 148f
        var shown = 0
        for (cl in clients) {
            if (shown >= 3) break
            Geom.centerText(c, cl.ip, y, clientPaint)
            y += 20f
            shown++
        }
        if (clients.isEmpty()) {
            Geom.centerText(c, str(R.string.wifi_no_clients), 152f, hint)
        }
    }
}
