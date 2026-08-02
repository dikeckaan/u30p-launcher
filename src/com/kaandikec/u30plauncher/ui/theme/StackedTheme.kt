package com.kaandikec.u30plauncher.ui.theme

import android.graphics.Canvas
import android.graphics.Paint
import com.kaandikec.u30plauncher.core.Fmt
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.ui.Geom
import com.kaandikec.u30plauncher.ui.Palette
import com.kaandikec.u30plauncher.ui.UiStrings

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
    private val pctPaint = ThemeUtil.text(12f, Palette.FG)
    private val estPaint = ThemeUtil.text(10f, Palette.DIM)

    override fun draw(c: Canvas, s: Snapshot, sb: StringBuilder, t: UiStrings) {
        sb.setLength(0)
        ThemeUtil.appendClock(sb, s.clockMinuteOfDay)
        Geom.centerText(c, sb, 26f, clock)

        sb.setLength(0)
        ThemeUtil.appendOperator(sb, s, t)
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
        drawSpeed(c, sb, 100f, s.rxSpeed, down, downUnit, true)
        drawSpeed(c, sb, 144f, s.txSpeed, up, upUnit, false)

        drawBottomStrip(c, s, sb, t)
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

    /**
     * Alt alan iki satir:
     *   1) pil ikonu + yuzde + kalan sure
     *   2) VPN kalkani + istemci sayisi (yalnizca varsa)
     *
     * Once hepsi tek satirdaydi ve alttaki kilit gostergesiyle birlikte
     * sikisik duruyordu; yuzde ve kalan sure eklenince tek satira sigmiyordu.
     */
    private fun drawBottomStrip(c: Canvas, s: Snapshot, sb: StringBuilder, t: UiStrings) {
        drawBatteryRow(c, s, sb, 186f, t)

        val showVpn = s.vpnUp
        val showClients = s.clients > 0
        if (!showVpn && !showClients) return

        var width = 0f
        if (showVpn) width += 11f
        if (showVpn && showClients) width += 7f
        if (showClients) width += 9f
        var x = Geom.CX - width / 2f
        val y = 206f

        if (showVpn) {
            Geom.shield(c, x, y, 11f, shieldPaint)
            x += 18f
        }
        if (showClients) {
            sb.setLength(0)
            sb.append(s.clients)
            Geom.textAt(c, sb, x, y + 1f, small)
        }
    }

    /** Pil ikonu + yuzde + kalan sure, birlikte ortalanir. */
    private fun drawBatteryRow(c: Canvas, s: Snapshot, sb: StringBuilder, y: Float, t: UiStrings) {
        sb.setLength(0)
        if (s.batteryPct == Snapshot.UNKNOWN) sb.append(t.unknown) else {
            sb.append(s.batteryPct)
            sb.append('%')
        }
        val pctW = Geom.textWidth(sb, pctPaint)

        // Kalan sure yalnizca desarjda anlamli; sarjdayken yerine sarj isareti
        val estimate = StringBuilder(8)
        if (s.batteryCharging) estimate.append(t.charging)
        else if (s.batteryMinutesLeft != Snapshot.UNKNOWN) {
            Fmt.appendDuration(estimate, s.batteryMinutesLeft, t.hourShort, t.minuteShort)
        }
        val estW = if (estimate.isEmpty()) 0f else Geom.textWidth(estimate, estPaint) + 8f

        val total = Geom.BATT_W + 5f + pctW + estW
        var x = Geom.CX - total / 2f

        battFill.color = Palette.batteryColor(s.batteryPct)
        x += Geom.battery(c, x, y, s.batteryPct, battOutline, battFill) + 5f
        Geom.textAt(c, sb, x, y - 1f, pctPaint)

        if (estimate.isNotEmpty()) {
            Geom.textAt(c, estimate, x + pctW + 8f, y + 1f, estPaint)
        }
    }
}
