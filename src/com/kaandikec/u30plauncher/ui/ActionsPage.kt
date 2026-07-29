package com.kaandikec.u30plauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import com.kaandikec.u30plauncher.Actions
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.ui.theme.ThemeUtil

/**
 * Yikici islemler kilit acikken bile 1 sn basili tutma ister; dis kenarda
 * dolan halka geri bildirim verir, parmak kalkinca islem iptal olur.
 *
 * Ekran suresi zararsiz oldugu icin tek dokunusla degerler arasinda doner.
 */
class ActionsPage(ctx: Context) : PageView(ctx) {
    companion object {
        private const val HOLD_MS = 1000L
        private val ROW_Y = floatArrayOf(66f, 114f, 162f)
        private const val ROW_HIT = 22f
    }

    private val titlePaint = ThemeUtil.text(10f, Palette.DIM2)
    private val labelPaint = ThemeUtil.text(14f, Palette.FG)
    private val hintPaint = ThemeUtil.text(10f, Palette.DIM2)
    private val bullet = ThemeUtil.stroke(Palette.ERR, 2f)
    private val progress = ThemeUtil.stroke(Palette.ERR, 3f).apply {
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    private val rowColor = intArrayOf(Palette.ERR, Palette.WARN, Palette.UP)
    private val sb = StringBuilder(24)

    private var holdingRow = -1
    private var holdStart = 0L
    private var timeoutMs = 60_000
    private var airplane = false

    var onActionDone: ((String) -> Unit)? = null

    override val showDots: Boolean get() = false
    override val wantsRawTouch: Boolean get() = true

    /** Kilit acildiginda cagrilir; root'a bir kez sorup durumu tazeler. */
    fun refreshState() {
        if (!snapshot.rootAvailable) return
        timeoutMs = Actions.screenTimeout()
        airplane = Actions.airplaneOn()
        invalidate()
    }

    override fun draw(c: Canvas, s: Snapshot) {
        Geom.centerText(c, "AKSIYONLAR", 20f, titlePaint)

        for (i in 0 until 3) {
            bullet.color = if (s.rootAvailable) rowColor[i] else Palette.DIM2
            c.drawCircle(52f, ROW_Y[i] + 2f, 8f, bullet)
            labelPaint.color = if (s.rootAvailable) Palette.FG else Palette.DIM2
            sb.setLength(0)
            appendRowLabel(sb, i)
            Geom.textAt(c, sb, 72f, ROW_Y[i] - 5f, labelPaint)
        }

        if (!s.rootAvailable) {
            Geom.centerText(c, "root yok", 200f, hintPaint)
            return
        }

        if (holdingRow >= 0) {
            val elapsed = SystemClock.uptimeMillis() - holdStart
            val f = (elapsed.toFloat() / HOLD_MS).coerceIn(0f, 1f)
            progress.color = rowColor[holdingRow]
            Geom.arcRing(c, 116f, 3f, 0f, 360f * f, progress)
            if (f >= 1f) fire(holdingRow) else postInvalidateOnAnimation()
        } else {
            Geom.centerText(c, "1 sn basili tut", 206f, hintPaint)
        }
    }

    private fun appendRowLabel(sb: StringBuilder, i: Int) {
        when (i) {
            0 -> sb.append("Yeniden baslat")
            1 -> sb.append(if (airplane) "Veriyi ac" else "Veri kes")
            else -> {
                sb.append("Ekran: ")
                appendTimeout(sb, timeoutMs)
            }
        }
    }

    /**
     * Cihazdaki deger bizim on tanimli seceneklerimizden biri olmak zorunda
     * degil (baska bir uygulama veya kullanici degistirmis olabilir); o durumda
     * "kapanmaz" yazmak yaniltici olur, gercek sureyi gosteririz.
     */
    private fun appendTimeout(sb: StringBuilder, ms: Int) {
        when {
            ms == Int.MAX_VALUE || ms <= 0 -> sb.append("kapanmaz")
            ms < 60_000 -> {
                sb.append(ms / 1000)
                sb.append(" sn")
            }
            ms < 3_600_000 -> {
                sb.append(ms / 60_000)
                sb.append(" dk")
            }
            else -> {
                sb.append(ms / 3_600_000)
                sb.append(" sa")
            }
        }
    }

    private fun fire(row: Int) {
        holdingRow = -1
        when (row) {
            0 -> {
                onActionDone?.invoke("Yeniden baslatiliyor")
                Actions.reboot()
            }
            1 -> {
                val target = !airplane
                Actions.setAirplane(target)
                airplane = target
                onActionDone?.invoke(if (target) "Veri kesildi" else "Veri acildi")
            }
        }
        invalidate()
    }

    private fun cycleTimeout() {
        var idx = -1
        for (i in Actions.TIMEOUTS.indices) if (Actions.TIMEOUTS[i] == timeoutMs) idx = i
        val next = Actions.TIMEOUTS[(if (idx < 0) 1 else idx + 1) % Actions.TIMEOUTS.size]
        Actions.setScreenTimeout(next)
        timeoutMs = next
        invalidate()
    }

    private fun rowAt(y: Float): Int {
        for (i in 0 until 3) if (Math.abs(y - ROW_Y[i]) < ROW_HIT) return i
        return -1
    }

    private var downY = 0f

    override fun onRawTouch(event: MotionEvent) {
        if (!snapshot.rootAvailable) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                val row = rowAt(event.y)
                // Ekran suresi zararsiz: tek dokunusla degisir, basili tutma istemez.
                if (row >= 0 && row != 2) {
                    holdingRow = row
                    holdStart = SystemClock.uptimeMillis()
                    invalidate()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                // Parmak satirdan cikarsa islem iptal olsun
                if (holdingRow >= 0 && rowAt(event.y) != holdingRow) {
                    holdingRow = -1
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                if (holdingRow >= 0) {
                    holdingRow = -1
                    invalidate()
                } else if (rowAt(event.y) == 2 && rowAt(downY) == 2) {
                    cycleTimeout()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (holdingRow >= 0) {
                    holdingRow = -1
                    invalidate()
                }
            }
        }
    }
}
