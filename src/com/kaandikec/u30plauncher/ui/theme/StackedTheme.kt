package com.kaandikec.u30plauncher.ui.theme

import android.graphics.Canvas
import android.graphics.Paint
import com.kaandikec.u30plauncher.core.Fmt
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.ui.Geom
import com.kaandikec.u30plauncher.ui.Palette

/**
 * Varsayilan tema: sussuz bes satir, mumkun olan en buyuk tipografi.
 *
 * Tum Paint'ler burada bir kez ayrilir; `draw` icinde allocation yoktur.
 */
object StackedTheme : Theme {
    private val clock = ThemeUtil.text(13f, Palette.DIM2)
    private val operator = ThemeUtil.text(17f, Palette.FG)
    private val meta = ThemeUtil.text(11f, Palette.DIM)
    private val unit = ThemeUtil.text(11f, Palette.DIM2)
    private val small = ThemeUtil.text(10f, Palette.DIM)
    private val down = ThemeUtil.text(34f, Palette.DOWN)
    private val up = ThemeUtil.text(34f, Palette.UP)
    private val downUnit = ThemeUtil.text(11f, Palette.DOWN)
    private val upUnit = ThemeUtil.text(11f, Palette.UP)
    private val barOn = ThemeUtil.fill(Palette.FG)
    private val barOff = ThemeUtil.fill(Palette.TRACK)
    private val battOutline = ThemeUtil.stroke(Palette.DIM, 1f)
    private val battFill = ThemeUtil.fill(Palette.DOWN)
    private val shieldPaint = ThemeUtil.fill(Palette.DOWN)

    override fun draw(c: Canvas, s: Snapshot, sb: StringBuilder) {
        sb.setLength(0)
        ThemeUtil.appendClock(sb, s.clockMinuteOfDay)
        Geom.centerText(c, sb, 26f, clock)

        sb.setLength(0)
        ThemeUtil.appendOperator(sb, s)
        Geom.centerText(c, sb, 48f, operator)

        // sinyal cubugu + teknoloji + RSRP tek satirda, birlikte ortalanir
        sb.setLength(0)
        sb.append(if (s.netType.isNotEmpty()) s.netType else "—")
        sb.append("  •  ")
        if (s.rsrp == Snapshot.UNKNOWN) sb.append('—') else {
            sb.append(s.rsrp)
            sb.append(" dBm")
        }
        val textW = Geom.textWidth(sb, meta)
        val x0 = Geom.CX - (Geom.BARS_W + 8f + textW) / 2f
        Geom.signalBars(c, x0, 72f, s.signalLevel, barOn, barOff)
        Geom.textAt(c, sb, x0 + Geom.BARS_W + 8f, 74f, meta)

        // Her deger kendi birimini yaninda tasir: indirme bps iken yukleme
        // Mbps olabiliyor, tek ortak etiket ikisinden birini yanlis tanimlardi.
        drawSpeed(c, sb, 106f, s.rxSpeed, down, downUnit, true)
        drawSpeed(c, sb, 152f, s.txSpeed, up, upUnit, false)

        drawBottomStrip(c, s, sb)
    }

    /** ok + sayi + kendi birimi, birlikte ortalanir. */
    private fun drawSpeed(
        c: Canvas, sb: StringBuilder, y: Float, v: Long,
        numPaint: Paint, unitPaint: Paint, isDown: Boolean
    ) {
        sb.setLength(0)
        Fmt.appendSpeedValue(sb, v)
        val numW = Geom.textWidth(sb, numPaint)
        val unitText = Fmt.speedUnit(v)
        val unitW = Geom.textWidth(unitText, unitPaint)
        val total = 20f + numW + 5f + unitW
        val x = Geom.CX - total / 2f
        Geom.arrow(c, x + 7f, y + 13f, 18f, isDown, numPaint)
        Geom.textAt(c, sb, x + 20f, y, numPaint)
        // Birim, sayinin taban cizgisine yakin otursun
        Geom.textAt(c, unitText, x + 20f + numW + 5f, y + 17f, unitPaint)
    }

    /** Pil her zaman; VPN yalnizca bagliyken; istemci yalnizca > 0 iken. */
    private fun drawBottomStrip(c: Canvas, s: Snapshot, sb: StringBuilder) {
        val y = 202f
        val showVpn = s.vpnUp
        val showClients = s.clients > 0

        var width = Geom.BATT_W + 8f
        if (showVpn) width += 19f
        if (showClients) width += 18f

        var x = Geom.CX - width / 2f
        battFill.color = Palette.batteryColor(s.batteryPct)
        x += Geom.battery(c, x, y, s.batteryPct, battOutline, battFill) + 8f

        if (showVpn) {
            Geom.shield(c, x, y - 1f, 11f, shieldPaint)
            x += 19f
        }
        if (showClients) {
            sb.setLength(0)
            sb.append(s.clients)
            Geom.textAt(c, sb, x, y + 1f, small)
        }
    }
}
