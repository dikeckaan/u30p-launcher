package com.kaandikec.u30plauncher.test

import com.kaandikec.u30plauncher.core.SmsText
import com.kaandikec.u30plauncher.test.TestSupport.eq
import java.util.Calendar

object SmsTextTest {

    /** Her karakter 5 birim; olcum Paint'e bagli olmadan sinanabiliyor. */
    private object FixedWidth : SmsText.Measurer {
        override fun width(s: CharSequence, start: Int, end: Int): Float =
            (end - start) * 5f
    }

    private val out = ArrayList<String>(8)
    private val cal: Calendar = Calendar.getInstance()

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long {
        cal.set(y, mo - 1, d, h, mi, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun stamp(ms: Long, nowMs: Long): String =
        StringBuilder().also { SmsText.appendStamp(it, ms, nowMs, cal) }.toString()

    fun run() {
        // 40 birim = 8 karakter; kelime ortasindan degil bosluktan bolunmeli
        SmsText.wrap("abc def ghi", FixedWidth, 40f, 4, out)
        eq("kelime bolme satir sayisi", out.size, 2)
        eq("kelime bolme 1", out[0], "abc def")
        eq("kelime bolme 2", out[1], "ghi")

        // maxLines asilinca kalan atilir, uc nokta eklenmez
        SmsText.wrap("abc def ghi", FixedWidth, 40f, 1, out)
        eq("kirpma satir sayisi", out.size, 1)
        eq("kirpma metni", out[0], "abc def")

        // Satirdan uzun tek kelime kesilir; aksi halde dongu ilerlemez
        SmsText.wrap("abcdefghij", FixedWidth, 20f, 4, out)
        eq("uzun kelime 1", out[0], "abcd")
        eq("uzun kelime 2", out[1], "efgh")
        eq("uzun kelime 3", out[2], "ij")

        // Sert satir sonu korunur, satir sonundaki bosluk kirpilir
        SmsText.wrap("ab \ncd", FixedWidth, 100f, 4, out)
        eq("sert satir sayisi", out.size, 2)
        eq("sert satir 1", out[0], "ab")
        eq("sert satir 2", out[1], "cd")

        SmsText.wrap("", FixedWidth, 40f, 4, out)
        eq("bos metin", out.size, 0)

        val sb = StringBuilder()
        SmsText.appendFlattened(sb, "  ab\n\ncd  ef  ", 40)
        eq("duzlestirme", sb.toString(), "ab cd ef")

        sb.setLength(0)
        SmsText.appendFlattened(sb, "abcdef", 3)
        eq("duzlestirme siniri", sb.toString(), "abc")

        val now = at(2026, 8, 2, 20, 0)
        eq("bugun damgasi", stamp(at(2026, 8, 2, 14, 5), now), "14:05")
        eq("gece yarisi damgasi", stamp(at(2026, 8, 2, 0, 7), now), "00:07")
        eq("bu yil damgasi", stamp(at(2026, 7, 29, 9, 0), now), "29.7")
        eq("gecen yil damgasi", stamp(at(2025, 8, 2, 9, 0), now), "2.8.25")

        sb.setLength(0)
        SmsText.appendFullStamp(sb, at(2026, 8, 2, 14, 5), cal)
        eq("tam damga", sb.toString(), "2.8.2026 14:05")
    }
}
