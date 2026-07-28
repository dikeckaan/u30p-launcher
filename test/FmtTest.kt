package com.kaandikec.u30plauncher.test

import com.kaandikec.u30plauncher.core.Fmt
import com.kaandikec.u30plauncher.test.TestSupport.eq

object FmtTest {
    fun run() {
        // bytes: 1024 tabani; ondalik basamak buyudukce daralir, boylece
        // metin genisligi dar bir aralikta kalir
        eq("bytes 0", Fmt.bytes(0), "0 B")
        eq("bytes 999", Fmt.bytes(999), "999 B")
        eq("bytes 1 KB", Fmt.bytes(1024), "1.00 KB")
        eq("bytes 821 KB", Fmt.bytes(821L * 1024), "821 KB")
        eq("bytes 21.8 GB", Fmt.bytes(23413089239L), "21.8 GB")
        eq("bytes 1 TB", Fmt.bytes(1024L * 1024 * 1024 * 1024), "1.00 TB")

        // speed: bit/s, 1000 tabani
        eq("speed 0", Fmt.speed(0), "0 bps")
        eq("speed 12.4 Mbps", Fmt.speedValueOf(1_550_000), "12.4")
        eq("unit 12.4 Mbps", Fmt.speedUnit(1_550_000), "Mbps")
        eq("speed 1.10 Mbps", Fmt.speedValueOf(137_500), "1.10")
        eq("unit Kbps", Fmt.speedUnit(1_000), "Kbps")

        // negatif girdi savunmasi
        eq("bytes negatif", Fmt.bytes(-5), "0 B")
        eq("speed negatif", Fmt.speed(-5), "0 bps")

        // Ortak birim: indirme ve yukleme tek etiket paylasir, ikisi de ayni
        // olcekte yazilmali. 12.4 Mbps ve 4.32 Kbps ayni anda gosterilirken
        // kucuk olan Mbps olceginde "0.00" olur — dogru davranis budur.
        val rx = 1_550_000L   // 12.4 Mbps
        val tx = 540L         // 4.32 Kbps
        val shared = Fmt.speedUnitIndex(if (rx > tx) rx else tx)
        eq("ortak birim adi", Fmt.unitName(shared), "Mbps")
        eq("ortak olcek rx", Fmt.speedValueAtOf(rx, shared), "12.4")
        eq("ortak olcek tx", Fmt.speedValueAtOf(tx, shared), "0.00")

        // Ikisi de kucukse ortak birim Kbps olur
        val shared2 = Fmt.speedUnitIndex(540L)
        eq("kucuk ortak birim", Fmt.unitName(shared2), "Kbps")
        eq("kucuk ortak olcek", Fmt.speedValueAtOf(540L, shared2), "4.32")

        // sinir disi indeks kirpilir, cokmez
        eq("indeks kirpma", Fmt.unitName(99), "Tbps")
        eq("negatif indeks", Fmt.unitName(-3), "bps")
    }
}
