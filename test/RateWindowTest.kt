package com.kaandikec.u30plauncher.test

import com.kaandikec.u30plauncher.core.RateWindow
import com.kaandikec.u30plauncher.test.TestSupport.eq
import com.kaandikec.u30plauncher.test.TestSupport.ok

object RateWindowTest {
    fun run() {
        // Sabit hiz: pencere ne olursa olsun ayni sonucu vermeli
        val steady = RateWindow()
        repeat(6) { steady.add(1000, 1000) }
        eq("sabit hiz", steady.bytesPerSec(), 1000L)

        // Cihazdan gozlenen topakli desen: cogu ornek 0, arada sicrama.
        // Dogrudan fark 0 gosterirdi; pencere ortalamasi gostermemeli.
        val bursty = RateWindow()
        for (d in longArrayOf(0, 0, 13684, 0, 0)) bursty.add(d, 1000)
        ok("topakli veride sifir gostermez", bursty.bytesPerSec() > 0)
        // son 3 ornek (0,0,13684 degil; en yeniden geriye: 0,0,13684) -> 13684/3
        eq("topakli ortalama", bursty.bytesPerSec(), 13684L / 3)

        // Gercekten bosta: hepsi sifirsa sonuc da sifir olmali
        val idle = RateWindow()
        repeat(5) { idle.add(0, 1000) }
        eq("bosta sifir", idle.bytesPerSec(), 0L)

        // Ornek yokken cokmemeli
        eq("bos pencere", RateWindow().bytesPerSec(), 0L)

        // Sure bazli pencere: kisa aralikta daha cok ornek toplanir
        val fast = RateWindow(minWindowMs = 2500L)
        repeat(8) { fast.add(500, 500) }   // 500 B / 0.5 sn = 1000 B/s
        eq("kisa aralik", fast.bytesPerSec(), 1000L)

        // Uzun aralikta tek ornek pencereyi doldurur
        val slow = RateWindow(minWindowMs = 2500L)
        slow.add(10_000, 5000)
        eq("uzun aralik", slow.bytesPerSec(), 2000L)

        // Kapasite asilinca en eskiler dusmeli
        val small = RateWindow(capacity = 3, minWindowMs = 10_000L)
        small.add(999_999, 1000)
        repeat(3) { small.add(100, 1000) }
        eq("eski ornek dustu", small.bytesPerSec(), 100L)

        // reset sonrasi temiz baslangic
        val r = RateWindow()
        r.add(5000, 1000)
        r.reset()
        eq("reset sonrasi", r.bytesPerSec(), 0L)

        // Gecersiz sure yok sayilmali (saat geri gitmesi vb.)
        val bad = RateWindow()
        bad.add(1000, 0)
        bad.add(1000, -5)
        eq("gecersiz sure yok sayildi", bad.bytesPerSec(), 0L)

        // Negatif byte 0 sayilmali, ortalamayi bozmamali
        val neg = RateWindow()
        neg.add(-100, 1000)
        neg.add(2000, 1000)
        eq("negatif byte", neg.bytesPerSec(), 1000L)
    }
}
