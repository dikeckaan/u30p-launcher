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

    private var lockFlashUntil = 0L

    /**
     * Kilitliyken dokunuldugunda gostergeyi kisa sure vurgular.
     *
     * Kilitli ekranda hicbir sey olmamasi "bozuk" gibi hissettiriyordu;
     * asma kilidin parlamasi nedenini aciklar.
     */
    fun flashLock() {
        lockFlashUntil = android.os.SystemClock.uptimeMillis() + 500L
        invalidate()
    }

    /** Kilit durumu; Pager tarafindan atanir, gostergeyi surer. */
    var lockedIndicator: Boolean = true
        set(v) {
            if (field == v) return
            field = v
            invalidate()
        }

    private val dotOn = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        .apply { color = Palette.FG }
    private val dotOff = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        .apply { color = Palette.TRACK }
    private val lockPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.DIM2
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1.5f
    }

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
        // Kilitliyken sayfa gecisi yok, dolayisiyla nokta gostergesi anlamsiz;
        // onun yerine asma kilit cizilir. Boylece durum tek bakista okunur ve
        // alt seritteki pil/VPN/istemci gostergeleriyle cakismaz.
        if (showDots) {
            if (lockedIndicator) {
                val flashing = android.os.SystemClock.uptimeMillis() < lockFlashUntil
                lockPaint.color = if (flashing) Palette.FG else Palette.DIM2
                val size = if (flashing) 13f else 10f
                Geom.padlock(canvas, Geom.CX - size / 2f, 220f - size, size, lockPaint)
                if (flashing) postInvalidateDelayed(60L)
            } else {
                Geom.dots(canvas, pageCount, pageIndex, 227f, dotOn, dotOff)
            }
        }
    }

    protected open val showDots: Boolean get() = true

    /**
     * Ham dokunma olaylarina ihtiyac duyan sayfalar (aksiyon, ayar) bunu true
     * yapar; Pager olaylari `onRawTouch` ile iletir.
     */
    open val wantsRawTouch: Boolean get() = false

    /**
     * Cihaz diline gore metin.
     *
     * Tum kullaniciya gorunen metinler `res/values/` altindaki strings.xml
     * dosyalarinda; Android cihazin diline gore dogru olani secer.
     */
    protected fun str(id: Int): String = context.getString(id)

    /** Pager tarafindan iletilen ham dokunma. Varsayilan: yok say. */
    open fun onRawTouch(event: android.view.MotionEvent) {}

    /**
     * Sayfa bu yonde kaydirabiliyor mu? Kaydirabiliyorsa Pager dikey hareketi
     * mod degistirmek icin kullanmaz, sayfaya birakir.
     *
     * @param fingerDown parmak asagi hareket ediyor (icerik yukari kayar)
     */
    open fun canScrollVertically(fingerDown: Boolean): Boolean = false

    protected abstract fun draw(c: Canvas, s: Snapshot)
}
