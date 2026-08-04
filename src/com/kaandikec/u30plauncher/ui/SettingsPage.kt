package com.kaandikec.u30plauncher.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.kaandikec.u30plauncher.R
import com.kaandikec.u30plauncher.Actions
import com.kaandikec.u30plauncher.core.LockTransition
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.store.Prefs
import com.kaandikec.u30plauncher.ui.theme.ThemeUtil

/**
 * Ayarlar. Satira dokunmak degeri siradaki secenege gecirir.
 *
 * Liste kaydirilabilir: ayar sayisi buyudukce 240x240'a sigmiyor. Son satir
 * cihazin kendi Ayarlar uygulamasini acar — launcher'da olmayan ZTE/Android
 * ayarlari icin; yanindaki ok oraya gecilecegini gosterir.
 */
class SettingsPage(
    ctx: Context,
    private val prefs: Prefs,
    private val onChanged: () -> Unit,
    private val onLanguageChanged: () -> Unit,
    private val onEnrolLock: (Int) -> Unit,
    private val onPickCycleDay: () -> Unit
) : PageView(ctx) {

    companion object {
        private const val ROW_H = 36f
        private const val TOP = 34f
        private const val VISIBLE_BOTTOM = 214f

        private const val THEME = 0
        private const val BRIGHTNESS = 1
        private const val REFRESH = 2
        private const val LANGUAGE = 3
        private const val LOCK = 4
        private const val CYCLE_DAY = 5
        private const val DATA_LIMIT = 6
        private const val DETAIL = 7
        private const val ENGINEERING = 8
        private const val SYSTEM = 9
        private const val HISTORY = 10
        private const val SMS = 11
        private const val DEVICE_SETTINGS = 12
        private const val ROW_COUNT = 13

        /**
         * Kilit satirinda sifre belirlemeyi acan basili tutma suresi.
         *
         * Pager'in kendi uzun basmasi (600 ms) ayarlar ekraninda kapali
         * oldugu icin catisma yok; yine de kaydirmayla karismasin diye
         * biraz kisa tutuldu.
         */
        private const val LONG_PRESS_MS = 450L
    }

    private val title = ThemeUtil.text(10f, Palette.DIM2)
    private val label = ThemeUtil.text(12f, Palette.FG)
    private val value = ThemeUtil.text(11f, Palette.DIM)
    private val warn = ThemeUtil.text(9f, Palette.WARN)
    private val hair = ThemeUtil.hairline(Palette.HAIRLINE)
    private val marker = ThemeUtil.fill(Palette.DOWN)
    private val chevron = ThemeUtil.stroke(Palette.DOWN, 2f)
    private val highlight = ThemeUtil.fill(0x14FFFFFF)
    private val sb = StringBuilder(16)

    /** -1 = otomatik; sistemden okunur, sayfa acilinca tazelenir. */
    private var brightnessLevel = -1

    /** Sayfa gosterilirken cagrilir; parlaklik disaridan degismis olabilir. */
    fun refreshState() {
        Actions.brightnessAsync { brightnessLevel = it; invalidate() }
    }

    private var scrollY = 0f
    private var maxScroll = 0f
    private var downY = 0f
    private var downScroll = 0f
    private var dragging = false
    private var longPressFired = false
    private var pressed = -1
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    override val showDots: Boolean get() = false
    override val wantsRawTouch: Boolean get() = true

    init {
        val content = ROW_COUNT * ROW_H
        val viewport = VISIBLE_BOTTOM - TOP
        maxScroll = if (content > viewport) content - viewport else 0f
    }

    override fun canScrollVertically(fingerDown: Boolean): Boolean =
        if (fingerDown) scrollY > 0.5f else scrollY < maxScroll - 0.5f

    override fun draw(c: Canvas, s: Snapshot) {
        Geom.centerText(c, str(R.string.settings_title), 16f, title)
        Geom.hairline(c, 30f, 60f, hair)

        c.save()
        c.clipRect(0f, TOP, Geom.W, VISIBLE_BOTTOM)
        for (i in 0 until ROW_COUNT) {
            val y = TOP + i * ROW_H - scrollY
            if (y > VISIBLE_BOTTOM || y + ROW_H < TOP) continue
            drawRow(c, i, y)
        }
        c.restore()

        drawScrollbar(c)

        if (!s.phonePermission) {
            Geom.centerText(c, str(R.string.permission_missing), 220f, warn)
        }
    }

    private fun drawRow(c: Canvas, i: Int, y: Float) {
        if (i == pressed) {
            c.drawRoundRect(34f, y + 1f, 206f, y + ROW_H - 4f, 8f, 8f, highlight)
        }
        Geom.textAt(c, labelOf(i), 44f, y + 8f, label)

        if (i == DEVICE_SETTINGS) {
            // Cihazin kendi ayarlarina gidilecegini gosteren ok
            val ax = 190f
            val ay = y + 15f
            c.drawLine(ax - 4f, ay - 5f, ax + 1f, ay, chevron)
            c.drawLine(ax + 1f, ay, ax - 4f, ay + 5f, chevron)
            return
        }

        sb.setLength(0)
        appendValue(sb, i)
        // Sirri olmayan bir kilit modu sessizce ise yaramaz; degeri uyari
        // renginde cizmek "burada yapilacak bir sey var" der.
        val p = if (i == LOCK && needsSecret()) warn else value
        val w = Geom.textWidth(sb, p)
        Geom.textAt(c, sb, 196f - w, y + 9f, p)
    }

    /** Secili modun kullanilabilir bir sirri var mi? */
    private fun needsSecret(): Boolean =
        LockTransition.needsEnrolment(prefs.lockMode, prefs.lockSecret, prefs.lockSecretMode)

    private fun drawScrollbar(c: Canvas) {
        if (maxScroll <= 0f) return
        val trackTop = TOP + 2f
        val trackH = (VISIBLE_BOTTOM - 2f) - trackTop
        val viewport = VISIBLE_BOTTOM - TOP
        val thumbH = (trackH * viewport / (ROW_COUNT * ROW_H)).coerceAtLeast(12f)
        val top = trackTop + (trackH - thumbH) * (scrollY / maxScroll)
        c.drawRoundRect(214f, top, 217f, top + thumbH, 1.5f, 1.5f, marker)
    }

    private fun labelOf(i: Int): String = when (i) {
        THEME -> str(R.string.setting_theme)
        BRIGHTNESS -> str(R.string.setting_brightness)
        REFRESH -> str(R.string.setting_refresh)
        LANGUAGE -> str(R.string.setting_language)
        LOCK -> str(R.string.setting_lock)
        CYCLE_DAY -> str(R.string.setting_cycle_day)
        DATA_LIMIT -> str(R.string.setting_data_limit)
        DETAIL -> str(R.string.setting_detail_page)
        ENGINEERING -> str(R.string.setting_engineering_page)
        SYSTEM -> str(R.string.setting_system_page)
        HISTORY -> str(R.string.setting_history_page)
        SMS -> str(R.string.setting_sms_page)
        else -> str(R.string.device_settings)
    }

    private fun appendValue(sb: StringBuilder, i: Int) {
        when (i) {
            THEME -> sb.append(
                when (prefs.theme) {
                    Prefs.THEME_ARC -> "Arc"
                    Prefs.THEME_BALANCED -> "Balanced"
                    else -> "Stacked"
                }
            )
            BRIGHTNESS -> {
                if (brightnessLevel < 0) sb.append(str(R.string.brightness_auto))
                else {
                    sb.append(Actions.BRIGHTNESS_LEVELS[brightnessLevel] * 100 / 255)
                    sb.append('%')
                }
            }
            REFRESH -> {
                val ms = prefs.refreshMs
                if (ms < 1000) {
                    sb.append("0.")
                    sb.append(ms / 100)
                } else {
                    sb.append(ms / 1000)
                }
                sb.append(' ')
                sb.append(str(R.string.second_short))
            }
            LANGUAGE -> sb.append(
                when (prefs.language) {
                    Prefs.LANG_EN -> str(R.string.lang_en)
                    Prefs.LANG_TR -> str(R.string.lang_tr)
                    else -> str(R.string.lang_system)
                }
            )
            LOCK -> {
                sb.append(
                    when (prefs.lockMode) {
                        Prefs.LOCK_PATTERN -> str(R.string.lock_pattern)
                        Prefs.LOCK_PIN -> str(R.string.lock_pin)
                        else -> str(R.string.lock_hold)
                    }
                )
                // Basili tut sir gerektirmez; digerlerinde sirrin durumu
                // satirin kendisinde gorunsun.
                if (prefs.lockMode != Prefs.LOCK_HOLD) {
                    sb.append(' ')
                    sb.append(
                        if (needsSecret()) str(R.string.lock_not_set)
                        else str(R.string.lock_is_set)
                    )
                }
            }
            CYCLE_DAY -> sb.append(prefs.cycleDay)
            DATA_LIMIT -> {
                val mb = prefs.dataLimitMb
                if (mb <= 0) sb.append(str(R.string.data_unlimited))
                else if (mb % 1024 == 0) {
                    sb.append(mb / 1024)
                    sb.append(" GB")
                } else {
                    sb.append(mb)
                    sb.append(" MB")
                }
            }
            DETAIL -> sb.append(str(if (prefs.detailPage) R.string.on else R.string.off))
            ENGINEERING -> sb.append(str(if (prefs.engineeringPage) R.string.on else R.string.off))
            HISTORY -> sb.append(str(if (prefs.historyPage) R.string.on else R.string.off))
            SMS -> sb.append(str(if (prefs.smsPage) R.string.on else R.string.off))
            else -> sb.append(str(if (prefs.systemPage) R.string.on else R.string.off))
        }
    }

    private fun rowAt(y: Float): Int {
        if (y < TOP || y > VISIBLE_BOTTOM) return -1
        val idx = ((y - TOP + scrollY) / ROW_H).toInt()
        return if (idx in 0 until ROW_COUNT) idx else -1
    }

    override fun onRawTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                downScroll = scrollY
                dragging = false
                longPressFired = false
                pressed = rowAt(event.y)
                if (pressed >= 0) postDelayed(longPress, LONG_PRESS_MS)
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - downY
                if (!dragging && Math.abs(dy) > slop) {
                    dragging = true
                    pressed = -1
                    clearPendingLongPress()
                }
                if (dragging) {
                    scrollY = (downScroll - dy).coerceIn(0f, maxScroll)
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                clearPendingLongPress()
                // Uzun basma zaten calistiysa birakma satiri ayrica
                // tetiklemesin; yoksa hem sifre ekrani acilir hem mod doner.
                if (!dragging && !longPressFired &&
                    pressed >= 0 && pressed == rowAt(event.y)
                ) {
                    activate(pressed)
                }
                pressed = -1
                dragging = false
                invalidate()
            }

            MotionEvent.ACTION_CANCEL -> {
                clearPendingLongPress()
                pressed = -1
                dragging = false
                invalidate()
            }
        }
    }

    /**
     * Kilit satirinda basili tutmak sifre belirlemeyi acar.
     *
     * Dokunus ile ayrilmasinin sebebi: dokunus modlar arasinda gezinmeyi
     * saglamali, her gecisde sifre ekrani dayatmamali.
     */
    private val longPress = Runnable {
        if (pressed == LOCK && !dragging) {
            longPressFired = true
            pressed = -1
            invalidate()
            if (prefs.lockMode != Prefs.LOCK_HOLD) onEnrolLock(prefs.lockMode)
        }
    }

    /** `View.cancelLongPress()` ile ad catismasin diye ayri isim. */
    private fun clearPendingLongPress() = removeCallbacks(longPress)

    private fun activate(row: Int) {
        when (row) {
            THEME -> prefs.theme = (prefs.theme + 1) % Prefs.THEME_COUNT
            BRIGHTNESS -> {
                // -1 (oto) -> 0..4 -> tekrar oto
                brightnessLevel =
                    if (brightnessLevel >= Actions.BRIGHTNESS_LEVELS.size - 1) -1
                    else brightnessLevel + 1
                Actions.setBrightnessAsync(brightnessLevel)
            }
            REFRESH -> {
                val opts = Prefs.REFRESH_OPTIONS
                var idx = -1
                for (i in opts.indices) if (opts[i] == prefs.refreshMs) idx = i
                prefs.refreshMs = opts[(if (idx < 0) 1 else idx + 1) % opts.size]
            }
            LANGUAGE -> {
                prefs.language = (prefs.language + 1) % Prefs.LANG_COUNT
                prefs.returnToSettings = true
                // Yerellestirilmis Context Activity olusurken kuruluyor;
                // degisikligin gorunmesi icin yeniden olusturulmasi gerek.
                onLanguageChanged()
                return
            }
            LOCK -> {
                // Dokunus YALNIZCA modu dondurur. Once burada kayit ekrani
                // aciliyordu; sirri olmayan bir moda her gecisde kullanici
                // desen ekranina dusuyor, modlar arasinda gezinemiyordu.
                // Sir belirleme artik ayni satiri basili tutmakla geliyor.
                //
                // Sirri olmayan bir mod tehlikeli degil: LockTransition
                // .requiresChallenge sir yoksa meydan okuma gostermez, yani
                // kimse disarida kalmaz.
                prefs.lockMode = (prefs.lockMode + 1) % Prefs.LOCK_COUNT
            }
            CYCLE_DAY -> {
                onPickCycleDay()
                return
            }
            DATA_LIMIT -> {
                val opts = Prefs.DATA_LIMIT_OPTIONS
                var idx = -1
                for (i in opts.indices) if (opts[i] == prefs.dataLimitMb) idx = i
                prefs.dataLimitMb = opts[(idx + 1) % opts.size]
            }
            DETAIL -> prefs.detailPage = !prefs.detailPage
            ENGINEERING -> prefs.engineeringPage = !prefs.engineeringPage
            SYSTEM -> prefs.systemPage = !prefs.systemPage
            HISTORY -> prefs.historyPage = !prefs.historyPage
            SMS -> prefs.smsPage = !prefs.smsPage
            DEVICE_SETTINGS -> {
                try {
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Throwable) {
                }
                return
            }
        }
        invalidate()
        onChanged()
    }
}
