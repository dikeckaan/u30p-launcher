package com.kaandikec.u30plauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.kaandikec.u30plauncher.R
import com.kaandikec.u30plauncher.core.Fmt
import com.kaandikec.u30plauncher.core.History
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.ui.theme.ThemeUtil

/**
 * Sinyal ve hiz gecmisi grafigi.
 *
 * Iki serit: ustte indirme/yukleme hizi, altta RSRP. Anlik deger kapsama ve
 * hiz sorununu teshise yetmiyordu; son birkac dakikanin egrisi "dustu mu,
 * dalgali mi, toparliyor mu" sorusunu tek bakista yanitliyor.
 *
 * Hiz seridi LOG olcekli. Bir MiFi'de trafik bosta neredeyse sifir, tepede
 * megabit; dogrusal olcekte butun dusuk degerler tabana yapisip ayirt
 * edilemez oluyordu (50 kbps ile 500 kbps ayni pikselde). Log, orani
 * gorunur kilar ve algiya da uyar. RSRP seridi dogrusal, kendi araligina
 * otomatik olceklenir.
 */
class HistoryPage(ctx: Context) : PageView(ctx) {

    companion object {
        /** ~120 ornek: gorunur oldugu surece saniyede bir eklenince ~2 dakika. */
        private const val SAMPLES = 120

        private const val PLOT_L = 46f
        private const val PLOT_R = 194f

        private const val SPD_TOP = 44f
        private const val SPD_BOT = 108f

        private const val RS_TOP = 138f
        private const val RS_BOT = 194f

        /** Log olcekte sabit taban (bit/sn). Alt siniri da otomatik yapmak
         *  bosta gurultuyle egriyi zipilatiyordu; taban sabitken oturakli. */
        private const val FLOOR_BITS = 1000.0

        private const val KIND_SPEED = 0
        private const val KIND_RSRP = 1
    }

    private val rxHist = History(SAMPLES)
    private val txHist = History(SAMPLES)
    private val rsrpHist = History(SAMPLES)

    private val title = ThemeUtil.text(10f, Palette.DIM2)
    private val lab = ThemeUtil.text(9f, Palette.DIM2)
    private val valTxt = ThemeUtil.text(11f, Palette.FG)
    private val hair = ThemeUtil.hairline(Palette.HAIRLINE)

