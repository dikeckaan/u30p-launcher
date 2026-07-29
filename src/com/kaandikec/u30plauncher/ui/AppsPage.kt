package com.kaandikec.u30plauncher.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.store.AppUsage
import com.kaandikec.u30plauncher.ui.theme.ThemeUtil

/**
 * Uygulama izgarasi: satir basina 3 simge, ekran basina 9 uygulama.
 *
 * Neden izgara: 240x240'ta dikey liste ekran basina 5 satir gosteriyordu ve
 * 24 uygulamanin sonuna inmek bes kaydirma aliyordu. Izgarada iki kaydirmada
 * hepsi geziliyor, simge zaten etiketten hizli taniniyor.
 *
 * Siralama en cok acilana gore: bir MiFi'de kurulu uygulamalarin cogu hic
 * acilmaz, alfabetik siralama gunluk kullanilani dibe atiyordu.
 */
class AppsPage(ctx: Context) : PageView(ctx) {
    companion object {
        private const val COLS = 3
        private const val CELL_W = 58f
        private const val CELL_H = 60f
        private const val ICON = 36
        private const val TOP = 30f
        private const val VISIBLE_BOTTOM = 226f
    }

    private val label = ThemeUtil.text(8.5f, Palette.DIM).apply {
        textAlign = Paint.Align.CENTER
    }
    private val hint = ThemeUtil.text(9f, Palette.DIM2)
    private val marker = ThemeUtil.fill(Palette.DOWN)
    private val iconPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val highlight = ThemeUtil.fill(0x1FFFFFFF)

    private val usage = AppUsage(ctx)
    private val labels = ArrayList<String>(32)
    private val icons = ArrayList<Bitmap?>(32)
    private val intents = ArrayList<Intent>(32)
    private val keys = ArrayList<String>(32)

    private val iconSrc = Rect()
    private val iconDst = Rect()

    private var scrollY = 0f
    private var maxScroll = 0f
    private var downY = 0f
    private var downScroll = 0f
    private var dragging = false
    private var pressed = -1
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    override val showDots: Boolean get() = false
    override val wantsRawTouch: Boolean get() = true

    /** Acilista bir kez kurulur; siralama her acilista tazelenir. */
    fun ensureLoaded() {
        if (labels.isEmpty()) load()
        else resort()
    }

