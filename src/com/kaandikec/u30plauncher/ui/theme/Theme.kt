package com.kaandikec.u30plauncher.ui.theme

import android.graphics.Canvas
import android.graphics.Paint
import com.kaandikec.u30plauncher.core.Snapshot

/**
 * Sayfa 1'i cizen strateji.
 *
 * `sb` cagiran tarafindan saglanir ve her cizimde yeniden kullanilir — tema
 * kendi StringBuilder'ini ayirmaz.
 */
interface Theme {
    fun draw(c: Canvas, s: Snapshot, sb: StringBuilder)
}

/** Temalarin paylastigi Paint fabrikalari ve ortak metin parcalari. */
object ThemeUtil {
    fun text(size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
    }

    fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    fun stroke(color: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = w
    }

    fun hairline(color: Int) = Paint().apply {
        this.color = color
        strokeWidth = 1f
    }

    /** "HH:MM" — allocation yok. */
    fun appendClock(sb: StringBuilder, minuteOfDay: Int) {
        val h = minuteOfDay / 60
        val m = minuteOfDay % 60
        if (h < 10) sb.append('0')
        sb.append(h)
        sb.append(':')
        if (m < 10) sb.append('0')
        sb.append(m)
    }

    /** Operator adi, yoksa aciklayici metin. */
    fun appendOperator(sb: StringBuilder, s: Snapshot) {
        if (s.operator.isNotEmpty()) sb.append(s.operator)
        else if (!s.phonePermission) sb.append("izin yok")
        else sb.append("SIM yok")
    }

    fun appendOrDash(sb: StringBuilder, v: Int) {
        if (v == Snapshot.UNKNOWN) sb.append('—') else sb.append(v)
    }

    /** ondabir birimli degeri "6.0" gibi yazar. */
    fun appendTenths(sb: StringBuilder, v: Int) {
        if (v == Snapshot.UNKNOWN) {
            sb.append('—')
            return
        }
        sb.append(v / 10)
        sb.append('.')
        val f = v % 10
        sb.append(if (f < 0) -f else f)
    }
}
