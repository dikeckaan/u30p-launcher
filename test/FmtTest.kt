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

        // Her deger kendi birimini alir: indirme ve yukleme buyuklukleri
        // birbirinden bagimsiz olabilir ve ekranda birim yaninda yazilir.
        eq("hizli indirme degeri", Fmt.speedValueOf(1_550_000), "12.4")
        eq("hizli indirme birimi", Fmt.speedUnit(1_550_000), "Mbps")
        eq("yavas yukleme degeri", Fmt.speedValueOf(540), "4.32")
        eq("yavas yukleme birimi", Fmt.speedUnit(540), "Kbps")
        eq("cok yavas birim", Fmt.speedUnit(10), "bps")
        eq("cok hizli birim", Fmt.speedUnit(500_000_000), "Gbps")
    }
}
