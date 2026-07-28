package com.kaandikec.u30plauncher

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View

class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(BootProbeView(this))
    }
}

/** Task 1 iskeleti — derleme hattini dogrular, Task 10'da kaldirilir. */
private class BootProbeView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        canvas.drawText("U30P", 120f, 110f, paint)
        canvas.drawText("${width}x${height}", 120f, 136f, paint)
    }
}
