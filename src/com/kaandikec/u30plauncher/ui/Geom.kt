package com.kaandikec.u30plauncher.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * 240x240 yuvarlak ekran icin geometri ve tekrar eden cizim parcalari.
 *
 * Yardimcilar cagiranin verdigi Paint'i kullanir; Path ve RectF ornekleri
 * burada bir kez ayrilip yeniden kullanilir, boylece cizim yolunda allocation
 * olmaz. Bu nedenle sinif tek is parcacigindan (UI thread) cagrilmalidir.
 */
object Geom {
    const val W = 240f
    const val H = 240f
    const val CX = 120f
    const val CY = 120f
    const val R = 120f

    /** Iki sutunlu izgaranin sutun merkezleri. */
    const val COL_L = 78f
    const val COL_R = 162f

    private val path = Path()
    private val rect = RectF()

    /** Merkezden dikey `dy` uzaklikta dairenin icinde kalan yari genislik. */
    fun halfWidthAt(dy: Float): Float {
        val d = if (dy < 0) -dy else dy
        if (d >= R) return 0f
        return Math.sqrt((R * R - d * d).toDouble()).toFloat()
    }

    /** `y` cap-top olacak sekilde yatay ortali metin. */
    fun centerText(c: Canvas, s: CharSequence, y: Float, p: Paint, cx: Float = CX) {
        val prev = p.textAlign
        p.textAlign = Paint.Align.CENTER
        c.drawText(s, 0, s.length, cx, y - p.fontMetrics.ascent, p)
        p.textAlign = prev
    }

    /** Sol hizali metin; `y` cap-top. */
    fun textAt(c: Canvas, s: CharSequence, x: Float, y: Float, p: Paint) {
        c.drawText(s, 0, s.length, x, y - p.fontMetrics.ascent, p)
    }

    fun textWidth(s: CharSequence, p: Paint): Float = p.measureText(s, 0, s.length)

    /** Etiket ustte, deger altta hucre. */
    fun kvCell(
        c: Canvas, cx: Float, y: Float,
        label: CharSequence, value: CharSequence,
        labelPaint: Paint, valuePaint: Paint
    ) {
        centerText(c, label, y, labelPaint, cx)
        centerText(c, value, y + 13f, valuePaint, cx)
    }

    const val BARS_W = 32f

    /** 5 cubuklu sinyal gostergesi. Toplam genisligi dondurur. */
    fun signalBars(c: Canvas, x: Float, y: Float, level: Int, on: Paint, off: Paint): Float {
        val bw = 4f
        val gap = 3f
        val hMin = 4f
        val hMax = 16f
        for (i in 0 until 5) {
            val h = hMin + (hMax - hMin) * i / 4f
            rect.set(x + i * (bw + gap), y + (hMax - h), x + i * (bw + gap) + bw, y + hMax)
            c.drawRoundRect(rect, 1.5f, 1.5f, if (i < level) on else off)
        }
        return BARS_W
    }

    const val BATT_W = 26f

    /** Pil govdesi + ucu. Toplam genisligi dondurur. */
    fun battery(c: Canvas, x: Float, y: Float, pct: Int, outline: Paint, fill: Paint): Float {
        val w = 22f
        val h = 11f
        rect.set(x, y, x + w, y + h)
        c.drawRoundRect(rect, 3f, 3f, outline)
        rect.set(x + w + 1f, y + h / 2f - 2.5f, x + w + 3f, y + h / 2f + 2.5f)
        c.drawRoundRect(rect, 1f, 1f, outline)
        val p = if (pct < 0) 0 else if (pct > 100) 100 else pct
        val inner = (w - 4f) * p / 100f
        if (inner > 0.5f) {
            rect.set(x + 2f, y + 2f, x + 2f + inner, y + h - 2f)
            c.drawRoundRect(rect, 1.5f, 1.5f, fill)
        }
        return BATT_W
    }

    /** Dolu ucgen ok; `cy` dikey merkez. */
    fun arrow(c: Canvas, cx: Float, cy: Float, size: Float, down: Boolean, p: Paint) {
        val w = size * 0.78f
        path.rewind()
        if (down) {
            path.moveTo(cx - w / 2f, cy - size / 2f)
            path.lineTo(cx + w / 2f, cy - size / 2f)
            path.lineTo(cx, cy + size / 2f)
        } else {
            path.moveTo(cx - w / 2f, cy + size / 2f)
            path.lineTo(cx + w / 2f, cy + size / 2f)
            path.lineTo(cx, cy - size / 2f)
        }
        path.close()
        c.drawPath(path, p)
    }

    /** Sayfa gostergesi noktalari. */
    fun dots(c: Canvas, count: Int, active: Int, y: Float, on: Paint, off: Paint) {
        if (count <= 1) return
        val gap = 9f
        val x0 = CX - (count - 1) * gap / 2f
        for (i in 0 until count) {
            val r = if (i == active) 2.2f else 1.6f
            c.drawCircle(x0 + i * gap, y, r, if (i == active) on else off)
        }
    }

    /** Kilit ipucu ikonu. */
    fun padlock(c: Canvas, x: Float, y: Float, s: Float, p: Paint) {
        rect.set(x + s * 0.22f, y, x + s * 0.78f, y + s * 0.9f)
        c.drawArc(rect, 180f, 180f, false, p)
        rect.set(x, y + s * 0.45f, x + s, y + s)
        c.drawRoundRect(rect, 1.5f, 1.5f, p)
    }

    /** VPN kalkani. */
    fun shield(c: Canvas, x: Float, y: Float, s: Float, p: Paint) {
        path.rewind()
        path.moveTo(x + s / 2f, y)
        path.lineTo(x + s, y + s * 0.26f)
        path.lineTo(x + s, y + s * 0.6f)
        path.lineTo(x + s / 2f, y + s)
        path.lineTo(x, y + s * 0.6f)
        path.lineTo(x, y + s * 0.26f)
        path.close()
        c.drawPath(path, p)
    }

    /** 12 yonunden saat yonunde olculen yay. */
    fun arcRing(c: Canvas, rOut: Float, width: Float, startDeg: Float, sweepDeg: Float, p: Paint) {
        if (sweepDeg <= 0f) return
        val inset = width / 2f
        rect.set(CX - rOut + inset, CY - rOut + inset, CX + rOut - inset, CY + rOut - inset)
        p.strokeWidth = width
        c.drawArc(rect, startDeg - 90f, sweepDeg, false, p)
    }

    /** Ince ayirici cizgi; genislik dairenin icinde kalacak sekilde kirpilir. */
    fun hairline(c: Canvas, y: Float, halfWidth: Float, p: Paint) {
        val limit = halfWidthAt(y - CY) - 4f
        val w = if (halfWidth > limit) limit else halfWidth
        if (w <= 0f) return
        c.drawLine(CX - w, y, CX + w, y, p)
    }
}
