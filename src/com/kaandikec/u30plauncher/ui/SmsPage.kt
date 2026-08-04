package com.kaandikec.u30plauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.kaandikec.u30plauncher.R
import com.kaandikec.u30plauncher.core.SmsText
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.source.SmsSource
import com.kaandikec.u30plauncher.ui.theme.ThemeUtil

/**
 * Gelen kutusu: gonderen + kisa tarih + govdenin ilk iki satiri; bir mesaja
 * dokununca ayni sayfada tam metin.
 *
 * Detay ayri bir sayfa/mod degil: cihazda geri tusu YOK ve geri hareketi de
 * kapali, dolayisiyla her yeni mod kendi cikisini cizmek zorunda. Tek sayfada
 * kalinca cikis tek bir X'e iniyor ve yatay kaydirma butun sayfa icin ayni
 * anlamda calismaya devam ediyor.
 *
 * Satir metinleri LISTE KURULURKEN bir kez bolunup saklanir; cizimde yalnizca
 * hazir String'ler cizilir. Bolme sirasinda Paint olcumu yapiliyor, bunu her
 * karede tekrarlamak 30 Hz'de gorunur maliyet.
 */
class SmsPage(ctx: Context) : PageView(ctx) {

    companion object {
        /**
         * Yatay sinirlar. Yuvarlak ekranda en dar nokta gorunur alanin ALT
         * kenari (y=204): orada daire x=34..206 arasi; 44..196 her satir
         * yuksekliginde guvenli kaliyor.
         */
        private const val LEFT = 44f
        private const val RIGHT = 196f

        private const val TOP = 34f
        private const val BOTTOM = 204f

        private const val ROW_H = 44f
        private const val PREVIEW_H = 11f

        private const val DETAIL_TOP = 52f
        private const val DETAIL_LINE_H = 12f

        /** Bes parcali birlesik bir mesaj bile buraya sigar. */
        private const val DETAIL_MAX_LINES = 60

        /** Alt dugme: listede tazele, detayda kapat. */
        private const val BTN_CY = 214f
        private const val BTN_R = 7f
        private const val BTN_HIT_X = 26f
    }

    private val title = ThemeUtil.text(10f, Palette.DIM2)
    private val sender = ThemeUtil.text(11f, Palette.FG)
    private val stamp = ThemeUtil.text(9f, Palette.DIM2)
    private val preview = ThemeUtil.text(9.5f, Palette.DIM)
    private val bodyPaint = ThemeUtil.text(10f, Palette.FG)
    private val hint = ThemeUtil.text(9f, Palette.DIM2)
    private val hair = ThemeUtil.hairline(Palette.HAIRLINE)
    private val marker = ThemeUtil.fill(Palette.DOWN)
    private val unreadDot = ThemeUtil.fill(Palette.DOWN)
    private val highlight = ThemeUtil.fill(0x14FFFFFF)
    private val glyph = ThemeUtil.stroke(Palette.DIM, 2f)
    private val glyphFill = ThemeUtil.fill(Palette.DIM)
    private val arcRect = RectF()

    private val source = SmsSource(ctx)
    private val cal = java.util.Calendar.getInstance()
    private val sb = StringBuilder(64)

    /** Cizimde kullanilan hazir metinler; `rebuildCache` doldurur. */
    private val senders = ArrayList<String>(SmsSource.LIMIT)
    private val stamps = ArrayList<String>(SmsSource.LIMIT)
    private val line1 = ArrayList<String>(SmsSource.LIMIT)
    private val line2 = ArrayList<String>(SmsSource.LIMIT)
    private val stampW = FloatArray(SmsSource.LIMIT)
    private val unread = BooleanArray(SmsSource.LIMIT)

    private val detailLines = ArrayList<String>(DETAIL_MAX_LINES)
    private val wrapOut = ArrayList<String>(4)
    private var detailIndex = -1
    private var detailSender = ""
    private var detailStamp = ""

    private val senderMeasure = PaintMeasurer(sender)
    private val previewMeasure = PaintMeasurer(preview)
    private val bodyMeasure = PaintMeasurer(bodyPaint)

    private var scrollY = 0f
    private var maxScroll = 0f
    private var listScroll = 0f
    private var downY = 0f
    private var downScroll = 0f
    private var dragging = false
    private var pressed = -1
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    override val showDots: Boolean get() = false
    override val wantsRawTouch: Boolean get() = true

    /** `SmsText.Measurer`'i bir Paint'e baglar; metin boyu basina bir ornek. */
    private class PaintMeasurer(private val p: Paint) : SmsText.Measurer {
        override fun width(s: CharSequence, start: Int, end: Int): Float =
            p.measureText(s, start, end)
    }

