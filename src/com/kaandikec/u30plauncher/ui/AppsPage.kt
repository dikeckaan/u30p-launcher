package com.kaandikec.u30plauncher.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.provider.Settings
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.kaandikec.u30plauncher.R
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.store.AppUsage
import com.kaandikec.u30plauncher.ui.theme.ThemeUtil

/**
 * Uygulama izgarasi: satir basina 3 simge, ekran basina 9 uygulama.
 *
 * Neden izgara: 240x240'ta dikey liste ekran basina 5 satir gosteriyordu ve
 * 24 uygulamanin sonuna inmek bes kaydirma aliyordu. Izgarada iki kaydirmada
 * hepsi geziliyor, simge zaten etiketten hizli taniniyor.
 *
 * Siralama en cok acilana gore: bir MiFi'de kurulu uygulamalarin cogu hic
 * acilmaz, alfabetik siralama gunluk kullanilani dibe atiyordu.
 *
 * Simgeye basili tutmak ustune kucuk bir menu acar (bilgi / kaldir). Menu ayri
 * sayfa degil: 240x240'ta ayri sayfa acmak hem agir hem baglami kopariyordu.
 */
class AppsPage(ctx: Context) : PageView(ctx) {
    companion object {
        private const val COLS = 3
        private const val CELL_W = 58f
        private const val CELL_H = 60f
        private const val ICON = 36
        private const val TOP = 30f
        private const val VISIBLE_BOTTOM = 226f

        /**
         * Simgeye basili tutma suresi. SettingsPage ile ayni deger: kaydirma
         * ile karismasin diye kisa, ama kasitli olacak kadar uzun.
         */
        private const val LONG_PRESS_MS = 450L

        // Basili tutma menusunun olculeri; panel ekranda dikey ortali durur
        private const val MENU_W = 160f
        private const val MENU_TITLE_H = 30f
        private const val MENU_ROW_H = 34f
        private const val MENU_INFO = 0
        private const val MENU_UNINSTALL = 1
    }

    private val label = ThemeUtil.text(8.5f, Palette.DIM).apply {
        textAlign = Paint.Align.CENTER
    }
    private val hint = ThemeUtil.text(9f, Palette.DIM2)
    private val marker = ThemeUtil.fill(Palette.DOWN)
    private val iconPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val highlight = ThemeUtil.fill(0x1FFFFFFF)

    // Menu katmani: izgarayi karartan ortu + panel + secenek metinleri
    private val scrim = ThemeUtil.fill(0xC8000000.toInt())
    private val menuBg = ThemeUtil.fill(0xFF1C1C1E.toInt())
    private val menuBorder = ThemeUtil.stroke(Palette.TRACK, 1f)
    private val menuTitle = ThemeUtil.text(9.5f, Palette.DIM).apply {
        textAlign = Paint.Align.CENTER
    }
    private val menuItem = ThemeUtil.text(12f, Palette.FG)
    private val menuItemOff = ThemeUtil.text(12f, Palette.DIM2)
    private val menuHair = ThemeUtil.hairline(Palette.HAIRLINE)

    private val usage = AppUsage(ctx)
    private val labels = ArrayList<String>(32)
    private val icons = ArrayList<Bitmap?>(32)
    private val intents = ArrayList<Intent>(32)
    private val keys = ArrayList<String>(32)
    private val packages = ArrayList<String>(32)
    private val removable = ArrayList<Boolean>(32)

    private val iconSrc = Rect()
    private val iconDst = Rect()

    // Panel dikdortgeni bir kez hesaplanir; cizim yolunda yeniden uretilmez
    private val menuTop = Geom.CY - (MENU_TITLE_H + 2f * MENU_ROW_H) / 2f
    private val menuLeft = Geom.CX - MENU_W / 2f
    private val menuRight = Geom.CX + MENU_W / 2f
    private val menuBottom = menuTop + MENU_TITLE_H + 2f * MENU_ROW_H

    private var scrollY = 0f
    private var maxScroll = 0f
    private var downY = 0f
    private var downScroll = 0f
    private var dragging = false
    private var pressed = -1
    private var longPressFired = false
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    private var menuOpen = false
    private var menuIndex = -1
    private var menuPressed = -1

    /**
     * Menuyu acan basili tutmanin GERI KALANINI yok say.
     *
     * Menu parmak hala basiliyken aciliyor; ayni hareketin ACTION_UP'i bir
     * anda menu secenegini tetikleyip veya paneli kapatip menuyu ise
     * yaramaz kiliyordu. Bu bayrak o birakmayi yutar.
     */
    private var menuArmingUp = false

