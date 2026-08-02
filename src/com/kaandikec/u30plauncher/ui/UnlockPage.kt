package com.kaandikec.u30plauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.os.SystemClock
import android.view.MotionEvent
import com.kaandikec.u30plauncher.R
import com.kaandikec.u30plauncher.core.LockSecret
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.store.Prefs
import com.kaandikec.u30plauncher.ui.theme.ThemeUtil

/**
 * Desen veya PIN ile kilit acma ekrani. Ayni ekran sirri BELIRLEMEK icin de
 * kullanilir (iki kez girilir, eslesmeleri gerekir).
 *
 * 240x240 yuvarlak ekranda 3x3 desen izgarasi ve 3x4 tus takimi kenarlara
 * degmeden sigacak sekilde yerlestirildi.
 */
class UnlockPage(
    ctx: Context,
    private val prefs: Prefs,
    private val onUnlocked: () -> Unit,
    private val onEnrolled: () -> Unit,
    private val onCancel: () -> Unit
) : PageView(ctx) {

    companion object {
        private val GRID_X = floatArrayOf(70f, 120f, 170f)
        private val GRID_Y = floatArrayOf(78f, 128f, 178f)
        /**
         * Nokta yakalama yaricapi. 26 px cok genisti: noktalar 50 px arali
         * oldugu icin bolgeler birbirine deger ve izgaranin uzerinden gecen
         * her hareket hucre yakalardi. 20 px, aralarinda 10 px'lik olu bant
         * birakir.
         */
        private const val HIT = 20f

        private val KEY_X = floatArrayOf(64f, 120f, 176f)
        private val KEY_Y = floatArrayOf(74f, 118f, 162f, 202f)
        private const val KEY_HIT = 26f

        private const val ERROR_MS = 700L
    }

    /** Sirri belirleme modu; false ise dogrulama. */
    var enrolling = false
        set(v) {
            field = v
            first = ""
            reset()
        }

    private val title = ThemeUtil.text(10f, Palette.DIM2)
    private val hint = ThemeUtil.text(9f, Palette.DIM2)
    private val digit = ThemeUtil.text(17f, Palette.FG)
    private val dotIdle = ThemeUtil.fill(Palette.TRACK)
    private val dotOn = ThemeUtil.fill(Palette.DOWN)
    private val dotErr = ThemeUtil.fill(Palette.ERR)
    private val line = ThemeUtil.stroke(Palette.DOWN, 3f)
    private val keyRing = ThemeUtil.stroke(Palette.DIM2, 1f)
    private val pinOn = ThemeUtil.fill(Palette.DOWN)
    private val pinOff = ThemeUtil.stroke(Palette.DIM2, 1.5f)
    private val path = Path()

    private val cells = ArrayList<Int>(9)
    private var pin = StringBuilder(8)
    private var fingerX = 0f
    private var fingerY = 0f
    private var tracking = false
    private var errorUntil = 0L

    /** Belirleme modunda ilk giris; ikincisiyle karsilastirilir. */
    private var first = ""

    override val showDots: Boolean get() = false
    override val wantsRawTouch: Boolean get() = true

    private val isPattern: Boolean get() = prefs.lockMode == Prefs.LOCK_PATTERN

    fun reset() {
        cells.clear()
        pin.setLength(0)
        tracking = false
        invalidate()
    }

    override fun draw(c: Canvas, s: Snapshot) {
        val err = SystemClock.uptimeMillis() < errorUntil
        Geom.centerText(
            c,
            when {
                !enrolling -> str(R.string.unlock_title)
                first.isEmpty() -> str(R.string.lock_set_first)
                else -> str(R.string.lock_set_repeat)
            },
            18f, title
        )

        if (isPattern) drawPattern(c, err) else drawPin(c, err)

        if (err) Geom.centerText(c, str(R.string.lock_wrong), 222f, hint)
        else if (enrolling) Geom.centerText(c, str(R.string.lock_cancel_hint), 222f, hint)
    }

    private fun drawPattern(c: Canvas, err: Boolean) {
        if (cells.size > 1) {
            path.rewind()
            for (i in cells.indices) {
                val cx = GRID_X[cells[i] % 3]
                val cy = GRID_Y[cells[i] / 3]
                if (i == 0) path.moveTo(cx, cy) else path.lineTo(cx, cy)
            }
            if (tracking) path.lineTo(fingerX, fingerY)
            line.color = if (err) Palette.ERR else Palette.DOWN
            c.drawPath(path, line)
        }
        for (r in 0 until 3) for (col in 0 until 3) {
            val idx = r * 3 + col
            val on = cells.contains(idx)
            c.drawCircle(
                GRID_X[col], GRID_Y[r], if (on) 8f else 5f,
                if (err) dotErr else if (on) dotOn else dotIdle
            )
        }
    }

    private fun drawPin(c: Canvas, err: Boolean) {
        // Girilen hane sayisi
        val n = pin.length
        val gap = 14f
        val x0 = Geom.CX - (LockSecret.PIN_LENGTH - 1) * gap / 2f
        for (i in 0 until LockSecret.PIN_LENGTH) {
            val x = x0 + i * gap
            if (i < n) c.drawCircle(x, 44f, 4f, if (err) dotErr else pinOn)
            else c.drawCircle(x, 44f, 4f, pinOff)
        }

        for (r in 0 until 4) for (col in 0 until 3) {
            val label = keyLabel(r, col) ?: continue
            c.drawCircle(KEY_X[col], KEY_Y[r], 19f, keyRing)
            Geom.centerText(c, label, KEY_Y[r] - 8f, digit, KEY_X[col])
        }
    }

    /** 1-9, sonra sil / 0 / onay. */
    private fun keyLabel(row: Int, col: Int): String? = when {
        row < 3 -> (row * 3 + col + 1).toString()
        col == 0 -> "<"
        col == 1 -> "0"
        else -> null
    }

    override fun onRawTouch(event: MotionEvent) {
        if (isPattern) onPatternTouch(event) else onPinTouch(event)
    }

    private fun onPatternTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cells.clear()
                // Cizim ancak bir noktanin uzerinde baslar; bos alana dokunmak
                // desen baslatmamali.
                tracking = cellAt(event.x, event.y) >= 0
                if (tracking) {
                    fingerX = event.x
                    fingerY = event.y
                    addCellAt(event.x, event.y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (tracking) {
                    fingerX = event.x
                    fingerY = event.y
                    addCellAt(event.x, event.y)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasTracking = tracking
                tracking = false
                // Nokta uzerinde baslamayan dokunus desen sayilmaz; sessizce
                // yok sayilir, "yanlis desen" uyarisi verilmez.
                if (wasTracking) {
                    submit(LockSecret.encodePattern(cells), LockSecret.isPatternValid(cells))
                } else {
                    invalidate()
                }
            }
        }
    }

    /** Dairesel yakalama: kose bolgeleri kare testte fazla genis kaliyordu. */
    private fun cellAt(x: Float, y: Float): Int {
        for (r in 0 until 3) for (col in 0 until 3) {
            val dx = x - GRID_X[col]
            val dy = y - GRID_Y[r]
            if (dx * dx + dy * dy <= HIT * HIT) return r * 3 + col
        }
        return -1
    }

    private fun addCellAt(x: Float, y: Float) {
        val idx = cellAt(x, y)
        if (idx < 0) return
        if (!cells.contains(idx)) {
            cells.add(idx)
            invalidate()
        }
    }

    private fun onPinTouch(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_UP) return
        for (r in 0 until 4) for (col in 0 until 3) {
            val label = keyLabel(r, col) ?: continue
            if (Math.abs(event.x - KEY_X[col]) < KEY_HIT &&
                Math.abs(event.y - KEY_Y[r]) < KEY_HIT
            ) {
                when (label) {
                    "<" -> {
                        if (pin.isNotEmpty()) pin.setLength(pin.length - 1)
                        else onCancel()
                    }
                    else -> if (pin.length < LockSecret.PIN_LENGTH) pin.append(label)
                }
                invalidate()
                if (pin.length == LockSecret.PIN_LENGTH) {
                    submit(pin.toString(), true)
                }
                return
            }
        }
    }

    /** Giris tamamlandi: belirleme modunda kaydet, degilse dogrula. */
    private fun submit(code: String, valid: Boolean) {
        if (!valid) {
            fail()
            return
        }
        if (enrolling) {
            if (first.isEmpty()) {
                first = code
                reset()
                return
            }
            if (first == code) {
                prefs.lockSecret = LockSecret.hash(code)
                first = ""
                reset()
                onEnrolled()
            } else {
                first = ""
                fail()
            }
            return
        }
        if (LockSecret.matches(code, prefs.lockSecret)) {
            reset()
            onUnlocked()
        } else {
            fail()
        }
    }

    private fun fail() {
        errorUntil = SystemClock.uptimeMillis() + ERROR_MS
        invalidate()
        postDelayed({ reset() }, ERROR_MS)
    }
}