    /**
     * Sayfa gosterilirken cagrilir.
     *
     * Gelen kutusu saniyede bir degismiyor; okunmus bir liste korunur, yalnizca
     * hic okunamamissa yeniden denenir. Elle tazeleme alttaki dugmede.
     */
    fun refreshState() {
        closeDetail()
        if (source.state != SmsSource.STATE_READY) reload()
    }

    private fun reload() {
        source.load {
            rebuildCache()
            invalidate()
        }
    }

    // ------------------------------------------------------------ onbellek

    private fun rebuildCache() {
        senders.clear(); stamps.clear(); line1.clear(); line2.clear()
        val list = source.messages
        val now = System.currentTimeMillis()
        val n = if (list.size > SmsSource.LIMIT) SmsSource.LIMIT else list.size
        for (i in 0 until n) {
            val m = list[i]
            unread[i] = m.unread

            sb.setLength(0)
            SmsText.appendStamp(sb, m.dateMs, now, cal)
            val st = sb.toString()
            stamps.add(st)
            stampW[i] = stamp.measureText(st)

            sb.setLength(0)
            if (m.address.isEmpty()) sb.append(str(R.string.sms_unknown_sender))
            else sb.append(m.address)
            SmsText.wrap(sb, senderMeasure, RIGHT - LEFT - stampW[i] - 8f, 1, wrapOut)
            senders.add(if (wrapOut.isEmpty()) "" else wrapOut[0])

            // Onizleme duzlestirilir; govdedeki sert satir sonu iki satirin
            // birini bos birakiyordu
            sb.setLength(0)
            SmsText.appendFlattened(sb, m.body, 240)
            SmsText.wrap(sb, previewMeasure, RIGHT - LEFT, 2, wrapOut)
            line1.add(if (wrapOut.size > 0) wrapOut[0] else "")
            line2.add(if (wrapOut.size > 1) wrapOut[1] else "")
        }
        pressed = -1
        if (detailIndex >= senders.size) closeDetail()
        if (detailIndex < 0) applyScrollLimit(senders.size * ROW_H)
    }

    private fun applyScrollLimit(contentH: Float) {
        val viewport = BOTTOM - TOP
        maxScroll = if (contentH > viewport) contentH - viewport else 0f
        if (scrollY > maxScroll) scrollY = maxScroll
    }

    // ------------------------------------------------------------ cizim

    override fun draw(c: Canvas, s: Snapshot) {
        if (detailIndex >= 0) {
            drawDetail(c)
            return
        }
        Geom.centerText(c, str(R.string.sms_title), 16f, title)
        Geom.hairline(c, 30f, 60f, hair)

        if (senders.isEmpty()) {
            Geom.centerText(c, statusText(), 110f, hint)
            drawRefresh(c)
            return
        }

        c.save()
        c.clipRect(0f, TOP, Geom.W, BOTTOM)
        for (i in 0 until senders.size) {
            val y = TOP + i * ROW_H - scrollY
            if (y > BOTTOM || y + ROW_H < TOP) continue
            drawRow(c, i, y)
        }
        c.restore()

        drawScrollbar(c)
        drawRefresh(c)
    }

    private fun drawRow(c: Canvas, i: Int, y: Float) {
        if (i == pressed) {
            c.drawRoundRect(38f, y - 1f, 202f, y + ROW_H - 5f, 8f, 8f, highlight)
        }
        if (unread[i]) c.drawCircle(38f, y + 5f, 2.5f, unreadDot)

        Geom.textAt(c, senders[i], LEFT, y, sender)
        Geom.textAt(c, stamps[i], RIGHT - stampW[i], y + 2f, stamp)
        Geom.textAt(c, line1[i], LEFT, y + 15f, preview)
        Geom.textAt(c, line2[i], LEFT, y + 15f + PREVIEW_H, preview)
        Geom.hairline(c, y + ROW_H - 4f, 76f, hair)
    }

    private fun drawDetail(c: Canvas) {
        Geom.centerText(c, detailSender, 14f, sender)
        Geom.centerText(c, detailStamp, 30f, stamp)
        Geom.hairline(c, 46f, 70f, hair)

        c.save()
        c.clipRect(0f, DETAIL_TOP, Geom.W, BOTTOM)
        for (i in 0 until detailLines.size) {
            val y = DETAIL_TOP + i * DETAIL_LINE_H - scrollY
            if (y > BOTTOM || y + DETAIL_LINE_H < DETAIL_TOP) continue
            Geom.textAt(c, detailLines[i], LEFT, y, bodyPaint)
        }
        c.restore()

        drawScrollbar(c)
        drawClose(c)
    }