    override val showDots: Boolean get() = false
    override val wantsRawTouch: Boolean get() = true

    /**
     * Paket listesi degisti; bir sonraki acilista yeniden okunmali.
     *
     * Liste sureci boyunca bir kez okunuyordu. Launcher HOME oldugu icin
     * surec gunlerce yasiyor; sonradan kurulan uygulama hic gorunmuyor,
     * kaldirilan ise izgarada kaliyordu. Yeniden okuma burada degil, sayfa
     * acilirken yapiliyor: simge rasterlemek pahali, yayin aninda yapmanin
     * anlami yok.
     */
    fun markStale() {
        stale = true
    }

    private var stale = false

    /** Acilista bir kez kurulur; siralama her acilista tazelenir. */
    fun ensureLoaded() {
        if (labels.isEmpty() || stale) load() else resort()
    }

    private fun load() {
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found: List<ResolveInfo> = try {
            pm.queryIntentActivities(probe, 0)
        } catch (_: Throwable) {
            return
        }

        val rows = ArrayList<Entry>(found.size)
        for (ri in found) {
            val ai = ri.activityInfo ?: continue
            if (ai.packageName == context.packageName) continue
            val name = try {
                ri.loadLabel(pm)?.toString() ?: ai.packageName
            } catch (_: Throwable) {
                ai.packageName
            }
            val icon = try {
                rasterize(ri.loadIcon(pm))
            } catch (_: Throwable) {
                null
            }
            val key = ai.packageName + "/" + ai.name
            val i = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(ai.packageName, ai.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            rows.add(Entry(name, icon, i, key, ai.packageName, canRemove(ai.applicationInfo)))
        }
        entries = rows
        stale = false
        resort()
    }

    /**
     * Sistem bolumundeki uygulama kaldirilamaz; guncellenmis sistem uygulamasi
     * da yalnizca guncellemeyi kaldirir, uygulamanin kendisi kalir. Ikisini de
     * "kaldirilamaz" sayip menude pasif gosteriyoruz.
     */
    private fun canRemove(ai: ApplicationInfo?): Boolean {
        ai ?: return false
        val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        return (ai.flags and systemFlags) == 0
    }

    private class Entry(
        val label: String,
        val icon: Bitmap?,
        val intent: Intent,
        val key: String,
        val pkg: String,
        val removable: Boolean
    )

    private var entries: List<Entry> = emptyList()

    /** Cok acilan uste; esitlikte alfabetik. */
    private fun resort() {
        val sorted = entries.sortedWith(
            compareByDescending<Entry> { usage.count(it.key) }.thenBy { it.label.lowercase() }
        )
        labels.clear(); icons.clear(); intents.clear(); keys.clear()
        packages.clear(); removable.clear()
        for (e in sorted) {
            labels.add(e.label); icons.add(e.icon); intents.add(e.intent); keys.add(e.key)
            packages.add(e.pkg); removable.add(e.removable)
        }
        // Siralama degistiyse menu indeksi baska uygulamaya kayar; kapat
        closeMenu()
        val rowCount = (labels.size + COLS - 1) / COLS
        val content = rowCount * CELL_H
        val viewport = VISIBLE_BOTTOM - TOP
        maxScroll = if (content > viewport) content - viewport else 0f
        if (scrollY > maxScroll) scrollY = maxScroll
        invalidate()
    }

    private fun rasterize(d: android.graphics.drawable.Drawable?): Bitmap? {
        d ?: return null
        val bmp = Bitmap.createBitmap(ICON, ICON, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, ICON, ICON)
        d.draw(Canvas(bmp))
        return bmp
    }

    private fun cellCx(col: Int): Float = Geom.CX + (col - 1) * CELL_W

    override fun draw(c: Canvas, s: Snapshot) {
        if (labels.isEmpty()) {
            Geom.centerText(c, str(R.string.apps_empty), 110f, hint)
            return
        }

        c.save()
        c.clipRect(0f, TOP, Geom.W, VISIBLE_BOTTOM)
        for (i in labels.indices) {
            val row = i / COLS
            val col = i % COLS
            val y = TOP + row * CELL_H - scrollY
            if (y > VISIBLE_BOTTOM || y + CELL_H < TOP) continue
            drawCell(c, i, cellCx(col), y)
        }
        c.restore()

        drawScrollbar(c)

        if (menuOpen) drawMenu(c)
    }

    private fun drawCell(c: Canvas, i: Int, cx: Float, y: Float) {
        if (i == pressed) {
            c.drawRoundRect(cx - 26f, y + 1f, cx + 26f, y + CELL_H - 5f, 10f, 10f, highlight)
        }
        val icon = icons[i]
        if (icon != null) {
            iconSrc.set(0, 0, icon.width, icon.height)
            val left = (cx - ICON / 2f).toInt()
            val top = (y + 6f).toInt()
            iconDst.set(left, top, left + ICON, top + ICON)
            c.drawBitmap(icon, iconSrc, iconDst, iconPaint)
        }
        // Etiket hucreye sigmiyorsa kirp; ... eklemek 8.5px'te yer kaybettiriyor
        val name = labels[i]
        var text: CharSequence = name
        if (Geom.textWidth(name, label) > CELL_W - 4f) {
            var n = name.length
            while (n > 1 && Geom.textWidth(name.substring(0, n), label) > CELL_W - 4f) n--
            text = name.substring(0, n)
        }
        c.drawText(text, 0, text.length, cx, y + ICON + 17f, label)
    }

    private fun drawMenu(c: Canvas) {
        // Ortu once: altta kalan izgara "dokunma bunu, menuyu kullan" der
        c.drawRect(0f, 0f, Geom.W, Geom.H, scrim)
        c.drawRoundRect(menuLeft, menuTop, menuRight, menuBottom, 14f, 14f, menuBg)
        c.drawRoundRect(menuLeft, menuTop, menuRight, menuBottom, 14f, 14f, menuBorder)

        // Baslik: hangi uygulama; panele sigmiyorsa kirpilir
        val name = labels.getOrNull(menuIndex) ?: ""
        var title: CharSequence = name
        val maxW = MENU_W - 16f
        if (Geom.textWidth(name, menuTitle) > maxW) {
            var n = name.length
            while (n > 1 && Geom.textWidth(name.substring(0, n), menuTitle) > maxW) n--
            title = name.substring(0, n)
        }
        Geom.centerText(c, title, menuTop + 9f, menuTitle)
        Geom.hairline(c, menuTop + MENU_TITLE_H, MENU_W / 2f - 8f, menuHair)

        drawMenuRow(c, MENU_INFO, str(R.string.app_menu_info), true)
        drawMenuRow(c, MENU_UNINSTALL, str(R.string.app_menu_uninstall), removableAt(menuIndex))
    }

    private fun drawMenuRow(c: Canvas, item: Int, text: String, enabled: Boolean) {
        val top = menuTop + MENU_TITLE_H + item * MENU_ROW_H
        if (enabled && item == menuPressed) {
            c.drawRoundRect(
                menuLeft + 4f, top + 2f, menuRight - 4f, top + MENU_ROW_H - 2f, 8f, 8f, highlight
            )
        }
        Geom.textAt(c, text, menuLeft + 16f, top + 11f, if (enabled) menuItem else menuItemOff)
    }

    private fun drawScrollbar(c: Canvas) {
        if (maxScroll <= 0f) return
        val trackTop = TOP + 4f
        val trackH = (VISIBLE_BOTTOM - 4f) - trackTop
        val viewport = VISIBLE_BOTTOM - TOP
        val rowCount = (labels.size + COLS - 1) / COLS
        val thumbH = (trackH * viewport / (rowCount * CELL_H)).coerceAtLeast(14f)
        val top = trackTop + (trackH - thumbH) * (scrollY / maxScroll)
        c.drawRoundRect(232f, top, 235f, top + thumbH, 1.5f, 1.5f, marker)
    }

    private fun cellAt(x: Float, y: Float): Int {
        if (y < TOP || y > VISIBLE_BOTTOM) return -1
        val row = ((y - TOP + scrollY) / CELL_H).toInt()
        var col = -1
        for (k in 0 until COLS) if (Math.abs(x - cellCx(k)) < CELL_W / 2f) col = k
        if (col < 0) return -1
        val idx = row * COLS + col
        return if (idx in labels.indices) idx else -1
    }

    override fun onRawTouch(event: MotionEvent) {
        if (menuOpen) {
            handleMenuTouch(event)
            return
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                downScroll = scrollY
                dragging = false
                longPressFired = false
                pressed = cellAt(event.x, event.y)
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
                // Basili tutma menuyu actiysa birakma uygulamayi ACMASIN
                if (!dragging && !longPressFired && pressed >= 0 && pressed == cellAt(event.x, event.y)) {
                    launch(pressed)
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

    private fun handleMenuTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                menuArmingUp = false
                menuPressed = enabledMenuItemAt(event.x, event.y)
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                if (menuArmingUp) return
                val it = enabledMenuItemAt(event.x, event.y)
                if (it != menuPressed) {
                    menuPressed = it
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                // Menuyu acan hareketin birakmasi; secim veya kapatma sayilmaz
                if (menuArmingUp) {
                    menuArmingUp = false
                    menuPressed = -1
                    invalidate()
                    return
                }
                val it = enabledMenuItemAt(event.x, event.y)
                if (it >= 0 && it == menuPressed) {
                    executeMenu(it)
                } else if (!pointInPanel(event.x, event.y)) {
                    // Disina dokunmak menuyu kapatir
                    closeMenu()
                } else {
                    menuPressed = -1
                    invalidate()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                menuArmingUp = false
                menuPressed = -1
                invalidate()
            }
        }
    }

    override fun canScrollVertically(fingerDown: Boolean): Boolean =
        // Menu acikken dikey hareketi sayfaya birakma; Pager mod degistirip
        // izgaradan cikmasin, menu modal kalsin
        if (menuOpen) true
        else if (fingerDown) scrollY > 0.5f else scrollY < maxScroll - 0.5f

    /**
     * Simgeye basili tutmak menuyu acar.
     *
     * Kaydirmayla ayrilmasi: dokunma izgarayi gezmeli, her dokunusta menu
     * dayatmamali. clearPendingLongPress adi View.cancelLongPress ile
     * catismasin diye ayri secildi (SettingsPage ile ayni desen).
     */
    private val longPress = Runnable {
        if (pressed >= 0 && !dragging) {
            longPressFired = true
            openMenu(pressed)
            pressed = -1
            invalidate()
        }
    }

    private fun clearPendingLongPress() = removeCallbacks(longPress)

    private fun openMenu(index: Int) {
        menuOpen = true
        menuIndex = index
        menuPressed = -1
        menuArmingUp = true
    }

    private fun closeMenu() {
        menuOpen = false
        menuIndex = -1
        menuPressed = -1
        menuArmingUp = false
        invalidate()
    }

    private fun removableAt(i: Int): Boolean = removable.getOrNull(i) ?: false

    /** Panel dikdortgeni icinde mi? */
    private fun pointInPanel(x: Float, y: Float): Boolean =
        x >= menuLeft && x <= menuRight && y >= menuTop && y <= menuBottom

    /** Noktadaki secenek satiri; secenek yoksa -1. Baslik bandi da -1. */
    private fun menuItemAt(x: Float, y: Float): Int {
        if (x < menuLeft || x > menuRight) return -1
        val rowsTop = menuTop + MENU_TITLE_H
        if (y < rowsTop || y > menuBottom) return -1
        val idx = ((y - rowsTop) / MENU_ROW_H).toInt()
        return if (idx < 0) -1 else if (idx > MENU_UNINSTALL) -1 else idx
    }

    /** Yalnizca ETKIN secenegi dondurur; pasif "Kaldir" -1 verir. */
    private fun enabledMenuItemAt(x: Float, y: Float): Int {
        val it = menuItemAt(x, y)
        if (it == MENU_UNINSTALL && !removableAt(menuIndex)) return -1
        return it
    }

    private fun executeMenu(item: Int) {
        val index = menuIndex
        closeMenu()
        when (item) {
            MENU_INFO -> openAppInfo(index)
            MENU_UNINSTALL -> uninstall(index)
        }
    }

    private fun openAppInfo(index: Int) {
        val pkg = packages.getOrNull(index) ?: return
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
        }
    }

    private fun uninstall(index: Int) {
        if (!removableAt(index)) return
        val pkg = packages.getOrNull(index) ?: return
        try {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
        }
    }

    private fun launch(index: Int) {
        val intent = intents.getOrNull(index) ?: return
        try {
            context.startActivity(intent)
            usage.record(keys[index])
        } catch (_: Throwable) {
            labels.clear(); icons.clear(); intents.clear(); keys.clear()
            packages.clear(); removable.clear()
            entries = emptyList()
            load()
        }
    }
}
