package com.kaandikec.u30plauncher.test

import com.kaandikec.u30plauncher.core.CyclePeriod
import com.kaandikec.u30plauncher.test.TestSupport.eq
import java.util.Calendar
import java.util.TimeZone

object CyclePeriodTest {
    private fun cal(y: Int, m: Int, d: Int, h: Int = 12): Calendar {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        c.clear()
        c.set(y, m, d, h, 30, 45)
        return c
    }

    private fun ymd(c: Calendar): String =
        "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH) + 1}-${c.get(Calendar.DAY_OF_MONTH)}"

    fun run() {
        // gun >= donem gunu: donem BU ay basladi
        val a = cal(2026, Calendar.AUGUST, 20)
        CyclePeriod.applyStart(a, 12)
        eq("ayni ay", ymd(a), "2026-8-12")
        eq("gun basi saat", a.get(Calendar.HOUR_OF_DAY), 0)
        eq("gun basi dakika", a.get(Calendar.MINUTE), 0)

        // gun < donem gunu: donem GECEN ay basladi
        val b = cal(2026, Calendar.AUGUST, 4)
        CyclePeriod.applyStart(b, 12)
        eq("onceki ay", ymd(b), "2026-7-12")

        // yil siniri
        val c = cal(2026, Calendar.JANUARY, 3)
        CyclePeriod.applyStart(c, 15)
        eq("yil siniri", ymd(c), "2025-12-15")

        // 31'inde bir ay geri gitmek gunu kaydirmamali
        val d = cal(2026, Calendar.MARCH, 31)
        CyclePeriod.applyStart(d, 28)
        eq("31'inden geri", ymd(d), "2026-3-28")
        val e = cal(2026, Calendar.MARCH, 5)
        CyclePeriod.applyStart(e, 28)
        eq("marttan subata", ymd(e), "2026-2-28")

        // varsayilan: takvim ayinin basi
        val f = cal(2026, Calendar.AUGUST, 4)
        CyclePeriod.applyStart(f, 1)
        eq("varsayilan ay basi", ymd(f), "2026-8-1")

        // anahtar donem baslangicindan turer; ayni donem boyunca sabit kalir
        val g = cal(2026, Calendar.AUGUST, 4)
        CyclePeriod.applyStart(g, 12)
        val h = cal(2026, Calendar.JULY, 20)
        CyclePeriod.applyStart(h, 12)
        eq("ayni donem ayni anahtar", CyclePeriod.key(g), CyclePeriod.key(h))
        val i = cal(2026, Calendar.AUGUST, 20)
        CyclePeriod.applyStart(i, 12)
        eq("sonraki donem farkli", CyclePeriod.key(i) != CyclePeriod.key(g), true)
    }
}
