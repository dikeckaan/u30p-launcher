package com.kaandikec.u30plauncher.ui

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.kaandikec.u30plauncher.core.Snapshot

/**
 * ViewPager2 yerine elle yazilmis sayfa anahtari.
 *
 * Sayfalar parmagi TAKIP EDER ve birakinca yerine oturur. Once animasyonsuzdu
 * ("30 Hz panelde kazanc yok") ama gercek kullanimda sonuc kotuydu: parmak
 * suruklenirken hicbir sey kipirdamiyor, esik tutmazsa da sessizce hicbir sey
 * olmuyordu — kullanici bunu "takiliyor" olarak yasiyor. 240x240'ta iki
 * katmani otelemek ucuz; geri bildirim onemli.
 *
 * Eksen kilidi: yon ilk anlamli harekette secilir ve gesture boyunca sabit
 * kalir. Kucuk yuvarlak ekranda bas parmak hareketi capraz oluyor; bitis
 * noktasina bakmak yatay hareketi dikeye cevirebiliyordu.
 *
 * Sistem kenar hareketleri: 240px genislikte Android'in geri-hareket bolgesi
 * ekranin ciddi bir kismini kapliyor ve kenardan baslayan kaydirmalari
 * yutuyordu. `systemGestureExclusionRects` ile tum yuzey talep ediliyor.
 */
class Pager(ctx: Context) : FrameLayout(ctx) {
    companion object {
        /** Sayfa degistirmek icin gereken en kucuk yatay mesafe. */
        private const val SWIPE_MIN = 24f
        private const val LONG_PRESS_MS = 600L

        /** Yerine oturma suresi. */
        private const val SETTLE_MS = 180f

        /** Kenarda sayfa yokken suruklemeye direnc. */
        private const val EDGE_RESIST = 0.35f
    }

    private val pages = ArrayList<PageView>(3)
    private var current = 0
    private var latest: Snapshot = Snapshot.EMPTY
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    private var downX = 0f
    private var downY = 0f
    private var longPressFired = false
    private var moved = false
    private var pageCanScrollUp = false
    private var pageCanScrollDown = false

    /** null = eksen henuz secilmedi. */
    private var horizontal: Boolean? = null

    private var dragX = 0f
    private var settleFrom = 0f
    private var settleTo = 0f
    private var settleStart = 0L
    private var settling = false

    var onLongPress: (() -> Unit)? = null
    var onInteraction: (() -> Unit)? = null
    var onSwipeUp: (() -> Unit)? = null
    var onSwipeDown: (() -> Unit)? = null

    /** Kilitli ekranda dokunma denendiginde gosterge vurgulansin diye. */
    var onLockedTouch: (() -> Unit)? = null

    var longPressEnabled = true

    var locked = true
        set(v) {
            field = v
            for (p in pages) p.lockedIndicator = v
        }

    private val longPress = Runnable {
        if (!moved) {
            longPressFired = true
            onLongPress?.invoke()
        }
    }

    init {
        isClickable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Android'in kenar hareketlerini bizden calmasini engelle.
        systemGestureExclusionRects = listOf(Rect(0, 0, w, h))
        applyOffsets()
    }

    fun setPages(list: List<PageView>) {
        removeAllViews()
        pages.clear()
        pages.addAll(list)
        for (i in pages.indices) {
            val p = pages[i]
            p.pageCount = pages.size
            p.pageIndex = i
            p.lockedIndicator = locked
            addView(p)
        }
        current = 0
        dragX = 0f
        settling = false
        applyOffsets()
        pages.firstOrNull()?.update(latest)
    }

    fun update(s: Snapshot) {
        latest = s
        // Suruklerken komsu sayfa da gorunur oldugu icin o da guncellenmeli
        for (i in pages.indices) {
            if (Math.abs(i - current) <= 1) pages[i].update(s)
        }
    }

    val currentPage: PageView? get() = pages.getOrNull(current)

    var index: Int
        get() = current
        set(v) {
            val n = pages.size
            if (n == 0) return
            current = ((v % n) + n) % n
            dragX = 0f
            applyOffsets()
            pages[current].update(latest)
        }