    private fun drawScrollbar(c: Canvas) {
        if (maxScroll <= 0f) return
        val trackTop = TOP + 6f
        val trackH = (BOTTOM - 6f) - trackTop
        val viewport = BOTTOM - TOP
        val contentH = viewport + maxScroll
        val thumbH = (trackH * viewport / contentH).coerceAtLeast(12f)
        val top = trackTop + (trackH - thumbH) * (scrollY / maxScroll)
        c.drawRoundRect(202f, top, 205f, top + thumbH, 1.5f, 1.5f, marker)
    }

    /** Dairesel ok: listeyi yeniden okumanin tek gorunur yolu. */
    private fun drawRefresh(c: Canvas) {
        arcRect.set(Geom.CX - BTN_R, BTN_CY - BTN_R, Geom.CX + BTN_R, BTN_CY + BTN_R)
        c.drawArc(arcRect, -40f, 300f, false, glyph)
        Geom.arrow(c, Geom.CX + BTN_R * 0.77f, BTN_CY - BTN_R * 0.64f, 6f, false, glyphFill)
    }

    /** Detaydan cikisin tek yolu; cihazda geri tusu yok. */
    private fun drawClose(c: Canvas) {
        val r = BTN_R
        c.drawLine(Geom.CX - r, BTN_CY - r, Geom.CX + r, BTN_CY + r, glyph)
        c.drawLine(Geom.CX + r, BTN_CY - r, Geom.CX - r, BTN_CY + r, glyph)
    }

    private fun statusText(): String = when (source.state) {
        SmsSource.STATE_DENIED -> str(R.string.sms_no_permission)
        SmsSource.STATE_FAILED -> str(R.string.sms_unreadable)
        SmsSource.STATE_READY -> str(R.string.sms_empty)
        else -> str(R.string.sms_loading)
    }

    // ------------------------------------------------------------ dokunma

    override fun canScrollVertically(fingerDown: Boolean): Boolean =
        if (fingerDown) scrollY > 0.5f else scrollY < maxScroll - 0.5f

    private fun rowAt(y: Float): Int {
        if (y < TOP || y > BOTTOM) return -1
        val idx = ((y - TOP + scrollY) / ROW_H).toInt()
        return if (idx in 0 until senders.size) idx else -1
    }

    private fun inButton(x: Float, y: Float): Boolean =
        y > BOTTOM && Math.abs(x - Geom.CX) < BTN_HIT_X

    override fun onRawTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                downScroll = scrollY
                dragging = false
                pressed = if (detailIndex >= 0) -1 else rowAt(event.y)
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - downY
                if (!dragging && Math.abs(dy) > slop) {
                    dragging = true
                    pressed = -1
                }
                if (dragging) {
                    scrollY = (downScroll - dy).coerceIn(0f, maxScroll)
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!dragging) {
                    if (inButton(event.x, event.y)) {
                        if (detailIndex >= 0) closeDetail() else reload()
                    } else if (pressed >= 0 && pressed == rowAt(event.y)) {
                        openDetail(pressed)
                    }
                }
                pressed = -1
                dragging = false
                invalidate()
            }

            MotionEvent.ACTION_CANCEL -> {
                pressed = -1
                dragging = false
                invalidate()
            }
        }
    }

    private fun openDetail(i: Int) {
        val list = source.messages
        if (i >= list.size) return
        val m = list[i]

        sb.setLength(0)
        if (m.address.isEmpty()) sb.append(str(R.string.sms_unknown_sender))
        else sb.append(m.address)
        // Baslik ortali ve ekranin ust dar bandinda; liste genisligi buraya
        // sigmiyor
        SmsText.wrap(sb, senderMeasure, 120f, 1, wrapOut)
        detailSender = if (wrapOut.isEmpty()) "" else wrapOut[0]

        sb.setLength(0)
        SmsText.appendFullStamp(sb, m.dateMs, cal)
        detailStamp = sb.toString()

        SmsText.wrap(m.body, bodyMeasure, RIGHT - LEFT, DETAIL_MAX_LINES, detailLines)

        detailIndex = i
        listScroll = scrollY
        scrollY = 0f
        applyScrollLimit(detailLines.size * DETAIL_LINE_H)
        invalidate()
    }

    private fun closeDetail() {
        if (detailIndex < 0) return
        detailIndex = -1
        detailLines.clear()
        scrollY = listScroll
        applyScrollLimit(senders.size * ROW_H)
        invalidate()
    }
}
