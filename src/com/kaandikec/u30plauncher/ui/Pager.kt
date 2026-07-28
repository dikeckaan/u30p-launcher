package com.kaandikec.u30plauncher.ui

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.kaandikec.u30plauncher.core.Snapshot

/**
 * ViewPager2 yerine elle yazilmis sayfa anahtari.
 *
 * Tek seferde tek cocuk gorunur, kaydirma animasyonu yoktur: 30 Hz panelde
 * animasyon gorsel kazanc saglamaz ama her karede cizim maliyeti getirir.
 *
 * Dokunma isleme hiza degil MESAFEYE bakar. `GestureDetector.onFling` sentetik
 * olaylarda (ve yavas, kararli parmak hareketlerinde) guvenilmez; mesafe esigi
 * her iki durumda da calisir.
 *
 * Tum olaylar burada yakalanir; ham olaya ihtiyac duyan sayfalar (aksiyon,
 * ayar) `wantsRawTouch` ile talep eder — boylece aksiyon sayfasinin "basili
 * tut" hareketi ile kilidi acan uzun basma birbirine karismaz.
 */
class Pager(ctx: Context) : FrameLayout(ctx) {
    companion object {
        private const val SWIPE_MIN = 28f
        private const val LONG_PRESS_MS = 600L
    }

    private val pages = ArrayList<PageView>(3)
    private var current = 0
    private var latest: Snapshot = Snapshot.EMPTY
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var longPressFired = false
    private var moved = false

    var onLongPress: (() -> Unit)? = null
    var onInteraction: (() -> Unit)? = null

    /** Kilit acikken uzun basma kilidi acmaz; sayfa kendi islerini yapar. */
    var longPressEnabled = true

    private val longPress = Runnable {
        if (!moved) {
            longPressFired = true
            onLongPress?.invoke()
        }
    }

    init {
        isClickable = true
    }

    fun setPages(list: List<PageView>) {
        removeAllViews()
        pages.clear()
        pages.addAll(list)
        for (i in pages.indices) {
            val p = pages[i]
            p.pageCount = pages.size
            p.pageIndex = i
            addView(p)
        }
        current = 0
        applyVisibility()
        pages.firstOrNull()?.update(latest)
    }

    fun update(s: Snapshot) {
        latest = s
        pages.getOrNull(current)?.update(s)
    }

    val currentPage: PageView? get() = pages.getOrNull(current)

    var index: Int
        get() = current
        set(v) = go(v)

    private fun go(target: Int) {
        if (pages.isEmpty()) return
        val n = pages.size
        val next = ((target % n) + n) % n
        if (next == current) return
        current = next
        applyVisibility()
        pages[current].update(latest)
    }

    private fun applyVisibility() {
        for (i in pages.indices) {
            pages[i].visibility = if (i == current) VISIBLE else GONE
        }
    }

    /** Cocuklar dokunma almaz; karar burada verilir. */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        onInteraction?.invoke()
        val page = currentPage

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downAt = SystemClock.uptimeMillis()
                moved = false
                longPressFired = false
                if (longPressEnabled) postDelayed(longPress, LONG_PRESS_MS)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!moved &&
                    (Math.abs(event.x - downX) > slop || Math.abs(event.y - downY) > slop)
                ) {
                    moved = true
                    removeCallbacks(longPress)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                val dx = event.x - downX
                val dy = event.y - downY
                if (!longPressFired &&
                    Math.abs(dx) >= SWIPE_MIN && Math.abs(dx) > Math.abs(dy)
                ) {
                    go(if (dx < 0) current + 1 else current - 1)
                    // Sayfa degistiren hareket cocuga tikla olarak gitmemeli
                    page?.onRawTouch(cancelEvent(event))
                    return true
                }
            }
        }

        if (page != null && page.wantsRawTouch) page.onRawTouch(event)
        return true
    }

    /** Cocugun devam eden hareketini iptal ettirmek icin sentetik CANCEL. */
    private fun cancelEvent(src: MotionEvent): MotionEvent =
        MotionEvent.obtain(
            src.downTime, src.eventTime, MotionEvent.ACTION_CANCEL, src.x, src.y, 0
        )
}
