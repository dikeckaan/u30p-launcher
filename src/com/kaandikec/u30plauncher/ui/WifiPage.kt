package com.kaandikec.u30plauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.kaandikec.u30plauncher.R
import com.kaandikec.u30plauncher.core.ArpParser
import com.kaandikec.u30plauncher.core.Client
import com.kaandikec.u30plauncher.core.QrEncoder
import com.kaandikec.u30plauncher.core.QrMatrix
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.core.SoftApInfo
import com.kaandikec.u30plauncher.core.SoftApParser
import com.kaandikec.u30plauncher.core.WifiFactory
import com.kaandikec.u30plauncher.core.WifiCredGen
import com.kaandikec.u30plauncher.source.RootShell
import com.kaandikec.u30plauncher.ui.theme.ThemeUtil

/**
 * WiFi bilgisi: SSID, parola ve bagli istemciler.
 *
 * Bir MiFi'de en sik ihtiyac duyulan ekran budur — misafire ag adini ve
 * parolayi gostermek. Parola yalnizca root ile okunabilir; framework API'si
 * imza izni olmadan vermez.
 *
 * Uc katman var:
 *   ANA   liste, kaydirilabilir (istemci sayisi 3'u asiyor)
 *   QR    parolaya uzun basinca; misafir okutup baglanir
 *   YENI  SSID'ye uzun basinca; rastgele kimlik uretir ve ONAY ister
 *
 * Kilit katmanindadir: parola surekli ekranda durmasin.
 */
class WifiPage(ctx: Context) : PageView(ctx) {
    companion object {
        private const val CLIENT_REFRESH_MS = 5000L

        /**
         * Ad tablosu istemci listesinden cok daha yavas degisir ve `dumpsys`
         * cagrisi `cat /proc/net/arp`'a gore pahalidir; 5 sn'de bir cagirmak
         * root kabugunu bos yere mesgul ediyordu.
         */
        private const val NAME_REFRESH_MS = 30_000L
        private const val LONG_PRESS_MS = 450L

        private const val CONF =
            "/data/misc/apexdata/com.android.wifi/WifiConfigStoreSoftAp.xml"

        /**
         * Ana bilgisayar adlari yalnizca burada.
         *
         * Cihazda dnsmasq yok ve /data/misc/dhcp bos: Android'in kendi DHCP
         * sunucusu kiralari bellekte tutuyor, diske hic yazmiyor. Adin gectigi
         * tek yer network_stack'in halka tamponundaki kira gunlugu. Tampon
         * sinirli oldugu icin coktan susmus bir istemcinin adi kaybolabilir;
         * o durumda MAC gosterilir.
         */
        private const val NAMES_CMD =
            "dumpsys network_stack 2>/dev/null | grep 'hostname:' | tail -40"

        private const val HW_KEY = "hwAddr: "
        private const val HOST_KEY = "hostname: "

        private const val LIST_TOP = 116f
        private const val LIST_BOTTOM = 186f

        /**
         * Alt aksiyon seridi.
         *
         * Once bu isler sag kenardaki 10 px'lik ikonlara ve GIZLI uzun basmaya
         * bagliydi; yuvarlak ekranda kenara yakin kucuk hedefe basmak zor ve
         * uzun basmanin varligi hicbir yerde yazmiyordu. Uc is de artik
         * etiketli ve genis hedeflerle gorunur.
         */
        private const val BAR_CY = 202f
        private const val BAR_LABEL_Y = 220f
        private val BAR_CX = floatArrayOf(52f, 120f, 188f)
        private const val BAR_HIT_X = 34f
        private const val BAR_HIT_Y = 26f
        private const val BAR_QR = 0
        private const val BAR_NEW = 1
        private const val BAR_RESET = 2
        private const val ROW_H = 30f

        private const val ZONE_SSID = 0
        private const val ZONE_PASS = 1

        private const val VIEW_MAIN = 0
        private const val VIEW_QR = 1
        private const val VIEW_NEW = 2

        private const val APPLY_IDLE = 0
        private const val APPLY_RUNNING = 1
        private const val APPLY_LIVE = 9
        private const val APPLY_OK = 2
        private const val APPLY_FAIL = 3

        /**
         * Karekodun sigacagi en buyuk kare.
         *
         * 240 caplik dairenin ic teget karesi 169.7 px; 168 hem tam sayi hem
         * de kenarda bir piksel pay birakiyor.
         */
        private const val QR_MAX_PX = 168

        /** Kapatma carpisi ve onay isaretlerinin dokunma yaricapi. */
        private const val HIT = 22f

        private const val CLOSE_CX = 120f
        private const val CLOSE_CY = 218f
        private const val NEW_CANCEL_CX = 70f
        private const val NEW_OK_CX = 170f
        private const val NEW_BTN_CY = 190f

        /** Fabrika degerlerine donme dugmesi; iki tusun ortasinda. */
        private const val NEW_RESET_CX = 120f

        private const val FACTORY_MAC = "/mnt/vendor/wifimac.txt"
        private const val FACTORY_KEY = "/mnt/vendor/defaulthotspotkey_1.txt"

        /**
         * app_process ile root altinda calisan yardimci sinif.
         *
         * Uygulama process'i imza izni olan setSoftApConfiguration'i cagiramaz;
         * izin denetimi cagiran uid uzerinden yapildigi icin ayni cagri uid 0
         * altinda geciyor.
         */
        private const val SOFTAP_TOOL = "com.kaandikec.u30plauncher.root.SoftApTool"

        /** Aracin basarida bastigi isaret; baska hicbir sey basari sayilmaz. */
        private const val TOOL_OK = "U30P_SOFTAP_OK"

        /** Hotspot devir daim edildi; kullanicinin yeniden baslatmasi gerekmiyor. */
        private const val TOOL_RESTARTED = "U30P_SOFTAP_RESTARTED"
    }

