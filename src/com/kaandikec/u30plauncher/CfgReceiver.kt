package com.kaandikec.u30plauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kaandikec.u30plauncher.store.Prefs

/**
 * adb'den yapilandirma.
 *
 * `android.permission.DUMP` ile korunur: shell ve root bu izne sahiptir,
 * normal uygulamalar degildir. Manifest'te tanimli olmasi maliyet getirmez —
 * tetiklenmedikce surec baslatmaz, uygulamayi ayakta tutmaz.
 */
class CfgReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val prefs = Prefs(ctx)
        intent.getStringExtra("theme")?.let {
            prefs.theme = when (it.lowercase()) {
                "arc" -> Prefs.THEME_ARC
                "balanced" -> Prefs.THEME_BALANCED
                else -> Prefs.THEME_STACKED
            }
        }
        if (intent.hasExtra("refresh_ms")) {
            prefs.refreshMs = intent.getIntExtra("refresh_ms", 1000).coerceIn(500, 10_000)
        }
        if (intent.hasExtra("detail")) {
            prefs.detailPage = intent.getBooleanExtra("detail", true)
        }
        if (intent.hasExtra("engineering")) {
            prefs.engineeringPage = intent.getBooleanExtra("engineering", true)
        }
    }
}