    /** Sayfalari `current` ve `dragX`'e gore yerlestirir. */
    private fun applyOffsets() {
        val w = width
        if (w == 0) return
        for (i in pages.indices) {
            val tx = (i - current) * w + dragX
            val p = pages[i]
            if (Math.abs(tx) >= w) {
                if (p.visibility != GONE) p.visibility = GONE
            } else {
                if (p.visibility != VISIBLE) p.visibility = VISIBLE
                p.translationX = tx
            }
        }
    }

    private fun startSettle(to: Float) {
        settleFrom = dragX
        settleTo = to
        settleStart = android.view.animation.AnimationUtils.currentAnimationTimeMillis()
        settling = true
        postOnAnimation(settleStep)
    }

    private val settleStep = object : Runnable {
        override fun run() {
            if (!settling) return
            val now = android.view.animation.AnimationUtils.currentAnimationTimeMillis()
            var t = (now - settleStart) / SETTLE_MS
            if (t >= 1f) t = 1f
            // ease-out: hizli baslayip yumusak biten his
            val e = 1f - (1f - t) * (1f - t)
            dragX = settleFrom + (settleTo - settleFrom) * e
            applyOffsets()
            if (t < 1f) {
                postOnAnimation(this)
            } else {
                settling = false
                finishSettle()
            }
        }
    }

    /** Oturma bitince sayfa indeksini kaydirip ofseti sifirla. */
    private fun finishSettle() {
        val w = width
        if (w == 0) return
        if (settleTo != 0f) {
            current += if (settleTo < 0) 1 else -1
            dragX = 0f
            applyOffsets()
            pages.getOrNull(current)?.update(latest)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        onInteraction?.invoke()
        val page = currentPage

        if (locked) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    moved = false
                    longPressFired = false
                    postDelayed(longPress, LONG_PRESS_MS)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!moved &&
                        (Math.abs(event.x - downX) > slop || Math.abs(event.y - downY) > slop)
                    ) {
                        moved = true
                        removeCallbacks(longPress)
                        // Neden hicbir sey olmadigini goster
                        onLockedTouch?.invoke()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(longPress)
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                moved = false
                longPressFired = false
                horizontal = null
                settling = false
                pageCanScrollUp = page?.canScrollVertically(false) ?: false
                pageCanScrollDown = page?.canScrollVertically(true) ?: false
                if (longPressEnabled) postDelayed(longPress, LONG_PRESS_MS)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (!moved && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                    moved = true
                    removeCallbacks(longPress)
                    // Eksen bir kez secilir ve gesture boyunca degismez
                    horizontal = Math.abs(dx) >= Math.abs(dy)
                }
                if (horizontal == true) {
                    dragX = resist(dx)
                    applyOffsets()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                val dx = event.x - downX
                val dy = event.y - downY

                if (horizontal == true) {
                    settleHorizontal(dx)
                    page?.onRawTouch(cancelEvent(event))
                    return true
                }

                if (horizontal == false && !longPressFired &&
                    Math.abs(dy) >= SWIPE_MIN
                ) {
                    val consumedByPage = if (dy > 0) pageCanScrollDown else pageCanScrollUp
                    val handler = if (dy < 0) onSwipeUp else onSwipeDown
                    if (!consumedByPage && handler != null) {
                        handler.invoke()
                        page?.onRawTouch(cancelEvent(event))
                        return true
                    }
                }
            }
        }

        if (page != null && page.wantsRawTouch) page.onRawTouch(event)
        return true
    }

    /** Kenarda sayfa yoksa surukleme yavaslar — sinira gelindigi hissedilir. */
    private fun resist(dx: Float): Float {
        val atStart = current == 0 && dx > 0
        val atEnd = current == pages.size - 1 && dx < 0
        return if (atStart || atEnd) dx * EDGE_RESIST else dx
    }

    private fun settleHorizontal(dx: Float) {
        val w = width
        if (w == 0) return
        val canPrev = current > 0
        val canNext = current < pages.size - 1
        val target = when {
            dx <= -SWIPE_MIN && canNext -> -w.toFloat()
            dx >= SWIPE_MIN && canPrev -> w.toFloat()
            else -> 0f
        }
        startSettle(target)
    }

    private fun cancelEvent(src: MotionEvent): MotionEvent =
        MotionEvent.obtain(
            src.downTime, src.eventTime, MotionEvent.ACTION_CANCEL, src.x, src.y, 0
        )
}