    private val title = ThemeUtil.text(10f, Palette.DIM2)
    private val label = ThemeUtil.text(9f, Palette.DIM2)
    private val ssidPaint = ThemeUtil.text(15f, Palette.FG)
    private val passPaint = ThemeUtil.text(14f, Palette.DOWN)
    private val ipPaint = ThemeUtil.text(11f, Palette.FG)
    private val subPaint = ThemeUtil.text(8f, Palette.DIM)
    private val hint = ThemeUtil.text(9f, Palette.DIM2)
    private val warnPaint = ThemeUtil.text(9f, Palette.WARN)
    private val errPaint = ThemeUtil.text(9f, Palette.ERR)
    private val hair = ThemeUtil.hairline(Palette.HAIRLINE)
    private val thumb = ThemeUtil.fill(Palette.DOWN)
    private val iconStroke = ThemeUtil.stroke(Palette.DIM2, 1.2f)
    private val iconFill = ThemeUtil.fill(Palette.DIM2)
    private val closePaint = ThemeUtil.stroke(Palette.FG, 2.5f)

    /** Karekod ekrani beyaz; oradaki carpi koyu olmali yoksa kayboluyor. */
    private val closeOnWhite = ThemeUtil.stroke(Palette.BG, 2.5f)

    /** Hotspot devir daim edilince gosterilen olumlu metin. */
    private val okTextPaint = ThemeUtil.text(9f, Palette.DOWN)
    private val barLabel = ThemeUtil.text(8.5f, Palette.DIM)
    private val barLabelDim = ThemeUtil.text(8.5f, Palette.DIM2)
    private val dimStroke = ThemeUtil.stroke(Palette.DIM2, 1.5f)
    private val dimFill = ThemeUtil.fill(Palette.DIM2)
    private val cancelPaint = ThemeUtil.stroke(Palette.DIM, 2.5f)
    private val okPaint = ThemeUtil.stroke(Palette.DOWN, 2.5f)

    /**
     * Karekod her zaman BEYAZ zemine SIYAH cizilir, tema ne olursa olsun:
     * ters kontrastli karekodu tarayicilarin bir kismi hic gormuyor.
     * Kenar yumusatma kapali; yariseffaf modul kenarlari 4-5 px'lik modulde
     * tarayicinin esik secimini bozuyor.
     */
    private val qrWhite = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val qrBlack = Paint().apply {
        color = 0xFF000000.toInt()
        style = Paint.Style.FILL
    }

    private val iconRect = RectF()

    private val ap = SoftApInfo()
    private val clients = ArrayList<Client>(8)

    /** MAC (kucuk harf) -> ana bilgisayar adi. */
    private val names = HashMap<String, String>(8)

    private var lastClientsAt = 0L
    private var lastNamesAt = 0L
    private var loaded = false
    private val sb = StringBuilder(24)

    private var view = VIEW_MAIN

    private val qr = QrMatrix()
    private var qrOk = false
    private var qrScale = 0
    private var qrQuiet = 3
    private var qrBoxL = 0f
    private var qrBoxT = 0f
    private var qrBoxW = 0f

    private var newSsid = ""
    private var newPass = ""
    private var applyState = APPLY_IDLE

