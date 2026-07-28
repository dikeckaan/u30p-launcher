package com.kaandikec.u30plauncher

import android.app.Activity
import android.os.Bundle
import com.kaandikec.u30plauncher.store.Prefs
import com.kaandikec.u30plauncher.ui.StatusPage
import com.kaandikec.u30plauncher.ui.theme.StackedTheme

class LauncherActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var hub: DataHub
    private lateinit var page: StatusPage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        hub = DataHub(this)
        page = StatusPage(this) { StackedTheme }
        page.pageCount = 1
        page.pageIndex = 0
        setContentView(page)
    }

    override fun onResume() {
        super.onResume()
        hub.resume { page.update(it) }
    }

    override fun onPause() {
        super.onPause()
        hub.pause()
    }
}
