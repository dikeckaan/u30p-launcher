package com.kaandikec.u30plauncher.test

import com.kaandikec.u30plauncher.core.BatteryEstimate
import com.kaandikec.u30plauncher.core.Fmt
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.test.TestSupport.eq

object BatteryEstimateTest {
    fun run() {
        // Cihazdan olculen kapasite: 4626000 uAh (4626 mAh)
        // 500 mA cekimde ~9.25 saat
        eq("500 mA cekim", BatteryEstimate.minutesLeft(4_626_000, -500_000), 555)
        // 1 A cekimde yarisi
        eq("1 A cekim", BatteryEstimate.minutesLeft(4_626_000, -1_000_000), 277)

        // Sarj ederken (pozitif akim) kalan sure kavrami yok
        eq("sarjda", BatteryEstimate.minutesLeft(4_626_000, 573_000), Snapshot.UNKNOWN)
        eq("akim sifir", BatteryEstimate.minutesLeft(4_626_000, 0), Snapshot.UNKNOWN)

        // Sarj bilgisi yoksa
        eq("sarj bilinmiyor", BatteryEstimate.minutesLeft(0, -500_000), Snapshot.UNKNOWN)
        eq("negatif sarj", BatteryEstimate.minutesLeft(-5, -500_000), Snapshot.UNKNOWN)

        // Cok kucuk cekimde tahmin sacmalar; elenmeli
        eq("ihmal edilebilir cekim", BatteryEstimate.minutesLeft(4_626_000, -1_000), Snapshot.UNKNOWN)

        // Ust sinir 48 saat
        eq("ust sinir", BatteryEstimate.minutesLeft(4_626_000, -20_000), 48 * 60)

        // Sure formatlama
        eq("dakika", Fmt.duration(45), "45 dk")
        eq("bir saat", Fmt.duration(60), "1.0 sa")
        eq("ondalikli saat", Fmt.duration(555), "9.2 sa")
        eq("iki basamakli saat", Fmt.duration(12 * 60 + 30), "12 sa")
        eq("sifir dakika", Fmt.duration(0), "0 dk")
    }
}
