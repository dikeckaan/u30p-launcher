package com.kaandikec.u30plauncher.ui.theme

import android.graphics.Canvas
import com.kaandikec.u30plauncher.core.Fmt
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.ui.Geom
import com.kaandikec.u30plauncher.ui.Palette

/**
 * Cemberi sinyal gostergesi olarak kullanir; hero anlik indirme hizidir.
 *
 * Yay 7:30 yonunden (225°) baslar ve 270° suparer; doluluk sinyal seviyesinin
 * 0..4 araligina oranidir.
 */
object ArcTheme : Theme {
    private val track = ThemeUtil.stroke(Palette.TRACK, 5f).apply {
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    private val level = ThemeUtil.stroke(Palette.DOWN, 5f).apply {
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    private val operator = ThemeUtil.text(14f, Palette.FG)
    private val meta = ThemeUtil.text(11f, Palette.DIM)
    private val hero = ThemeUtil.text(52f, Palette.FG)
    private val unit = ThemeUtil.text(12f, Palette.DIM)
    private val upText = ThemeUtil.text(13f, Palette.UP)
    private val downFill = ThemeUtil.fill(Palette.DOWN)
    private val upFill = ThemeUtil.fill(Palette.UP)
    private val clock = ThemeUtil.text(13f, Palette.DIM)
    private val hair = ThemeUtil.hairline(Palette.HAIRLINE)
    private val battOutline = ThemeUtil.stroke(Palette.DIM, 1f)
    private val battFill = ThemeUtil.fill(Palette.DOWN)

    override fun draw(c: Canvas, s: Snapshot, sb: StringBuilder) {
        Geom.arcRing(c, 118f, 5f, 225f, 270f, track)
        val f = (s.signalLevel.coerceIn(0, 4)) / 4f
        Geom.arcRing(c, 118f, 5f, 225f, 270f * f, level)

        sb.setLength(0)
        ThemeUtil.appendOperator(sb, s)
        Geom.centerText(c, sb, 30f, operator)

        sb.setLength(0)
        sb.append(if (s.netType.isNotEmpty()) s.netType else "—")
        if (s.band != Snapshot.UNKNOWN) {
            sb.append("  B")
            sb.append(s.band)
        }
        Geom.centerText(c, sb, 47f, meta)

        sb.setLength(0)
        Fmt.appendSpeedValue(sb, s.rxSpeed)
        Geom.centerText(c, sb, 78f, hero)

        sb.setLength(0)
        sb.append(Fmt.speedUnit(s.rxSpeed))
        Geom.centerText(c, sb, 133f, unit)
        Geom.arrow(c, Geom.CX - 34f, 139f, 11f, true, downFill)

        sb.setLength(0)
        Fmt.appendSpeedValue(sb, s.txSpeed)
        sb.append(' ')
        sb.append(Fmt.speedUnit(s.txSpeed))
        val w = Geom.textWidth(sb, upText)
        val x = Geom.CX - (w + 12f) / 2f
        Geom.arrow(c, x + 5f, 162f, 10f, false, upFill)
        Geom.textAt(c, sb, x + 12f, 155f, upText)

        Geom.hairline(c, 180f, 50f, hair)

        sb.setLength(0)
        ThemeUtil.appendClock(sb, s.clockMinuteOfDay)
        val tw = Geom.textWidth(sb, clock)
        val bx = Geom.CX - (tw + 14f + Geom.BATT_W) / 2f
        Geom.textAt(c, sb, bx, 192f, clock)
        battFill.color = Palette.batteryColor(s.batteryPct)
        Geom.battery(c, bx + tw + 14f, 194f, s.batteryPct, battOutline, battFill)
    }
}
