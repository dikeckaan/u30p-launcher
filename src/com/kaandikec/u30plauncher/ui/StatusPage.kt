package com.kaandikec.u30plauncher.ui

import android.content.Context
import android.graphics.Canvas
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.ui.theme.Theme

/** Sayfa 1. Cizimi secili temaya devreder; tema her karede yeniden okunur. */
class StatusPage(ctx: Context, private val themeOf: () -> Theme) : PageView(ctx) {
    private val sb = StringBuilder(32)

    override fun draw(c: Canvas, s: Snapshot) {
        themeOf().draw(c, s, sb)
    }
}
