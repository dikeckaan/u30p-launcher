package com.kaandikec.u30plauncher

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.kaandikec.u30plauncher.store.Prefs
import com.kaandikec.u30plauncher.ui.ActionsPage
import com.kaandikec.u30plauncher.ui.DetailPage
import com.kaandikec.u30plauncher.ui.EngineeringPage
import com.kaandikec.u30plauncher.ui.PageView
import com.kaandikec.u30plauncher.ui.Pager
import com.kaandikec.u30plauncher.ui.SettingsPage
import com.kaandikec.u30plauncher.ui.StatusPage
import com.kaandikec.u30plauncher.ui.theme.ArcTheme
import com.kaandikec.u30plauncher.ui.theme.BalancedTheme
import com.kaandikec.u30plauncher.ui.theme.StackedTheme
import com.kaandikec.u30plauncher.ui.theme.Theme

class LauncherActivity : Activity() {
    companion object {
        /** Kilit acik kaldiginda kendiliginden kapanma suresi. */
        private const val RELOCK_MS = 30_000L
    }

    private lateinit var prefs: Prefs
    private lateinit var hub: DataHub
    private lateinit var pager: Pager
    private lateinit var actionsPage: ActionsPage
    private lateinit var settingsPage: SettingsPage

    private val relockHandler = Handler(Looper.getMainLooper())
    private val relock = Runnable { lock() }
    private var locked = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashGuard.install(applicationContext)

        prefs = Prefs(this)
        hub = DataHub(this)

        actionsPage = ActionsPage(this)
        settingsPage = SettingsPage(this, prefs) { onSettingsChanged() }

        pager = Pager(this)
        pager.onLongPress = { unlock() }
        pager.onInteraction = { if (!locked) scheduleRelock() }

        showInfoPages()
        setContentView(pager)
    }

    override fun onResume() {
        super.onResume()
        hub.resume { pager.update(it) }
    }

    override fun onPause() {
        super.onPause()
        hub.pause()
        relockHandler.removeCallbacks(relock)
        // Ekran kapanip acildiginda kilitli baslasin.
        locked = true
    }

    /** Kilit acikken geri tusu kilidi kapatir, uygulamadan cikmaz. */
    override fun onBackPressed() {
        if (!locked) lock() else super.onBackPressed()
    }

    private fun themeFor(id: Int): Theme = when (id) {
        Prefs.THEME_ARC -> ArcTheme
        Prefs.THEME_BALANCED -> BalancedTheme
        else -> StackedTheme
    }

    private fun showInfoPages() {
        val list = ArrayList<PageView>(3)
        list.add(StatusPage(this) { themeFor(prefs.theme) })
        if (prefs.detailPage) list.add(DetailPage(this))
        if (prefs.engineeringPage) list.add(EngineeringPage(this))
        pager.setPages(list)
    }

    private fun unlock() {
        if (!locked) return
        locked = false
        // Kilit acikken uzun basma aksiyon sayfasinin isi; kilidi tekrar acmaz.
        pager.longPressEnabled = false
        pager.setPages(listOf(actionsPage, settingsPage))
        actionsPage.update(hub.snapshot)
        actionsPage.refreshState()
        scheduleRelock()
    }

    private fun lock() {
        relockHandler.removeCallbacks(relock)
        if (locked) return
        locked = true
        pager.longPressEnabled = true
        showInfoPages()
        pager.update(hub.snapshot)
    }

    private fun scheduleRelock() {
        relockHandler.removeCallbacks(relock)
        relockHandler.postDelayed(relock, RELOCK_MS)
    }

    /** Ayar degisikligi sayfa listesini etkileyebilir; kilitliyken yeniden kur. */
    private fun onSettingsChanged() {
        scheduleRelock()
    }
}
