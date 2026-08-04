package com.kaandikec.u30plauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import com.kaandikec.u30plauncher.R
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.store.Prefs
import com.kaandikec.u30plauncher.ui.theme.ThemeUtil

/**
 * Faturalama doneminin basladigi gunu secmek icin 1..28 izgarasi.
 *
 * Neden ayri bir sayfa: ayar satirina dokundukca degeri bir artirmak 28
 * secenekte kullanilamaz hale geliyordu (ortalama 14 dokunus). Izgarada tek
 * dokunusla seciliyor.
 *
 * 5 sutun x 6 satir = 30 hucre; son iki hucrenin biri iptal.
 */
class CycleDayPage(
    ctx: Context,
    private val prefs: Prefs,
    private val onPicked: () -> Unit,
    private val onCancel: () -> Unit
) : PageView(ctx) {

    companion object {
        private const val COLS = 5
        private const val ROWS = 6
        private val COL_X = floatArrayOf(50f, 85f, 120f, 155f, 190f)
        private val ROW_Y = floatArrayOf(58f, 88f, 118f, 148f, 178f, 208f)

        /** Dokunma yaricapi; hucre araligi 35x30 px. */
        private const val HIT_X = 17f
        private const val HIT_Y = 15f

        /** Iptal hucresi: son satirin son sutunu. */
        private const val CANCEL_INDEX = COLS * ROWS - 1
    }

    private val title = ThemeUtil.text(10f, Palette.DIM2)
    private val digit = ThemeUtil.text(12f, Palette.FG)
    private val digitOn = ThemeUtil.text(12f, Palette.DOWN)
    private val ring = ThemeUtil.stroke(Palette.DOWN, 1.5f)
    private val cancelPaint = ThemeUtil.stroke(Palette.DIM, 2f)
    private val sb = StringBuilder(2)

    override val showDots: Boolean get() = false
    override val wantsRawTouch: Boolean get() = true

    override fun draw(c: Canvas, s: Snapshot) {
        Geom.centerText(c, str(R.string.setting_cycle_day), 22f, title)

        val selected = prefs.cycleDay
        for (i in 0 until Prefs.CYCLE_DAY_MAX) {
            val day = i + 1
            val cx = COL_X[i % COLS]
            val cy = ROW_Y[i / COLS]
            if (day == selected) c.drawCircle(cx, cy - 4f, 13f, ring)
            sb.setLength(0)
            sb.append(day)
            Geom.centerText(c, sb, cy, if (day == selected) digitOn else digit, cx)
        }

        // Iptal: bu sayfanin tek cikisi, cihazda geri tusu yok
        val cx = COL_X[CANCEL_INDEX % COLS]
        val cy = ROW_Y[CANCEL_INDEX / COLS] - 4f
        val r = 7f
        c.drawLine(cx - r, cy - r, cx + r, cy + r, cancelPaint)
        c.drawLine(cx + r, cy - r, cx - r, cy + r, cancelPaint)
    }

    override fun onRawTouch(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_UP) return
        for (i in 0 until COLS * ROWS) {
            if (i >= Prefs.CYCLE_DAY_MAX && i != CANCEL_INDEX) continue
            val cx = COL_X[i % COLS]
            val cy = ROW_Y[i / COLS] - 4f
            if (Math.abs(event.x - cx) > HIT_X || Math.abs(event.y - cy) > HIT_Y) continue
            if (i == CANCEL_INDEX) {
                onCancel()
            } else {
                prefs.cycleDay = i + 1
                onPicked()
            }
            return
        }
    }
}