    private val downLine = ThemeUtil.stroke(Palette.DOWN, 1.6f).apply {
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val upLine = ThemeUtil.stroke(Palette.UP, 1.6f).apply {
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val rsrpLine = ThemeUtil.stroke(Palette.FG, 1.6f).apply {
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val downFill = ThemeUtil.fill(Palette.DOWN)
    private val upFill = ThemeUtil.fill(Palette.UP)
    private val rsrpDot = ThemeUtil.fill(Palette.FG)

    private val sb = StringBuilder(16)
    // Tek Path yeniden kullanilir; her seri oncesi rewind ile temizlenir,
    // boylece cizim yolunda allocation olmaz.
    private val path = Path()

    private val step = (PLOT_R - PLOT_L) / (SAMPLES - 1)

    // Cizim basinda kurulan olcek katsayilari; y hesabi alan uzerinden okur,
    // lambda tasimaz.
    private var spdLogFloor = 0.0
    private var spdLogSpan = 1.0
    private var rsrpBottom = 0.0
    private var rsrpSpan = 1.0
    private var rsrpHasData = false

    /**
     * Ayni snapshot iki kez eklenmesin diye en son ornekledigimiz nesneyi
     * kimlik (referans) olarak tutariz.
     *
     * Ornekleme cizimde yapilir cunku PageView.update snapshot degismediyse
     * invalidate etmiyor — yani draw yalnizca DEGISEN snapshot'larla cagrilir.
     * Ama draw ayni snapshot icin baska nedenlerle de yeniden cagrilabilir
     * (kilit parlamasi, gorunurluk/yerlesim degisimi). O tekrarlarda ayni
     * nesneyi ikinci kez eklememek icin referans karsilastirmasi yeterli;
     * DataHub her yoklamada yeni bir Snapshot uretir, deger ayni kalirsa zaten
     * update hic cizdirmez.
     */
    private var lastSampled: Snapshot? = null

    override fun draw(c: Canvas, s: Snapshot) {
        sample(s)

        Geom.centerText(c, str(R.string.history_title), 14f, title)

        // --- hiz seridi ---
        // Efsane: yesil asagi ok = indirme, mavi yukari ok = yukleme.
        Geom.arrow(c, 54f, 30f, 8f, true, downFill)
        Geom.arrow(c, 68f, 30f, 8f, false, upFill)

        setupSpeedScale()
        // Tepe hiz etiketi: eksenin ust siniri; olcegi okumayi saglar.
        var peak = maxOf(rxHist.max(), txHist.max())
        if (peak == History.UNKNOWN) peak = 0L
        sb.setLength(0)
        Fmt.appendSpeedValue(sb, peak)
        sb.append(' ')
        sb.append(Fmt.speedUnit(peak))
        val pw = Geom.textWidth(sb, lab)
        Geom.textAt(c, sb, PLOT_R - pw, 24f, lab)

        c.drawLine(PLOT_L, SPD_BOT, PLOT_R, SPD_BOT, hair)
        drawSeries(c, rxHist, KIND_SPEED, SPD_TOP, SPD_BOT, downLine, downFill)
        drawSeries(c, txHist, KIND_SPEED, SPD_TOP, SPD_BOT, upLine, upFill)

        // --- RSRP seridi ---
        Geom.hairline(c, 120f, 70f, hair)
        Geom.textAt(c, "RSRP", PLOT_L, 122f, lab)
        sb.setLength(0)
        val now = rsrpHist.newest()
        if (now == History.UNKNOWN) sb.append('—') else {
            sb.append(now)
            sb.append(' ')
            sb.append(str(R.string.dbm_suffix))
        }
        val vw = Geom.textWidth(sb, valTxt)
        Geom.textAt(c, sb, PLOT_R - vw, 120f, valTxt)

        setupRsrpScale()
        c.drawLine(PLOT_L, RS_BOT, PLOT_R, RS_BOT, hair)
        drawSeries(c, rsrpHist, KIND_RSRP, RS_TOP, RS_BOT, rsrpLine, rsrpDot)
    }

    private fun sample(s: Snapshot) {
        if (s === lastSampled || s === Snapshot.EMPTY) return
        lastSampled = s
        rxHist.add(s.rxSpeed)
        txHist.add(s.txSpeed)
        rsrpHist.add(if (s.rsrp == Snapshot.UNKNOWN) History.UNKNOWN else s.rsrp.toLong())
    }

    private fun setupSpeedScale() {
        var maxBytes = maxOf(rxHist.max(), txHist.max())
        if (maxBytes == History.UNKNOWN) maxBytes = 0L
        var topBits = maxBytes * 8.0
        // En az bir onlu dilim goster; yoksa bos ekranda egri tavana yapisir.
        val minTop = FLOOR_BITS * 10.0
        if (topBits < minTop) topBits = minTop
        spdLogFloor = Math.log10(FLOOR_BITS)
        spdLogSpan = Math.log10(topBits) - spdLogFloor
    }

    private fun setupRsrpScale() {
        val lo = rsrpHist.min()
        rsrpHasData = lo != History.UNKNOWN
        if (!rsrpHasData) return
        val hi = rsrpHist.max()
        val loD = lo.toDouble()
        val hiD = hi.toDouble()
        // Tek deger varken (lo==hi) egri cizgiye yapismasin diye pay ac.
        val pad = if (hiD == loD) 3.0 else maxOf(2.0, (hiD - loD) / 8.0)
        rsrpBottom = loD - pad
        rsrpSpan = (hiD + pad) - rsrpBottom
    }

    private fun yFor(kind: Int, v: Long, top: Float, bot: Float): Float {
        val frac: Double
        if (kind == KIND_SPEED) {
            val bits = if (v > 0L) v * 8.0 else 0.0
            val l = if (bits <= FLOOR_BITS) spdLogFloor else Math.log10(bits)
            frac = (l - spdLogFloor) / spdLogSpan
        } else {
            frac = (v.toDouble() - rsrpBottom) / rsrpSpan
        }
        val y = bot - frac.toFloat() * (bot - top)
        return if (y < top) top else if (y > bot) bot else y
    }

    /**
     * Bir seriyi soldan saga cizer; en yeni ornek dolana kadar sagda degil,
     * biriktikce saga dogru buyur. UNKNOWN ornekte kalem kaldirilir (bosluk).
     */
    private fun drawSeries(
        c: Canvas, hist: History, kind: Int,
        top: Float, bot: Float, line: Paint, dot: Paint
    ) {
        if (kind == KIND_RSRP && !rsrpHasData) return
        path.rewind()
        var pen = false
        var nx = 0f
        var ny = 0f
        var has = false
        val n = hist.size
        for (i in 0 until n) {
            val v = hist.get(i)
            if (v == History.UNKNOWN) {
                pen = false
                continue
            }
            val x = PLOT_L + i * step
            val y = yFor(kind, v, top, bot)
            if (!pen) {
                path.moveTo(x, y)
                pen = true
            } else {
                path.lineTo(x, y)
            }
            nx = x; ny = y; has = true
        }
        c.drawPath(path, line)
        // En guncel degeri belirgin kilan nokta; tek ornek varken egri
        // gorunmezdi, nokta onu da gosterir.
        if (has) c.drawCircle(nx, ny, 1.8f, dot)
    }
}
