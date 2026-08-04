package com.kaandikec.u30plauncher.ui

import android.content.Context
import android.graphics.Canvas
import com.kaandikec.u30plauncher.core.Fmt
import com.kaandikec.u30plauncher.R
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.ui.theme.ThemeUtil

/** Sayfa 2: trafik sayaclari, istemci, VPN, sicakliklar. */
class DetailPage(ctx: Context) : PageView(ctx) {
    private val sb = StringBuilder(24)
    private val title = ThemeUtil.text(10f, Palette.DIM2)
    private val label = ThemeUtil.text(9f, Palette.DIM2)
    private val value = ThemeUtil.text(14f, Palette.FG)
    private val valueGreen = ThemeUtil.text(14f, Palette.DOWN)
    private val hair = ThemeUtil.hairline(Palette.HAIRLINE)

    override fun draw(c: Canvas, s: Snapshot) {
        Geom.centerText(c, str(R.string.detail_title), 22f, title)
        Geom.hairline(c, 40f, 50f, hair)

        sb.setLength(0)
        Fmt.appendBytes(sb, s.todayBytes)
        Geom.kvCell(c, Geom.COL_L, 54f, str(R.string.today), sb, label, value)

        sb.setLength(0)
        Fmt.appendBytes(sb, s.monthBytes)
        Geom.kvCell(
            c, Geom.COL_R, 54f,
            str(if (s.cycleDay == 1) R.string.this_month else R.string.this_period),
            sb, label, value
        )

        sb.setLength(0)
        if (s.clients < 0) sb.append('—') else sb.append(s.clients)
        Geom.kvCell(c, Geom.COL_L, 102f, str(R.string.clients), sb, label, value)

        Geom.centerText(c, str(R.string.vpn), 102f, label, Geom.COL_R)
        Geom.centerText(
            c, str(if (s.vpnUp) R.string.connected else R.string.disconnected), 115f,
            if (s.vpnUp) valueGreen else value, Geom.COL_R
        )

        sb.setLength(0)
        appendTemp(sb, s.cpuTempC)
        Geom.kvCell(c, Geom.COL_L, 150f, str(R.string.soc_temp), sb, label, value)

        sb.setLength(0)
        appendTemp(sb, s.batteryTempC)
        Geom.kvCell(c, Geom.COL_R, 150f, "PIL", sb, label, value)
    }

    /** ondabir °C -> "35.0°" */
    private fun appendTemp(sb: StringBuilder, t: Int) {
        if (t == Snapshot.UNKNOWN) {
            sb.append('—')
            return
        }
        ThemeUtil.appendTenths(sb, t)
        sb.append('°')
    }
}
