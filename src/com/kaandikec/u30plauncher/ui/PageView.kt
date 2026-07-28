package com.kaandikec.u30plauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.view.View
import com.kaandikec.u30plauncher.core.Snapshot

/** Tum sayfalarin ortak tabani: snapshot tutar, cizimi alt sinifa birakir. */
abstract class PageView(ctx: Context) : View(ctx) {

    var snapshot: Snapshot = Snapshot.EMPTY
        private set

    /** Kac nokta gosterilecegi; Pager tarafindan atanir. */
    var pageCount: Int = 1
    var pageIndex: Int = 0

    private val dotOn = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        .apply { color = Palette.FG }
    private val dotOff = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        .apply { color = Palette.TRACK }

    /** Yeniden cizim yalnizca icerik degistiyse. */
    fun update(s: Snapshot) {
        if (s == snapshot) return
        snapshot = s
        invalidate()
    }

    final override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Palette.BG)
        try {
            draw(canvas, snapshot)
        } catch (t: Throwable) {
            // Bir sayfanin cizim hatasi launcher'i dusurmemeli.
            android.util.Log.e("U30P", "draw failed in " + javaClass.simpleName, t)
        }
        if (showDots) Geom.dots(canvas, pageCount, pageIndex, 222f, dotOn, dotOff)
    }

    protected open val showDots: Boolean get() = true

    protected abstract fun draw(c: Canvas, s: Snapshot)
}
