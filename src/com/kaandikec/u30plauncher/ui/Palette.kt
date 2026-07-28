package com.kaandikec.u30plauncher.ui

object Palette {
    const val BG = 0xFF000000.toInt()
    const val FG = 0xFFFFFFFF.toInt()
    const val DIM = 0xFF8A8A8E.toInt()
    const val DIM2 = 0xFF5A5A5F.toInt()
    const val DOWN = 0xFF34C759.toInt()
    const val UP = 0xFF0A84FF.toInt()
    const val WARN = 0xFFFF9F0A.toInt()
    const val ERR = 0xFFFF453A.toInt()
    const val TRACK = 0xFF26262A.toInt()
    const val HAIRLINE = 0xFF1A1A1C.toInt()

    /** Pil yuzdesine gore dolgu rengi. */
    fun batteryColor(pct: Int): Int = when {
        pct < 0 -> DIM2
        pct > 30 -> DOWN
        pct > 15 -> WARN
        else -> ERR
    }
}
