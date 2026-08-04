package com.kaandikec.u30plauncher.test

import com.kaandikec.u30plauncher.core.WifiFactory
import com.kaandikec.u30plauncher.test.TestSupport.eq

object WifiFactoryTest {
    fun run() {
        // Cihazda dogrulanan kural: MAC'in son 6 hanesi, buyuk harf
        eq("gercek cihaz", WifiFactory.ssidFromMac("5c:1a:2b:9b:60:c4"), "ZTE_9B60C4")
        eq("buyuk harfe cevirir", WifiFactory.ssidFromMac("00:11:22:aa:bb:cc"), "ZTE_AABBCC")
        eq("ayirici fark etmez", WifiFactory.ssidFromMac("0011.22AA.BBCC"), "ZTE_AABBCC")
        eq("bosluk ve satir sonu", WifiFactory.ssidFromMac(" 00:11:22:aa:bb:cc \n"), "ZTE_AABBCC")

        // Cikarilamiyorsa BOS donmeli; cagiran sifirlamayi sunmamali
        eq("kisa girdi", WifiFactory.ssidFromMac("aabb"), "")
        eq("null", WifiFactory.ssidFromMac(null), "")
        eq("hex yok", WifiFactory.ssidFromMac("::::::"), "")

        // Parola: kirpilir ama icerigi degismez
        eq("parola kirpilir", WifiFactory.keyFromFile("3JDN4DZY5Z\n"), "3JDN4DZY5Z")
        eq("bosluklu", WifiFactory.keyFromFile("  3JDN4DZY5Z  "), "3JDN4DZY5Z")
        // Kisa/bos parola AP'yi acik aga cevirirdi; reddedilir
        eq("cok kisa", WifiFactory.keyFromFile("1234"), "")
        eq("bos", WifiFactory.keyFromFile("   \n"), "")
        eq("null parola", WifiFactory.keyFromFile(null), "")
    }
}
