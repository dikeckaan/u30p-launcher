package com.kaandikec.u30plauncher

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.kaandikec.u30plauncher.store.Prefs
import com.kaandikec.u30plauncher.ui.ActionsPage
import com.kaandikec.u30plauncher.ui.AppsPage
import com.kaandikec.u30plauncher.ui.DetailPage
import com.kaandikec.u30plauncher.ui.EngineeringPage
import com.kaandikec.u30plauncher.ui.PageView
import com.kaandikec.u30plauncher.ui.Pager
import com.kaandikec.u30plauncher.ui.SettingsPage
import com.kaandikec.u30plauncher.ui.StatusPage
import com.kaandikec.u30plauncher.ui.WifiPage
import com.kaandikec.u30plauncher.ui.theme.ArcTheme
import com.kaandikec.u30plauncher.ui.theme.BalancedTheme
import com.kaandikec.u30plauncher.ui.theme.StackedTheme
import com.kaandikec.u30plauncher.ui.theme.Theme

/**
 * Etkileşim modeli
 *
 *   KILITLI   bilgi görünür, hicbir dokunma islenmez     -- uzun bas --> ACIK
 *   ACIK      yatay: bilgi sayfalari
 *             yukari: uygulamalar
 *             asagi:  sistem (aksiyon / WiFi / ayar)
 *             30 sn hareketsizlik --> KILITLI
 *
 * Cihaz cepte tasindigi icin kilit tum ekrani kapsar, yalnizca aksiyon
 * sayfasini degil: kilitsiz bilgi sayfalarinda kazara kaydirmalar sayfa
 * degistiriyor ve uygulama acabiliyordu.
 */
class LauncherActivity : Activity() {
    companion object {
        /**
         * 30 sn cok kisaydi: ekrana bakip dusunurken kilitleniyor, sonraki
         * kaydirma "calismiyor" gibi hissettiriyordu.
         */
        private const val RELOCK_MS = 120_000L
    }

    private enum class Mode { INFO, APPS, SYSTEM }

    private lateinit var prefs: Prefs
    private lateinit var hub: DataHub
    private lateinit var pager: Pager
    private lateinit var actionsPage: ActionsPage
    private lateinit var appsPage: AppsPage
    private lateinit var wifiPage: WifiPage
    private lateinit var settingsPage: SettingsPage

    private val relockHandler = Handler(Looper.getMainLooper())
    private val relock = Runnable { lock() }
    private var locked = true
    private var mode = Mode.INFO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashGuard.install(applicationContext)

        prefs = Prefs(this)
        hub = DataHub(this)

        actionsPage = ActionsPage(this)
        appsPage = AppsPage(this)
        wifiPage = WifiPage(this)
        settingsPage = SettingsPage(this, prefs) { scheduleRelock() }

        pager = Pager(this)
        pager.onLongPress = { unlock() }
        pager.onInteraction = { if (!locked) scheduleRelock() }
        pager.onLockedTouch = { pager.currentPage?.flashLock() }
        pager.onSwipeUp = { onSwipeUp() }
        pager.onSwipeDown = { onSwipeDown() }

        showInfo()
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
        // Ekran kapanip acildiginda her zaman kilitli baslasin.
        locked = true
        pager.locked = true
        if (mode != Mode.INFO) showInfo()
    }

    /**
     * HOME uygulamasi geri tusuyla kapanmamalidir — kapanirsa cihazda
     * gosterilecek baska bir sey kalmaz.
     */
    override fun onBackPressed() {
        when {
            mode != Mode.INFO -> showInfo()
            !locked -> lock()
            else -> pager.index = 0
        }
    }

    /** HOME tusuna basildiginda basa don ve kilitle. */
    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        lock()
    }

    private fun themeFor(id: Int): Theme = when (id) {
        Prefs.THEME_ARC -> ArcTheme
        Prefs.THEME_BALANCED -> BalancedTheme
        else -> StackedTheme
    }

    // ------------------------------------------------------------ modlar

    private fun showInfo() {
        mode = Mode.INFO
        val list = ArrayList<PageView>(3)
        list.add(StatusPage(this) { themeFor(prefs.theme) })
        if (prefs.detailPage) list.add(DetailPage(this))
        if (prefs.engineeringPage) list.add(EngineeringPage(this))
        pager.setPages(list)
        pager.longPressEnabled = true
        pager.locked = locked
        pager.update(hub.snapshot)
    }

    private fun showApps() {
        mode = Mode.APPS
        appsPage.update(hub.snapshot)
        appsPage.ensureLoaded()
        pager.setPages(listOf(appsPage))
        pager.longPressEnabled = false
        scheduleRelock()
    }

    private fun showSystem() {
        mode = Mode.SYSTEM
        pager.setPages(listOf(actionsPage, wifiPage, settingsPage))
        pager.longPressEnabled = false
        actionsPage.update(hub.snapshot)
        actionsPage.refreshState()
        wifiPage.update(hub.snapshot)
        wifiPage.refreshState()
        scheduleRelock()
    }

    // ------------------------------------------------------------ hareketler

    private fun onSwipeUp() {
        when (mode) {
            Mode.INFO -> if (!locked) showApps()
            Mode.SYSTEM -> showInfo()
            Mode.APPS -> {}
        }
    }

    private fun onSwipeDown() {
        when (mode) {
            Mode.INFO -> if (!locked) showSystem()
            // Pager yalnizca liste daha fazla kaydirilamiyorsa buraya gelir
            Mode.APPS -> showInfo()
            Mode.SYSTEM -> {}
        }
    }

    // ------------------------------------------------------------ kilit

    private fun unlock() {
        if (!locked) return
        locked = false
        pager.locked = false
        scheduleRelock()
        pager.invalidate()
    }

    private fun lock() {
        relockHandler.removeCallbacks(relock)
        locked = true
        pager.locked = true
        if (mode != Mode.INFO) showInfo() else pager.invalidate()
    }

    private fun scheduleRelock() {
        relockHandler.removeCallbacks(relock)
        relockHandler.postDelayed(relock, RELOCK_MS)
    }
}
