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
import com.kaandikec.u30plauncher.ui.theme.ThemeUtil

/**
 * Kurulu uygulamalarin dikey listesi, simgeleriyle.
 *
 * Simgeler bir kez 24x24 bitmap'e rasterlestirilip onbellege alinir: Drawable'i
 * her karede olceklendirmek yerine hazir bitmap cizmek hem daha ucuz hem de
 * cizim yolunda allocation yapmaz. ~25 uygulama icin toplam maliyet ~55 KB.
 *
 * Kilit katmanindadir: bilgi sayfalari serbest gezilebilsin ama cihaz cepteyken
 * yanlislikla uygulama acilmasin.
 */
class AppsPage(ctx: Context) : PageView(ctx) {
    companion object {
        private const val ROW_H = 38f
        private const val TOP = 32f
        private const val VISIBLE_BOTTOM = 222f
        private const val ICON = 24
        private const val ICON_GAP = 10f
    }

    private val title = ThemeUtil.text(10f, Palette.DIM2)
    private val label = ThemeUtil.text(13f, Palette.FG)
    private val hint = ThemeUtil.text(9f, Palette.DIM2)
    private val hair = ThemeUtil.hairline(Palette.HAIRLINE)
    private val marker = ThemeUtil.fill(Palette.DOWN)
    private val iconPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val highlight = ThemeUtil.fill(0x14FFFFFF)

    private val labels = ArrayList<String>(32)
    private val icons = ArrayList<Bitmap?>(32)
    private val intents = ArrayList<Intent>(32)

    private val iconSrc = Rect()
    private val iconDst = Rect()

    private var scrollY = 0f
    private var maxScroll = 0f
    private var downY = 0f
    private var downScroll = 0f
    private var dragging = false
    private var pressedRow = -1
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    override val showDots: Boolean get() = false
    override val wantsRawTouch: Boolean get() = true

    /** Kilit acildiginda cagrilir; liste seyrek degistigi icin bir kez kurulur. */
    fun ensureLoaded() {
        if (labels.isNotEmpty()) return
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found: List<ResolveInfo> = try {
            pm.queryIntentActivities(probe, 0)
        } catch (_: Throwable) {
            return
        }

        val rows = ArrayList<Triple<String, Bitmap?, Intent>>(found.size)
        for (ri in found) {
            val ai = ri.activityInfo ?: continue
            if (ai.packageName == context.packageName) continue  // kendimizi listeleme
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
            val i = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(ai.packageName, ai.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            rows.add(Triple(name, icon, i))
        }
        rows.sortBy { it.first.lowercase() }

        labels.clear(); icons.clear(); intents.clear()
        for ((n, b, i) in rows) {
            labels.add(n); icons.add(b); intents.add(i)
        }

        val content = labels.size * ROW_H
        val viewport = VISIBLE_BOTTOM - TOP
        maxScroll = if (content > viewport) content - viewport else 0f
        scrollY = 0f
        invalidate()
    }

    /** Drawable'i bir kez sabit boyutlu bitmap'e cizer. */
    private fun rasterize(d: android.graphics.drawable.Drawable?): Bitmap? {
        d ?: return null
        val bmp = Bitmap.createBitmap(ICON, ICON, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, ICON, ICON)
        d.draw(c)
        return bmp
    }

    override fun draw(c: Canvas, s: Snapshot) {
        Geom.centerText(c, "UYGULAMALAR", 16f, title)
        Geom.hairline(c, 28f, 60f, hair)

        if (labels.isEmpty()) {
            Geom.centerText(c, "liste bos", 110f, hint)
            return
        }

        c.save()
        c.clipRect(0f, TOP, Geom.W, VISIBLE_BOTTOM)
        for (i in labels.indices) {
            val y = TOP + i * ROW_H - scrollY
            if (y > VISIBLE_BOTTOM || y + ROW_H < TOP) continue
            drawRow(c, i, y)
        }
        c.restore()

        drawScrollbar(c)
    }

    private fun drawRow(c: Canvas, i: Int, y: Float) {
        val name = labels[i]
        val icon = icons[i]
        val textW = Geom.textWidth(name, label)
        val total = ICON + ICON_GAP + textW
        // Satiri dairenin ortasina yasla; yuvarlak ekranda kenarlar dar
        val x = Geom.CX - total / 2f

        if (i == pressedRow) {
            c.drawRoundRect(x - 8f, y + 1f, x + total + 8f, y + ROW_H - 3f, 8f, 8f, highlight)
        }

        if (icon != null) {
            iconSrc.set(0, 0, icon.width, icon.height)
            val top = (y + (ROW_H - ICON) / 2f).toInt()
            iconDst.set(x.toInt(), top, x.toInt() + ICON, top + ICON)
            c.drawBitmap(icon, iconSrc, iconDst, iconPaint)
        }
        Geom.textAt(c, name, x + ICON + ICON_GAP, y + 11f, label)
    }

    /** Sag kenarda ince bir konum gostergesi. */
    private fun drawScrollbar(c: Canvas) {
        if (maxScroll <= 0f) return
        val trackTop = TOP + 4f
        val trackH = (VISIBLE_BOTTOM - 4f) - trackTop
        val viewport = VISIBLE_BOTTOM - TOP
        val content = labels.size * ROW_H
        val thumbH = (trackH * viewport / content).coerceAtLeast(14f)
        val top = trackTop + (trackH - thumbH) * (scrollY / maxScroll)
        val x = Geom.CX + Geom.halfWidthAt(0f) - 10f
        c.drawRoundRect(x, top, x + 3f, top + thumbH, 1.5f, 1.5f, marker)
    }

    private fun rowAt(y: Float): Int {
        if (y < TOP || y > VISIBLE_BOTTOM) return -1
        val idx = ((y - TOP + scrollY) / ROW_H).toInt()
        return if (idx in labels.indices) idx else -1
    }

    override fun onRawTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                downScroll = scrollY
                dragging = false
                pressedRow = rowAt(event.y)
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - downY
                if (!dragging && Math.abs(dy) > slop) {
                    dragging = true
                    pressedRow = -1
                }
                if (dragging) {
                    scrollY = (downScroll - dy).coerceIn(0f, maxScroll)
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!dragging && pressedRow >= 0 && pressedRow == rowAt(event.y)) {
                    launch(pressedRow)
                }
                pressedRow = -1
                dragging = false
                invalidate()
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedRow = -1
                dragging = false
                invalidate()
            }
        }
    }

    private fun launch(index: Int) {
        val intent = intents.getOrNull(index) ?: return
        try {
            context.startActivity(intent)
        } catch (_: Throwable) {
            // Kaldirilmis veya devre disi birakilmis uygulama: listeyi tazele
            labels.clear(); icons.clear(); intents.clear()
            ensureLoaded()
        }
    }
}
