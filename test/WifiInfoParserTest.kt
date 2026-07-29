package com.kaandikec.u30plauncher.test

import com.kaandikec.u30plauncher.core.ArpParser
import com.kaandikec.u30plauncher.core.Client
import com.kaandikec.u30plauncher.core.SoftApInfo
import com.kaandikec.u30plauncher.core.SoftApParser
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.test.TestSupport.eq
import com.kaandikec.u30plauncher.test.TestSupport.ok

object WifiInfoParserTest {
    // Cihazdan alinmis gercek dosya parcasi
    private const val XML = """<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<WifiConfigStoreData>
<int name="Version" value="3" />
<SoftAp>
<string name="WifiSsid">&quot;ornek-ssid&quot;</string>
<string name="Bssid">aa:bb:cc:00:11:22</string>
<boolean name="HiddenSSID" value="false" />
<int name="SecurityType" value="2" />
<string name="Passphrase">ornek-parola</string>
<int name="MaxNumberOfClients" value="16" />
<BandChannelMap>
<BandChannel>
<int name="Band" value="3" />
</BandChannel>
</BandChannelMap>
</SoftAp>"""

    private const val ARP = """IP address       HW type     Flags       HW address            Mask     Device
192.168.0.72     0x1         0x2         aa:bb:cc:00:11:33     *        br0
192.168.0.169    0x1         0x2         aa:bb:cc:00:11:44     *        br0
192.168.0.200    0x1         0x0         00:00:00:00:00:00     *        br0
10.0.0.5         0x1         0x2         aa:bb:cc:dd:ee:ff     *        wlan1"""

    fun run() {
        val ap = SoftApInfo()
        ok("softap ayristi", SoftApParser.parse(XML, ap))
        eq("ssid tirnaksiz", ap.ssid, "ornek-ssid")
        eq("parola", ap.passphrase, "ornek-parola")
        eq("max istemci", ap.maxClients, 16)
        eq("bant", ap.band, 3)

        // bos / bozuk girdi
        val empty = SoftApInfo()
        ok("bos xml", !SoftApParser.parse("", empty))
        ok("alakasiz xml", !SoftApParser.parse("<foo/>", empty))

        // XML kacislari cozulmeli
        val esc = SoftApInfo()
        SoftApParser.parse(
            "<string name=\"WifiSsid\">&quot;a&amp;b&quot;</string>" +
                "<string name=\"Passphrase\">x&lt;y&gt;z</string>", esc
        )
        eq("ssid kacis", esc.ssid, "a&b")
        eq("parola kacis", esc.passphrase, "x<y>z")

        // ARP
        val clients = ArrayList<Client>()
        ArpParser.parse(ARP, "br0", clients)
        eq("br0 istemci sayisi", clients.size, 2)
        eq("ilk ip", clients[0].ip, "192.168.0.72")
        eq("ilk mac", clients[0].mac, "aa:bb:cc:00:11:33")
        eq("ikinci ip", clients[1].ip, "192.168.0.169")

        // baska arayuz istenirse yalnizca o dondurulur
        ArpParser.parse(ARP, "wlan1", clients)
        eq("wlan1 sayisi", clients.size, 1)
        eq("wlan1 ip", clients[0].ip, "10.0.0.5")

        // bilinmeyen arayuz bos liste
        ArpParser.parse(ARP, "eth9", clients)
        eq("bilinmeyen arayuz", clients.size, 0)

        // yalnizca baslik satiri
        ArpParser.parse("IP address HW type Flags HW address Mask Device", "br0", clients)
        eq("yalnizca baslik", clients.size, 0)

        // bos metin cokmemeli
        ArpParser.parse("", "br0", clients)
        eq("bos arp", clients.size, 0)

        eq("bant bilinmiyorsa", SoftApInfo().band, Snapshot.UNKNOWN)
    }
}
