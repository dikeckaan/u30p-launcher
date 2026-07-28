package com.kaandikec.u30plauncher.ui

import android.content.Context
import android.graphics.Canvas
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.ui.theme.ThemeUtil

/** Sayfa 3: hucre ve sinyal detaylari. */
class EngineeringPage(ctx: Context) : PageView(ctx) {
    private val sb = StringBuilder(24)
    private val title = ThemeUtil.text(10f, Palette.DIM2)
    private val label = ThemeUtil.text(9f, Palette.DIM2)
    private val value = ThemeUtil.text(14f, Palette.FG)
    private val hair = ThemeUtil.hairline(Palette.HAIRLINE)

    override fun draw(c: Canvas, s: Snapshot) {
        sb.setLength(0)
        sb.append(if (s.netType.isNotEmpty()) s.netType else "—")
        if (s.band != Snapshot.UNKNOWN) {
            sb.append("  B")
            sb.append(s.band)
        }
        if (s.bandwidthKhz != Snapshot.UNKNOWN) {
            sb.append("  •  ")
            sb.append(s.bandwidthKhz / 1000)
            sb.append(" MHz")
        }
        Geom.centerText(c, sb, 22f, title)
        Geom.hairline(c, 40f, 64f, hair)

        num(c, Geom.COL_L, 50f, "RSRP", s.rsrp)
        num(c, Geom.COL_R, 50f, "RSRQ", s.rsrq)

        sb.setLength(0)
        ThemeUtil.appendTenths(sb, s.sinr)
        Geom.kvCell(c, Geom.COL_L, 90f, "SINR", sb, label, value)
        num(c, Geom.COL_R, 90f, "PCI", s.pci)

        num(c, Geom.COL_L, 130f, "EARFCN", s.earfcn)
        num(c, Geom.COL_R, 130f, "TAC", s.tac)

        num(c, Geom.CX, 170f, "CI", s.ci)
    }

    private fun num(c: Canvas, cx: Float, y: Float, lab: String, v: Int) {
        sb.setLength(0)
        ThemeUtil.appendOrDash(sb, v)
        Geom.kvCell(c, cx, y, lab, sb, label, value)
    }
}
