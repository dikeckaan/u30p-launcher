package com.kaandikec.u30plauncher.test

import com.kaandikec.u30plauncher.core.TimeAverage
import com.kaandikec.u30plauncher.test.TestSupport.eq
import com.kaandikec.u30plauncher.test.TestSupport.ok

object TimeAverageTest {
    fun run() {
        // Ilk ornek dogrudan deger olur; baslangicta sifirdan surunmez
        val a = TimeAverage()
        ok("baslangicta hazir degil", !a.isReady)
        a.add(-500_000, 1000)
        ok("ilk ornekten sonra hazir", a.isReady)
        eq("ilk ornek", a.value(), -500_000)

        // Sabit girdide deger degismez
        repeat(60) { a.add(-500_000, 1000) }
        eq("sabit girdi", a.value(), -500_000)

        // Tek bir sicrama 10 dk sabitte neredeyse etkisiz olmali
        val b = TimeAverage(300_000L)
        b.add(-500_000, 1000)
        b.add(-3_000_000, 1000)   // ani buyuk cekim
        val afterSpike = b.value()
        ok("sicrama az etkiler", Math.abs(afterSpike - (-500_000)) < 20_000)

        // Kalici degisim: ustel ortalama hedefe 1 - e^(-t/tau) oraninda yaklasir.
        // 5 dk sabitte 5 dk sonra ~%63 yol alinmis olmali.
        val c = TimeAverage(300_000L)
        c.add(-500_000, 1000)
        repeat(300) { c.add(-1_000_000, 1000) }   // 5 dk boyunca iki kati cekim
        ok("5 dk'da yolun yarisindan fazlasi", c.value() < -790_000)
        ok("henuz tam ulasmadi", c.value() > -1_000_000)

        // 10 dk sonra ~%86
        repeat(300) { c.add(-1_000_000, 1000) }
        ok("10 dk'da neredeyse ulasti", c.value() < -920_000)

        // Cok uzun bosluk: eski ortalama gecersiz, yeni ornek hakim olur
        val d = TimeAverage(300_000L)
        d.add(-500_000, 1000)
        d.add(-1_200_000, 3_600_000)   // 1 saat sonra
        eq("uzun bosluk", d.value(), -1_200_000)

        // Gecersiz sure yok sayilir
        val e = TimeAverage()
        e.add(-400_000, 1000)
        e.add(-900_000, 0)
        e.add(-900_000, -5)
        eq("gecersiz sure", e.value(), -400_000)

        // reset sonrasi temiz
        e.reset()
        ok("reset sonrasi hazir degil", !e.isReady)
        eq("reset sonrasi deger", e.value(), 0)
    }
}
