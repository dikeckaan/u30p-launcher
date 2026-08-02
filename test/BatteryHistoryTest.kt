package com.kaandikec.u30plauncher.test

import com.kaandikec.u30plauncher.core.BatteryEstimate
import com.kaandikec.u30plauncher.core.BatteryHistory
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.test.TestSupport.eq
import com.kaandikec.u30plauncher.test.TestSupport.ok

object BatteryHistoryTest {
    private const val MIN = 60_000L

    fun run() {
        // 4626 mAh pil, saatte 500 mAh dusus -> ~9.25 saat kalir
        val h = BatteryHistory()
        var t = 0L
        var c = 4_626_000
        repeat(20) {              // 20 dakika gozlem
            h.add(c, t)
            t += MIN
            c -= 500_000 / 60     // dakikada 8333 uAh
        }
        val drain = h.drainUahPerHour()
        ok("hiz olculdu", drain != Snapshot.UNKNOWN)
        ok("hiz makul", Math.abs(drain - 500_000) < 20_000)
        val left = BatteryEstimate.minutesLeftFromDrain(c, drain)
        ok("kalan sure makul", Math.abs(left - 9 * 60) < 60)

        // Yetersiz sure: 10 dakikanin altinda guven verilmez
        val short = BatteryHistory()
        short.add(4_626_000, 0)
        short.add(4_600_000, 5 * MIN)
        eq("kisa pencere", short.drainUahPerHour(), Snapshot.UNKNOWN)

        // Dusus cok kucukse (olcum gurultusu) guvenilmez
        val flat = BatteryHistory()
        flat.add(4_626_000, 0)
        flat.add(4_625_000, 20 * MIN)
        eq("ihmal edilebilir dusus", flat.drainUahPerHour(), Snapshot.UNKNOWN)

        // Sarja takilinca gecmis temizlenir
        val ch = BatteryHistory()
        ch.add(2_000_000, 0)
        ch.add(1_900_000, 15 * MIN)
        ok("once hiz vardi", ch.drainUahPerHour() != Snapshot.UNKNOWN)
        ch.add(2_100_000, 30 * MIN)      // sarj artti
        eq("sarjda gecmis silindi", ch.sampleCount, 1)
        eq("sarjda hiz yok", ch.drainUahPerHour(), Snapshot.UNKNOWN)

        // Ornekler seyrek alinir; sik cagri yok sayilir
        val sp = BatteryHistory()
        sp.add(4_000_000, 0)
        sp.add(3_999_000, 1000)
        sp.add(3_998_000, 2000)
        eq("sik cagrilar yok sayildi", sp.sampleCount, 1)

        // Ekran kapaliyken olusan uzun bosluk gecerli veridir
        val gap = BatteryHistory()
        gap.add(4_626_000, 0)
        gap.add(4_126_000, 60 * MIN)     // 1 saatte 500 mAh
        val gapDrain = gap.drainUahPerHour()
        ok("bosluk olcume dahil", Math.abs(gapDrain - 500_000) < 5_000)

        // Kalici saklama: yuklenince ayni hiz cikmali
        val saved = gap.serialize()
        val loaded = BatteryHistory()
        loaded.load(saved, 60 * MIN)
        eq("yuklenen ornek sayisi", loaded.sampleCount, 2)
        eq("yuklenen hiz", loaded.drainUahPerHour(), gapDrain)

        // Yeniden baslatma: kayitli zaman simdikinden buyukse atilir
        val stale = BatteryHistory()
        stale.load(saved, 10L)
        eq("reboot sonrasi atildi", stale.sampleCount, 0)

        // Bos veri cokmemeli
        val empty = BatteryHistory()
        empty.load("", 0)
        eq("bos veri", empty.sampleCount, 0)
        empty.load("bozuk;veri", 1000)
        eq("bozuk veri", empty.sampleCount, 0)

        // Gozlem yoksa tahmin de yok
        eq("hiz bilinmiyor", BatteryEstimate.minutesLeftFromDrain(4_626_000, Snapshot.UNKNOWN), Snapshot.UNKNOWN)
        eq("sifir hiz", BatteryEstimate.minutesLeftFromDrain(4_626_000, 0), Snapshot.UNKNOWN)
    }
}
