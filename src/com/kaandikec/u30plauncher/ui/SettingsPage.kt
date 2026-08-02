package com.kaandikec.u30plauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import com.kaandikec.u30plauncher.R
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.store.Prefs
import com.kaandikec.u30plauncher.ui.theme.ThemeUtil

/** Ayarlar: satira dokunmak degeri siradaki secenege gecirir. */
class SettingsPage(
    ctx: Context,
    private val prefs: Prefs,
    private val onChanged: () -> Unit
) : PageView(ctx) {

    companion object {
        private val ROW_Y = floatArrayOf(38f, 74f, 110f, 146f, 182f)
        private const val ROW_HIT = 20f
    }

    private val title = ThemeUtil.text(10f, Palette.DIM2)
    private val label = ThemeUtil.text(13f, Palette.FG)
    private val value = ThemeUtil.text(12f, Palette.DIM)
    private val warn = ThemeUtil.text(9f, Palette.WARN)
    private val hair = ThemeUtil.hairline(Palette.HAIRLINE)
    private val sb = StringBuilder(16)

    override val showDots: Boolean get() = false
    override val wantsRawTouch: Boolean get() = true

    override fun draw(c: Canvas, s: Snapshot) {
        Geom.centerText(c, str(R.string.settings_title), 18f, title)
        for (i in ROW_Y.indices) {
            Geom.textAt(c, labelOf(i), 44f, ROW_Y[i], label)
            sb.setLength(0)
            appendValue(sb, i)
            val w = Geom.textWidth(sb, value)
            Geom.textAt(c, sb, 196f - w, ROW_Y[i] + 1f, value)
            if (i < ROW_Y.size - 1) Geom.hairline(c, ROW_Y[i] + 26f, 80f, hair)
        }
        if (!s.phonePermission) {
            Geom.centerText(c, str(R.string.permission_missing), 216f, warn)
        }
    }

    private fun labelOf(i: Int): String = when (i) {
        0 -> str(R.string.setting_theme)
        1 -> str(R.string.setting_refresh)
        2 -> str(R.string.setting_detail_page)
        3 -> str(R.string.setting_engineering_page)
        else -> str(R.string.setting_system_page)
    }

    private fun appendValue(sb: StringBuilder, i: Int) {
        when (i) {
            0 -> sb.append(
                when (prefs.theme) {
                    Prefs.THEME_ARC -> "Arc"
                    Prefs.THEME_BALANCED -> "Balanced"
                    else -> "Stacked"
                }
            )
            1 -> {
                val ms = prefs.refreshMs
                if (ms < 1000) {
                    sb.append("0.")
                    sb.append(ms / 100)
                    sb.append(' '); sb.append(str(R.string.second_short))
                } else {
                    sb.append(ms / 1000)
                    sb.append(' '); sb.append(str(R.string.second_short))
                }
            }
            2 -> sb.append(str(if (prefs.detailPage) R.string.on else R.string.off))
            3 -> sb.append(str(if (prefs.engineeringPage) R.string.on else R.string.off))
            else -> sb.append(str(if (prefs.systemPage) R.string.on else R.string.off))
        }
    }

    private var downY = 0f

    override fun onRawTouch(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            downY = event.y
            return
        }
        if (event.actionMasked != MotionEvent.ACTION_UP) return
        var row = -1
        for (i in ROW_Y.indices) if (Math.abs(event.y - (ROW_Y[i] + 8f)) < ROW_HIT) row = i
        // Dokunusun basi ve sonu ayni satirda olmali
        if (row < 0 || Math.abs(downY - event.y) > ROW_HIT) return

        when (row) {
            0 -> prefs.theme = (prefs.theme + 1) % Prefs.THEME_COUNT
            1 -> {
                val opts = Prefs.REFRESH_OPTIONS
                var idx = -1
                for (i in opts.indices) if (opts[i] == prefs.refreshMs) idx = i
                prefs.refreshMs = opts[(if (idx < 0) 1 else idx + 1) % opts.size]
            }
            2 -> prefs.detailPage = !prefs.detailPage
            3 -> prefs.engineeringPage = !prefs.engineeringPage
            else -> prefs.systemPage = !prefs.systemPage
        }
        invalidate()
        onChanged()
    }
}