    private var scrollY = 0f
    private var maxScroll = 0f
    private var downY = 0f
    private var downScroll = 0f
    private var dragging = false
    private var pressedZone = -1
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    override val showDots: Boolean get() = false
    override val wantsRawTouch: Boolean get() = true

    /** Kilit acildiginda cagrilir. SSID ve parola nadiren degisir, bir kez okunur. */
    /**
     * Sayfa ekrandan cikinca ust katmanlari kapat.
     *
     * `refreshState` yalnizca sistem seridine GIRERKEN cagriliyor; kullanici
     * zaten seritteyken sayfalar arasinda kaydirdiginda hic calismiyordu ve
     * WiFi sayfasi birakildigi katmanla geri geliyordu. Gercekte oldu: sayfa
     * "fabrikaya don" onay ekraninda acildi ve orada duran onay tusu, farkinda
     * olmadan basilirsa kimligi uygulardi.
     *
     * Pager ekran disindaki sayfalari GONE yapiyor; kanca olarak o kullanildi.
     */
    override fun onVisibilityChanged(changedView: android.view.View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView !== this) return
        if (visibility == VISIBLE) {
            // Sayfaya her donuste degerler diskten tazelensin: kimlik bu arada
            // degismis olabilir.
            if (snapshot.rootAvailable) readConfig()
            return
        }
        if (view != VIEW_MAIN || applyState != APPLY_IDLE) {
            view = VIEW_MAIN
            applyState = APPLY_IDLE
        }
    }

    fun refreshState() {
        // Ust katmanlar sayfadan cikilinca kapanmali; kullanici bir sonraki
        // gelisinde beklemedigi bir karekodla karsilasmasin. Burada acikca
        // invalidate lazim: snapshot degismemis olabilir ve `update` o durumda
        // yeniden cizmiyor, karekod ekranda asili kalirdi.
        if (view != VIEW_MAIN) {
            view = VIEW_MAIN
            invalidate()
        }
        if (!snapshot.rootAvailable) return
        readConfig()
        refreshClients(force = true)
        loadFactory()
        refreshNames(force = true)
    }

    /** Root okumasi asenkron: ana thread'de bloke okuma arayuzu dondururdu. */
    private fun refreshClients(force: Boolean) {
        if (!snapshot.rootAvailable) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastClientsAt < CLIENT_REFRESH_MS) return
        lastClientsAt = now
        RootShell.async("cat /proc/net/arp") { arp ->
            if (arp != null) {
                ArpParser.parse(arp, "br0", clients)
                clampScroll()
                // Yeni katilan bir istemcinin adini beklemeden getir; aksi
                // halde 30 sn boyunca MAC gorunuyor.
                if (hasUnnamed()) refreshNames(force = false)
                invalidate()
            }
        }
    }

    private fun refreshNames(force: Boolean) {
        if (!snapshot.rootAvailable) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastNamesAt < NAME_REFRESH_MS) return
        lastNamesAt = now
        RootShell.async(NAMES_CMD) { out ->
            if (out != null) {
                parseLeaseNames(out, names)
                invalidate()
            }
        }
    }

    private fun hasUnnamed(): Boolean {
        for (i in 0 until clients.size) if (names[clients[i].mac] == null) return true
        return false
    }

    /**
     * Kira gunlugunden MAC -> ad eslesmesi cikarir.
     *
     * Ornek satir (tek satirda IKI cift olabilir; once eski kira, sonra
     * yenilenen):
     *   ... hwAddr: 72:af:46:77:66:2d, netAddr: 192.168.0.113/24,
     *   expTime: 86429550,hostname: Mac
     * Sonraki kayit oncekinin uzerine yazilir, boylece en guncel ad kalir.
     *
     * MAC'ler ARP tablosunda da bu gunlukte de kucuk harf geliyor; cizim
     * yolunda buyuk/kucuk donusumu yapmamak icin oldugu gibi anahtarlaniyor.
     */
    private fun parseLeaseNames(text: String, out: HashMap<String, String>) {
        out.clear()
        var i = 0
        while (true) {
            val at = text.indexOf(HW_KEY, i)
            if (at < 0) return
            val macStart = at + HW_KEY.length
            val macEnd = text.indexOf(',', macStart)
            if (macEnd < 0) return
            i = macEnd
            val nameAt = text.indexOf(HOST_KEY, macEnd)
            if (nameAt < 0) return
            // Araya baska bir hwAddr girdiyse bu ad o MAC'e ait degil
            val nextHw = text.indexOf(HW_KEY, macEnd)
            if (nextHw in 0 until nameAt) continue
            var end = nameAt + HOST_KEY.length
            while (end < text.length && text[end] != ',' && text[end] != '\n') end++
            val name = text.substring(nameAt + HOST_KEY.length, end).trim()
            val mac = text.substring(macStart, macEnd)
            // Istemci ad bildirmediyse cerceve "null" yaziyor; MAC daha bilgili
            if (mac.length == 17 && name.isNotEmpty() && name != "null") out[mac] = name
        }
    }

    private fun clampScroll() {
        val content = clients.size * ROW_H
        val viewport = LIST_BOTTOM - LIST_TOP
        maxScroll = if (content > viewport) content - viewport else 0f
        if (scrollY > maxScroll) scrollY = maxScroll
    }

    /**
     * Ust katman acikken her iki yonde de "kaydirabilirim" der.
     *
     * Pager dikey hareketi mod degistirmek icin kullaniyor; karekod acikken
     * yukari kaydirmak uygulama izgarasini aciyor ve karekod gorunmez bir
     * katman olarak asili kaliyordu.
     */
    override fun canScrollVertically(fingerDown: Boolean): Boolean {
        if (view != VIEW_MAIN) return true
        return if (fingerDown) scrollY > 0.5f else scrollY < maxScroll - 0.5f
    }

    override fun draw(c: Canvas, s: Snapshot) {
        if (view == VIEW_QR) {
            drawQr(c)
            return
        }
        if (view == VIEW_NEW) {
            drawNew(c)
            return
        }

        Geom.centerText(c, str(R.string.wifi_title), 14f, title)
        Geom.hairline(c, 30f, 40f, hair)

        if (!s.rootAvailable) {
            Geom.centerText(c, str(R.string.no_root), 110f, hint)
            return
        }
        if (!loaded) {
            Geom.centerText(c, str(R.string.wifi_unreadable), 110f, hint)
            return
        }

        refreshClients(force = false)

        Geom.centerText(c, str(R.string.wifi_network), 34f, label)
        Geom.centerText(c, ap.ssid, 45f, ssidPaint)

        Geom.centerText(c, str(R.string.wifi_password), 66f, label)
        Geom.centerText(c, ap.passphrase, 77f, passPaint)

        Geom.hairline(c, 98f, 74f, hair)

        sb.setLength(0)
        sb.append(str(R.string.wifi_connected_count))
        sb.append("  ")
        sb.append(clients.size)
        if (ap.maxClients != Snapshot.UNKNOWN) {
            sb.append(" / ")
            sb.append(ap.maxClients)
        }
        Geom.centerText(c, sb, 102f, label)

        if (clients.isEmpty()) {
            Geom.centerText(c, str(R.string.wifi_no_clients), 140f, hint)
            drawBar(c)
            return
        }

        c.save()
        c.clipRect(0f, LIST_TOP, Geom.W, LIST_BOTTOM)
        val n = clients.size
        for (i in 0 until n) {
            val y = LIST_TOP + i * ROW_H - scrollY
            if (y > LIST_BOTTOM || y + ROW_H < LIST_TOP) continue
            val cl = clients[i]
            Geom.centerText(c, cl.ip, y + 1f, ipPaint)
            // Ad yoksa MAC: hicbir sey yazmamak "bu kim?" sorusunu cevapsiz birakir
            val name = names[cl.mac]
            Geom.centerText(c, name ?: cl.mac, y + 14f, subPaint)
        }
        c.restore()

        drawScrollbar(c)
        drawBar(c)
    }

    /**
     * Alt aksiyon seridi: karekod / yeni kimlik / fabrikaya don.
     *
     * Hedefler 68x52 px; yuvarlak ekranda kenara tasmayacak sekilde ic tarafa
     * cekildi. Fabrika dugmesi degerler okunamadiysa soluk cizilir ve
     * dokunusa yanit vermez — basilabilir gorunup hicbir sey yapmamasi daha
     * kotu olurdu.
     */
    private fun drawBar(c: Canvas) {
        val factoryReady = factorySsid.isNotEmpty() && factoryKey.isNotEmpty()
        Geom.hairline(c, BAR_CY - 20f, 80f, hair)

        qrIcon(c, BAR_CX[BAR_QR], BAR_CY)
        Geom.centerText(c, str(R.string.wifi_bar_qr), BAR_LABEL_Y, barLabel, BAR_CX[BAR_QR])

        randomIcon(c, BAR_CX[BAR_NEW], BAR_CY)
        Geom.centerText(c, str(R.string.wifi_bar_new), BAR_LABEL_Y, barLabel, BAR_CX[BAR_NEW])

        val cx = BAR_CX[BAR_RESET]
        iconRect.set(cx - 7f, BAR_CY - 7f, cx + 7f, BAR_CY + 7f)
        c.drawArc(iconRect, 120f, 300f, false, if (factoryReady) iconStroke else dimStroke)
        Geom.arrow(c, cx - 6f, BAR_CY - 5f, 4f, true, if (factoryReady) iconFill else dimFill)
        Geom.centerText(
            c, str(R.string.wifi_bar_reset), BAR_LABEL_Y,
            if (factoryReady) barLabel else barLabelDim, cx
        )
    }

    /**
     * SSID ve parolayi diskten tazeler.
     *
     * Once yalnizca BIR KEZ okunuyordu (`if (!loaded)`). Kimlik disaridan
     * degisince — stok arayuzden, ya da bizim kendi "yeni/sifirla" akisimizla —
     * sayfa eski degerleri gostermeye devam ediyordu; kullanici ekrandaki
     * parolayi misafirine verse ise yaramazdi. Okuma tek bir `cat`, sayfa da
     * seyrek aciliyor; her acilista okumak bedava sayilir.
     */
    private fun readConfig() {
        RootShell.async("cat $CONF 2>/dev/null") { xml ->
            if (xml != null && SoftApParser.parse(xml, ap)) loaded = true
            invalidate()
        }
    }

    /** Serit hedefi; -1 = serit disi. */
    private fun barAt(x: Float, y: Float): Int {
        if (Math.abs(y - BAR_CY - 6f) > BAR_HIT_Y) return -1
        for (i in 0 until 3) if (Math.abs(x - BAR_CX[i]) <= BAR_HIT_X) return i
        return -1
    }

    /**
     * Kaydirma cubugu 202'de: SettingsPage'deki 214 bu sayfada calismiyor,
     * cunku liste ekranin altina kadar iniyor ve orada dairenin sag kenari
     * 210'un icine giriyor — cubuk yarisindan kesiliyordu.
     */
    private fun drawScrollbar(c: Canvas) {
        if (maxScroll <= 0f) return
        val trackTop = LIST_TOP + 2f
        val trackH = (LIST_BOTTOM - 4f) - trackTop
        val viewport = LIST_BOTTOM - LIST_TOP
        val thumbH = (trackH * viewport / (clients.size * ROW_H)).coerceAtLeast(12f)
        val top = trackTop + (trackH - thumbH) * (scrollY / maxScroll)
        c.drawRoundRect(202f, top, 205f, top + thumbH, 1.5f, 1.5f, thumb)
    }

    /**
     * Uzun basma ipuclari metin yerine ikonla veriliyor: 240 px'de ipucu
     * satirlari icin yer yok, ayrica metin cevirisi her iki dilde de
     * satiri tasiriyordu.
     */
    private fun randomIcon(c: Canvas, cx: Float, cy: Float) {
        iconRect.set(cx - 5f, cy - 5f, cx + 5f, cy + 5f)
        c.drawArc(iconRect, 30f, 280f, false, iconStroke)
        Geom.arrow(c, cx + 4.5f, cy - 4f, 4f, false, iconFill)
    }

    /** Kucuk karekod amblemi: uc konum bulma karesi + tek modul. */
    private fun qrIcon(c: Canvas, cx: Float, cy: Float) {
        val s = 3.4f
        c.drawRect(cx - 5f, cy - 5f, cx - 5f + s, cy - 5f + s, iconFill)
        c.drawRect(cx + 5f - s, cy - 5f, cx + 5f, cy - 5f + s, iconFill)
        c.drawRect(cx - 5f, cy + 5f - s, cx - 5f + s, cy + 5f, iconFill)
        c.drawRect(cx + 2f, cy + 2f, cx + 4f, cy + 4f, iconFill)
    }

    // ------------------------------------------------------------ karekod

    private fun openQr() {
        // Kodlama acilirken bir kez yapilir; cizim yolunda yapilamaz.
        qrOk = QrEncoder.encode(QrEncoder.wifiPayload(ap.ssid, ap.passphrase), qr)
        if (qrOk) layoutQr()
        view = VIEW_QR
    }

    /**
     * Modul boyu TAM SAYI piksel secilir; kesirli olcekte modul kenarlari
     * piksel sinirina oturmuyor ve tarayici esik secmekte zorlaniyor.
     *
     * Sessiz bolge kutunun ICINDE tutulmuyor: karekod gosterilirken TUM EKRAN
     * beyaza boyaniyor, boylece kodun cevresinde ekran kenarina kadar acik
     * alan kaliyor. Once kutu icine 2-3 modul pay sikistiriliyordu; o pay
     * spesifikasyonun istedigi 4 modulun altindaydi ve hemen disinda siyah
     * zemin basliyordu. Beyazi yayinca hem pay ~9 module cikiyor hem de
     * modul 5 px'te kaliyor — ikisinden birini secmek gerekmiyor.
     */
    private fun layoutQr() {
        val n = qr.size
        val scale = QR_MAX_PX / n
        if (scale < 1) {
            qrOk = false
            return
        }
        qrQuiet = 0
        qrScale = scale
        val box = n * scale
        qrBoxW = box.toFloat()
        qrBoxL = ((240 - box) / 2).toFloat()
        qrBoxT = qrBoxL
    }

    private fun drawQr(c: Canvas) {
        if (!qrOk) {
            Geom.centerText(c, str(R.string.wifi_qr_failed), 110f, hint)
            drawClose(c)
            return
        }
        // Sessiz bolge ekran kenarina kadar uzansin
        c.drawRect(0f, 0f, Geom.W, Geom.H, qrWhite)

        val s = qrScale.toFloat()
        val x0 = qrBoxL + qrQuiet * s
        val y0 = qrBoxT + qrQuiet * s
        val n = qr.size
        for (y in 0 until n) {
            var x = 0
            while (x < n) {
                if (!qr.dark(x, y)) {
                    x++
                    continue
                }
                // Yatay komsu modulleri tek dikdortgende birlestir: 29x29
                // matriste modul basina cagri ~840 drawRect demek oluyordu.
                var e = x
                while (e + 1 < n && qr.dark(e + 1, y)) e++
                c.drawRect(x0 + x * s, y0 + y * s, x0 + (e + 1) * s, y0 + (y + 1) * s, qrBlack)
                x = e + 1
            }
        }
        drawClose(c)
    }

    /**
     * Gorunur cikis. Cihazda fizik geri tusu yok ve Pager tum yuzeyi
     * `systemGestureExclusionRects` ile talep ettigi icin sistem geri hareketi
     * de gelmiyor; carpi olmazsa bu katmandan cikilamaz.
     */
    private fun drawClose(c: Canvas) {
        val r = 9f
        val p = if (view == VIEW_QR && qrOk) closeOnWhite else closePaint
        c.drawLine(CLOSE_CX - r, CLOSE_CY - r, CLOSE_CX + r, CLOSE_CY + r, p)
        c.drawLine(CLOSE_CX + r, CLOSE_CY - r, CLOSE_CX - r, CLOSE_CY + r, p)
    }

    // ------------------------------------------------------------ yeni kimlik

    private fun openNew() {
        val cred = WifiCredGen.generate()
        newSsid = cred.ssid
        newPass = cred.passphrase
        showingFactory = false
        applyState = APPLY_IDLE
        view = VIEW_NEW
        // Fabrika degerlerini simdiden getir: kullanici geri donmek isterse
        // dugme hazir olsun, root okumasi icin beklemesin.
        loadFactory()
    }

    /**
     * Onerilen kimligi FABRIKA degerlerine cevirir.
     *
     * Ayri bir ekran degil, ayni onay akisi: yazma yolu tek noktada kalsin.
     * Degerler cihaz uzerindeki etiketle ayni; kullanici rastgele bir ada
     * gecip vazgecerse donecek yeri olmali.
     */
    private fun useFactory() {
        if (factorySsid.isEmpty() || factoryKey.isEmpty()) return
        newSsid = factorySsid
        newPass = factoryKey
        showingFactory = true
        applyState = APPLY_IDLE
        invalidate()
    }

    private fun loadFactory() {
        if (factorySsid.isNotEmpty() && factoryKey.isNotEmpty()) return
        RootShell.async("cat $FACTORY_MAC 2>/dev/null") { mac ->
            factorySsid = WifiFactory.ssidFromMac(mac)
            invalidate()
        }
        RootShell.async("cat $FACTORY_KEY 2>/dev/null") { key ->
            factoryKey = WifiFactory.keyFromFile(key)
            invalidate()
        }
    }

    private fun drawNew(c: Canvas) {
        Geom.centerText(
            c,
            str(if (showingFactory) R.string.wifi_reset_title else R.string.wifi_new_title),
            26f, title
        )
        Geom.hairline(c, 42f, 50f, hair)

        Geom.centerText(c, str(R.string.wifi_network), 54f, label)
        Geom.centerText(c, newSsid, 65f, ssidPaint)

        Geom.centerText(c, str(R.string.wifi_password), 90f, label)
        Geom.centerText(c, newPass, 101f, passPaint)

        when (applyState) {
            APPLY_IDLE -> Geom.centerText(c, str(R.string.wifi_apply_confirm), 130f, hint)
            APPLY_RUNNING -> Geom.centerText(c, str(R.string.wifi_applying), 130f, hint)
            APPLY_LIVE -> Geom.centerText(c, str(R.string.wifi_applied), 130f, okTextPaint)
            APPLY_OK -> Geom.centerText(c, str(R.string.wifi_reboot_needed), 130f, warnPaint)
            else -> Geom.centerText(c, str(R.string.wifi_apply_failed), 130f, errPaint)
        }

        val r = 8f
        c.drawLine(
            NEW_CANCEL_CX - r, NEW_BTN_CY - r, NEW_CANCEL_CX + r, NEW_BTN_CY + r, cancelPaint
        )
        c.drawLine(
            NEW_CANCEL_CX + r, NEW_BTN_CY - r, NEW_CANCEL_CX - r, NEW_BTN_CY + r, cancelPaint
        )

        // Yazma bittikten sonra onay tusu kalkar: ayni yapilandirmayi ikinci
        // kez yazmanin faydasi yok, yanlislikla basmanin zarari var.
        if (applyState == APPLY_IDLE) {
            c.drawLine(NEW_OK_CX - 7f, NEW_BTN_CY, NEW_OK_CX - 2f, NEW_BTN_CY + 5f, okPaint)
            c.drawLine(NEW_OK_CX - 2f, NEW_BTN_CY + 5f, NEW_OK_CX + 8f, NEW_BTN_CY - 6f, okPaint)

            // Fabrika degerlerine don. Okunamadiysa dugme hic cizilmez —
            // basilabilir gorunup hicbir sey yapmamasi daha kotu.
            if (!showingFactory && factorySsid.isNotEmpty() && factoryKey.isNotEmpty()) {
                iconRect.set(
                    NEW_RESET_CX - 8f, NEW_BTN_CY - 8f, NEW_RESET_CX + 8f, NEW_BTN_CY + 8f
                )
                c.drawArc(iconRect, 120f, 300f, false, iconStroke)
                Geom.arrow(c, NEW_RESET_CX - 7f, NEW_BTN_CY - 6f, 4f, true, iconFill)
            }
        }
    }

    /**
     * TEK YAZMA NOKTASI — ayarlari gercekten degistiren baska hicbir yer yok.
     *
     * Yol: root altinda app_process ile calisan [SOFTAP_TOOL], IWifiManager
     * .setSoftApConfiguration'i cagirir. Bu cihazda `cmd wifi
     * set-soft-ap-configuration` alt komutu yok; XML'i elle duzenlemek ise
     * cerceve bellekteki kopyayi geri yazdiginda sessizce kayboluyor.
     *
     * SSID ve parola base64 ile geciriliyor: degerler kabuk satirina giriyor
     * ve $ ` " \ gibi bir isaret kacisi unutuldugunda parola sessizce baska
     * bir seye donusurdu.
     *
     * Yazma KALICI ama calisan AP'ye uygulanmaz — SoftApManager SSID/parola
     * degisiminde "requires restart" deyip yok sayiyor. Bu yuzden basarida
     * kullaniciya yeniden baslatma gerektigi soyleniyor; yeniden baslatmayi
     * BURADA yapmiyoruz, o karar aksiyon sayfasinda kullanicinin.
     */
    private fun applyCredentials(ssid: String, pass: String) {
        if (!WifiCredGen.isValidSsid(ssid) || !WifiCredGen.isValidPassphrase(pass)) {
            applyState = APPLY_FAIL
            invalidate()
            return
        }
        applyState = APPLY_RUNNING
        invalidate()
        // Her uretim icin bir jeton: kullanici yazma surerken cikip yeni bir
        // kimlik uretirse, eski cagrinin geri donusu ekrani "kaydedildi"ye
        // cevirip GOSTERILEN parolanin yazildigi izlenimi verirdi. Kullanici
        // o parolayi not edip yeniden baslatinca aga giremezdi.
        val token = ++applyToken
        val apk = context.applicationInfo.sourceDir
        // timeout: RootShell TEK bir `su` kabugu paylasiyor ve DataHub her
        // yoklamada oradan okuyor. Arac cikmazsa okuma sonsuza kadar bloke
        // olur ve istemci sayisi, pil, netpolicy dahil TUM root okumalari
        // durur. `timeout` cihazda var (toybox).
        val cmd = "timeout 60 env CLASSPATH='" + apk + "' app_process / " + SOFTAP_TOOL +
            " " + b64(ssid) + " " + b64(pass) + " restart"
        RootShell.async(cmd) { out ->
            if (token != applyToken) return@async
            // Basari isareti "OK" gibi genel bir sozcuk degil: su kabugu ve
            // app_process hata yolu kendi metinlerini ayni akisa yaziyor
            // (RootShell stderr'i stdout'a katiyor) ve icinde "OK" gecen bir
            // yigin izi basariymis gibi okunurdu.
            applyState = when {
                out == null || !out.contains(TOOL_OK) -> APPLY_FAIL
                out.contains(TOOL_RESTARTED) -> APPLY_LIVE
                else -> APPLY_OK
            }
            // Yazma tuttuysa ana sayfa artik ESKI degerleri gostermemeli.
            if (applyState != APPLY_FAIL) readConfig()
            invalidate()
        }
    }

    /** Uretim sayaci; eskimis yazma geri donuslerini elemek icin. */
    private var applyToken = 0

    private var factorySsid = ""
    private var factoryKey = ""

    /** Gosterilen oneri fabrika degeri mi, uretilmis mi? */
    private var showingFactory = false

    private fun b64(s: String): String = android.util.Base64.encodeToString(
        s.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP
    )

    // ------------------------------------------------------------ dokunma

    override fun onRawTouch(event: MotionEvent) {
        when (view) {
            VIEW_QR -> {
                if (event.actionMasked == MotionEvent.ACTION_UP &&
                    near(event, CLOSE_CX, CLOSE_CY)
                ) {
                    view = VIEW_MAIN
                    invalidate()
                }
            }

            VIEW_NEW -> {
                if (event.actionMasked != MotionEvent.ACTION_UP) return
                if (near(event, NEW_CANCEL_CX, NEW_BTN_CY)) {
                    view = VIEW_MAIN
                    invalidate()
                } else if (applyState == APPLY_IDLE && near(event, NEW_RESET_CX, NEW_BTN_CY)) {
                    useFactory()
                } else if (applyState == APPLY_IDLE && near(event, NEW_OK_CX, NEW_BTN_CY)) {
                    applyCredentials(newSsid, newPass)
                }
            }

            else -> mainTouch(event)
        }
    }

    private fun near(event: MotionEvent, cx: Float, cy: Float): Boolean =
        Math.abs(event.x - cx) < HIT && Math.abs(event.y - cy) < HIT

    private fun mainTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                downScroll = scrollY
                dragging = false
                pressedZone = zoneAt(event.y)
                if (pressedZone >= 0) postDelayed(longPress, LONG_PRESS_MS)
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - downY
                if (!dragging && Math.abs(dy) > slop) {
                    dragging = true
                    pressedZone = -1
                    clearPendingLongPress()
                }
                if (dragging && maxScroll > 0f) {
                    scrollY = (downScroll - dy).coerceIn(0f, maxScroll)
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                clearPendingLongPress()
                if (event.actionMasked == MotionEvent.ACTION_UP && !dragging) {
                    when (barAt(event.x, event.y)) {
                        BAR_QR -> openQr()
                        BAR_NEW -> openNew()
                        // Degerler okunamadiysa sessizce yok say; dugme zaten
                        // soluk cizilmis durumda.
                        BAR_RESET -> if (factorySsid.isNotEmpty() && factoryKey.isNotEmpty()) {
                            openNew()
                            useFactory()
                        }
                    }
                    invalidate()
                }
                pressedZone = -1
                dragging = false
            }
        }
    }

    /** Uzun basmanin hangi satira ait oldugu; -1 = ilgisiz bolge. */
    private fun zoneAt(y: Float): Int = when {
        !loaded || !snapshot.rootAvailable -> -1
        y >= 30f && y < 62f -> ZONE_SSID
        y >= 62f && y < 96f -> ZONE_PASS
        else -> -1
    }

    /**
     * SettingsPage ile ayni desen: dokunus ve uzun basma ayri islerdir.
     * Burada tek dokunusun bir isi yok, ama karekodu tek dokunusa baglamak
     * listeyi kaydirmak isteyen parmagi surekli karekod ekranina dusururdu.
     */
    private val longPress = Runnable {
        if (!dragging) {
            val zone = pressedZone
            pressedZone = -1
            if (zone == ZONE_PASS) openQr() else if (zone == ZONE_SSID) openNew()
            invalidate()
        }
    }

    /** `View.cancelLongPress()` ile ad catismasin diye ayri isim. */
    private fun clearPendingLongPress() = removeCallbacks(longPress)
}
