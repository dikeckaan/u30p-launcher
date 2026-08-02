package com.kaandikec.u30plauncher.ui.theme

import android.graphics.Canvas
import com.kaandikec.u30plauncher.core.Fmt
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.ui.Geom
import com.kaandikec.u30plauncher.ui.Palette
import com.kaandikec.u30plauncher.ui.UiStrings

/**
 * Sekiz alan: saat ve pil ustte, operator ve sinyal ortada, trafik sayaclari
 * altta. Mevcut dashboard'a en yakin secenek ama cok daha seyrek.
 */
object BalancedTheme : Theme {
    private val clock = ThemeUtil.text(11f, Palette.DIM)
    private val operator = ThemeUtil.text(15f, Palette.FG)
    private val meta = ThemeUtil.text(11f, Palette.DIM)
    private val down = ThemeUtil.text(28f, Palette.DOWN)
    private val up = ThemeUtil.text(15f, Palette.UP)
    private val downUnit = ThemeUtil.text(10f, Palette.DOWN)
    private val label = ThemeUtil.text(9f, Palette.DIM2)
    private val value = ThemeUtil.text(14f, Palette.FG)
    private val barOn = ThemeUtil.fill(Palette.FG)
    private val barOff = ThemeUtil.fill(Palette.TRACK)
    private val downFill = ThemeUtil.fill(Palette.DOWN)
    private val upFill = ThemeUtil.fill(Palette.UP)
    private val battOutline = ThemeUtil.stroke(Palette.DIM, 1f)
    private val battFill = ThemeUtil.fill(Palette.DOWN)
    private val hair = ThemeUtil.hairline(Palette.HAIRLINE)

    override fun draw(c: Canvas, s: Snapshot, sb: StringBuilder, t: UiStrings) {
        // ust satir: dar bant, saat + pil birlikte ortalanir
        sb.setLength(0)
        ThemeUtil.appendClock(sb, s.clockMinuteOfDay)
        val tw = Geom.textWidth(sb, clock)
        val x0 = Geom.CX - (tw + 10f + Geom.BATT_W) / 2f
        Geom.textAt(c, sb, x0, 22f, clock)
        battFill.color = Palette.batteryColor(s.batteryPct)
        Geom.battery(c, x0 + tw + 10f, 23f, s.batteryPct, battOutline, battFill)

        sb.setLength(0)
        ThemeUtil.appendOperator(sb, s, t)
        Geom.centerText(c, sb, 44f, operator)

        // sinyal + teknoloji + bant + RSRP
        sb.setLength(0)
        sb.append(if (s.netType.isNotEmpty()) s.netType else "—")
        if (s.band != Snapshot.UNKNOWN) {
            sb.append(" B")
            sb.append(s.band)
        }
        sb.append("  •  ")
        if (s.rsrp == Snapshot.UNKNOWN) sb.append('—') else {
            sb.append(s.rsrp)
            sb.append(" dBm")
        }
        val textW = Geom.textWidth(sb, meta)
        val sx = Geom.CX - (Geom.BARS_W + 8f + textW) / 2f
        Geom.signalBars(c, sx, 64f, s.signalLevel, barOn, barOff)
        Geom.textAt(c, sb, sx + Geom.BARS_W + 8f, 66f, meta)

        Geom.hairline(c, 88f, 74f, hair)

        // Her deger kendi birimiyle
        sb.setLength(0)
        Fmt.appendSpeedValue(sb, s.rxSpeed)
        var w = Geom.textWidth(sb, down)
        val rxUnit = Fmt.speedUnit(s.rxSpeed)
        val rxUnitW = Geom.textWidth(rxUnit, downUnit)
        var x = Geom.CX - (18f + w + 4f + rxUnitW) / 2f
        Geom.arrow(c, x + 6f, 110f, 15f, true, downFill)
        Geom.textAt(c, sb, x + 18f, 98f, down)
        Geom.textAt(c, rxUnit, x + 18f + w + 4f, 112f, downUnit)

        sb.setLength(0)
        Fmt.appendSpeedValue(sb, s.txSpeed)
        sb.append(' ')
        sb.append(Fmt.speedUnit(s.txSpeed))
        w = Geom.textWidth(sb, up)
        x = Geom.CX - (w + 14f) / 2f
        Geom.arrow(c, x + 5f, 141f, 11f, false, upFill)
        Geom.textAt(c, sb, x + 14f, 134f, up)

        Geom.hairline(c, 162f, 74f, hair)

        sb.setLength(0)
        Fmt.appendBytes(sb, s.todayBytes)
        Geom.kvCell(c, Geom.COL_L, 172f, t.today, sb, label, value)

        sb.setLength(0)
        Fmt.appendBytes(sb, s.monthBytes)
        Geom.kvCell(c, Geom.COL_R, 172f, t.thisMonth, sb, label, value)
    }
}