    private fun load() {
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found: List<ResolveInfo> = try {
            pm.queryIntentActivities(probe, 0)
        } catch (_: Throwable) {
            return
        }

        val rows = ArrayList<Entry>(found.size)
        for (ri in found) {
            val ai = ri.activityInfo ?: continue
            if (ai.packageName == context.packageName) continue
            val name = try {
                ri.loadLabel(pm)?.toString() ?: ai.packageName
            } catch (_: Throwable) {
                ai.packageName
            }
            val icon = try {
                rasterize(ri.loadIcon(pm))
            } catch (_: Throwable) {
                null
            }
            val key = ai.packageName + "/" + ai.name
            val i = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(ai.packageName, ai.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            rows.add(Entry(name, icon, i, key))
        }
        entries = rows
        resort()
    }

    private class Entry(
        val label: String,
        val icon: Bitmap?,
        val intent: Intent,
        val key: String
    )

    private var entries: List<Entry> = emptyList()

    /** Cok acilan uste; esitlikte alfabetik. */
    private fun resort() {
        val sorted = entries.sortedWith(
            compareByDescending<Entry> { usage.count(it.key) }.thenBy { it.label.lowercase() }
        )
        labels.clear(); icons.clear(); intents.clear(); keys.clear()
        for (e in sorted) {
            labels.add(e.label); icons.add(e.icon); intents.add(e.intent); keys.add(e.key)
        }
        val rowCount = (labels.size + COLS - 1) / COLS
        val content = rowCount * CELL_H
        val viewport = VISIBLE_BOTTOM - TOP
        maxScroll = if (content > viewport) content - viewport else 0f
        if (scrollY > maxScroll) scrollY = maxScroll
        invalidate()
    }

    private fun rasterize(d: android.graphics.drawable.Drawable?): Bitmap? {
        d ?: return null
        val bmp = Bitmap.createBitmap(ICON, ICON, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, ICON, ICON)
        d.draw(Canvas(bmp))
        return bmp
    }

    private fun cellCx(col: Int): Float = Geom.CX + (col - 1) * CELL_W

    override fun draw(c: Canvas, s: Snapshot) {
        if (labels.isEmpty()) {
            Geom.centerText(c, "liste bos", 110f, hint)
            return
        }

        c.save()
        c.clipRect(0f, TOP, Geom.W, VISIBLE_BOTTOM)
        for (i in labels.indices) {
            val row = i / COLS
            val col = i % COLS
            val y = TOP + row * CELL_H - scrollY
            if (y > VISIBLE_BOTTOM || y + CELL_H < TOP) continue
            drawCell(c, i, cellCx(col), y)
        }
        c.restore()

        drawScrollbar(c)
    }

    private fun drawCell(c: Canvas, i: Int, cx: Float, y: Float) {
        if (i == pressed) {
            c.drawRoundRect(cx - 26f, y + 1f, cx + 26f, y + CELL_H - 5f, 10f, 10f, highlight)
        }
        val icon = icons[i]
        if (icon != null) {
            iconSrc.set(0, 0, icon.width, icon.height)
            val left = (cx - ICON / 2f).toInt()
            val top = (y + 6f).toInt()
            iconDst.set(left, top, left + ICON, top + ICON)
            c.drawBitmap(icon, iconSrc, iconDst, iconPaint)
        }
        // Etiket hucreye sigmiyorsa kirp; ... eklemek 8.5px'te yer kaybettiriyor
        val name = labels[i]
        var text: CharSequence = name
        if (Geom.textWidth(name, label) > CELL_W - 4f) {
            var n = name.length
            while (n > 1 && Geom.textWidth(name.substring(0, n), label) > CELL_W - 4f) n--
            text = name.substring(0, n)
        }
        c.drawText(text, 0, text.length, cx, y + ICON + 17f, label)
    }

    private fun drawScrollbar(c: Canvas) {
        if (maxScroll <= 0f) return
        val trackTop = TOP + 4f
        val trackH = (VISIBLE_BOTTOM - 4f) - trackTop
        val viewport = VISIBLE_BOTTOM - TOP
        val rowCount = (labels.size + COLS - 1) / COLS
        val thumbH = (trackH * viewport / (rowCount * CELL_H)).coerceAtLeast(14f)
        val top = trackTop + (trackH - thumbH) * (scrollY / maxScroll)
        c.drawRoundRect(232f, top, 235f, top + thumbH, 1.5f, 1.5f, marker)
    }

    private fun cellAt(x: Float, y: Float): Int {
        if (y < TOP || y > VISIBLE_BOTTOM) return -1
        val row = ((y - TOP + scrollY) / CELL_H).toInt()
        var col = -1
        for (k in 0 until COLS) if (Math.abs(x - cellCx(k)) < CELL_W / 2f) col = k
        if (col < 0) return -1
        val idx = row * COLS + col
        return if (idx in labels.indices) idx else -1
    }

    override fun onRawTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                downScroll = scrollY
                dragging = false
                pressed = cellAt(event.x, event.y)
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
                if (!dragging && pressed >= 0 && pressed == cellAt(event.x, event.y)) {
                    launch(pressed)
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

    override fun canScrollVertically(fingerDown: Boolean): Boolean =
        if (fingerDown) scrollY > 0.5f else scrollY < maxScroll - 0.5f

    private fun launch(index: Int) {
        val intent = intents.getOrNull(index) ?: return
        try {
            context.startActivity(intent)
            usage.record(keys[index])
        } catch (_: Throwable) {
            labels.clear(); icons.clear(); intents.clear(); keys.clear()
            entries = emptyList()
            load()
        }
    }
}
