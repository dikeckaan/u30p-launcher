package com.kaandikec.u30plauncher.core

import java.util.Calendar

/**
 * SMS metninin saf (Android'siz) parcasi: satirlara bolme ve kisa tarih.
 *
 * Genislik olcumu cagirandan `Measurer` ile gelir; boylece Paint'e bagimli
 * olmayan bu mantik testten calistirilabiliyor. Bolme sonuclari cizim
 * disinda BIR KEZ hesaplanip saklanmali — burada String uretiliyor.
 */
object SmsText {

    /** Metnin `start` ile `end` arasindaki parcasinin piksel genisligi. */
    interface Measurer {
        fun width(s: CharSequence, start: Int, end: Int): Float
    }

    /**
     * `text`i en fazla `maxLines` satira boler; sigmayan kisim atilir.
     *
     * Sona "..." konmuyor: 9-11 px'te uc nokta bir kelime kadar yer kaplayip
     * son satiri daha da kirpiyor.
     *
     * '\n' sert satir sonudur — operator mesajlari kampanya kosullarini
     * boyle ayiriyor ve detayda o duzen korunmali.
     */
    fun wrap(
        text: CharSequence,
        m: Measurer,
        maxWidth: Float,
        maxLines: Int,
        out: ArrayList<String>
    ) {
        out.clear()
        if (maxWidth <= 0f || maxLines <= 0) return
        val n = text.length
        var i = 0
        while (i < n && out.size < maxLines) {
            // Satir basindaki bosluklar metni saga kaydirip hizayi bozuyordu
            while (i < n && isBlank(text[i])) i++
            if (i >= n) break

            var stop = i
            while (stop < n && text[stop] != '\n') stop++

            var end = fitEnd(text, m, i, stop, maxWidth)
            if (end < stop) {
                // Kelimeyi ortadan kesme; son bosluga geri sar. Tek kelime
                // satirdan uzunsa kesmekten baska care yok.
                var k = end
                while (k > i && !isBlank(text[k])) k--
                if (k > i) end = k
            }
            out.add(text.subSequence(i, trimmedEnd(text, i, end)).toString())
            i = if (end >= stop) stop + 1 else end
        }
    }

    /**
     * Onizleme icin satir sonlarini ve tekrarli bosluklari tek bosluga cevirir.
     *
     * Listede iki satirlik yer var; govdedeki sert satir sonuna uyulunca o iki
     * satirin biri "Degerli musterimiz," ile dolup mesaj hakkinda hicbir sey
     * anlatmiyordu.
     */
    fun appendFlattened(sb: StringBuilder, text: CharSequence, max: Int) {
        val n = text.length
        var written = 0
        var space = false
        for (i in 0 until n) {
            if (written >= max) return
            val ch = text[i]
            if (ch == '\n' || isBlank(ch)) {
                space = written > 0
                continue
            }
            if (space) {
                sb.append(' ')
                written++
                space = false
                if (written >= max) return
            }
            sb.append(ch)
            written++
        }
    }

    /**
     * Liste damgasi: bugunse "14:05", degilse "2.8", baska yilsa "2.8.25".
     *
     * 240 px'te sutun dar; tam tarih gonderen adini kirptiriyordu. Gun.ay
     * sirasi cihazin kullanildigi yerin (TR) alisilmis sirasi.
     *
     * `cal` cagirandan gelir ve yeniden kullanilir; her damga icin Calendar
     * ayirmak listeyi kurarken 20 nesne demekti.
     */
    fun appendStamp(sb: StringBuilder, ms: Long, nowMs: Long, cal: Calendar) {
        cal.timeInMillis = nowMs
        val nowYear = cal.get(Calendar.YEAR)
        val nowDay = cal.get(Calendar.DAY_OF_YEAR)

        cal.timeInMillis = ms
        val year = cal.get(Calendar.YEAR)
        if (year == nowYear && cal.get(Calendar.DAY_OF_YEAR) == nowDay) {
            appendClock(sb, cal)
            return
        }
        sb.append(cal.get(Calendar.DAY_OF_MONTH))
        sb.append('.')
        sb.append(cal.get(Calendar.MONTH) + 1)
        if (year != nowYear) {
            sb.append('.')
            val yy = year % 100
            if (yy < 10) sb.append('0')
            sb.append(yy)
        }
    }

    /** Detay basligi: "2.8.2026 14:05" — burada yer var, tarih tam yazilir. */
    fun appendFullStamp(sb: StringBuilder, ms: Long, cal: Calendar) {
        cal.timeInMillis = ms
        sb.append(cal.get(Calendar.DAY_OF_MONTH))
        sb.append('.')
        sb.append(cal.get(Calendar.MONTH) + 1)
        sb.append('.')
        sb.append(cal.get(Calendar.YEAR))
        sb.append(' ')
        appendClock(sb, cal)
    }

    private fun appendClock(sb: StringBuilder, cal: Calendar) {
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        if (h < 10) sb.append('0')
        sb.append(h)
        sb.append(':')
        if (m < 10) sb.append('0')
        sb.append(m)
    }

    /**
     * `maxWidth`e sigan en uzak bitis noktasi; en az bir karakter ilerler.
     *
     * Ikili arama, cunku olcum cagrisi (Paint) pahali: 160 karakterlik bir
     * mesajda dogrusal tarama satir basina onlarca cagri yapiyordu.
     */
    private fun fitEnd(
        text: CharSequence, m: Measurer, start: Int, stop: Int, maxWidth: Float
    ): Int {
        if (stop <= start) return stop
        if (m.width(text, start, stop) <= maxWidth) return stop
        var lo = start + 1
        var hi = stop
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (m.width(text, start, mid) <= maxWidth) lo = mid else hi = mid - 1
        }
        return lo
    }

    private fun trimmedEnd(text: CharSequence, start: Int, end: Int): Int {
        var e = end
        while (e > start && isBlank(text[e - 1])) e--
        return e
    }

    private fun isBlank(c: Char): Boolean = c == ' ' || c == '\t' || c == '\r'
}
