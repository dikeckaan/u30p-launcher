package com.kaandikec.u30plauncher.ui

import android.content.Context
import com.kaandikec.u30plauncher.R

/**
 * Temalarin ihtiyac duydugu metinler.
 *
 * Temalar `Context` tasimaz (yalnizca `Snapshot` alip cizerler), bu yuzden
 * cihaz diline gore cozulmus metinler bir kez burada toplanip gecirilir.
 */
class UiStrings(ctx: Context) {
    val unknown: String = ctx.getString(R.string.unknown)
    val charging: String = ctx.getString(R.string.charging)
    val noSim: String = ctx.getString(R.string.no_sim)
    val noPermission: String = ctx.getString(R.string.no_permission)
    val hourShort: String = ctx.getString(R.string.hour_short)
    val minuteShort: String = ctx.getString(R.string.minute_short)
}
