package com.kaandikec.u30plauncher.test

import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.test.TestSupport.ok

object SnapshotTest {
    fun run() {
        val a = Snapshot(
            operator = "KAANCELL", netType = "LTE", band = 3, hasService = true,
            rsrp = -100, rsrq = -11, sinr = 60, pci = 344, earfcn = 1279,
            tac = 21054, ci = 88103723, bandwidthKhz = 15000, signalLevel = 3,
            rxSpeed = 1_550_000, txSpeed = 137_500,
            todayBytes = 840704, monthBytes = 23413089239L,
            batteryPct = 20, batteryUa = -565000, batteryTempC = 350, cpuTempC = 424,
            vpnUp = true, clients = 2, clockMinuteOfDay = 42,
            rootAvailable = true, phonePermission = true
        )
        val b = a.copy()
        ok("ayni icerik esit", a == b)
        ok("hash esit", a.hashCode() == b.hashCode())

        // Her alanin esitlige katildigini dogrula — bir alan unutulursa
        // "degismediyse cizme" optimizasyonu o alani ekranda dondurur.
        ok("hiz farki", a != a.copy(rxSpeed = 1_550_001))
        ok("upload farki", a != a.copy(txSpeed = 1))
        ok("rsrp farki", a != a.copy(rsrp = -101))
        ok("rsrq farki", a != a.copy(rsrq = -12))
        ok("sinr farki", a != a.copy(sinr = 61))
        ok("pci farki", a != a.copy(pci = 345))
        ok("earfcn farki", a != a.copy(earfcn = 1280))
        ok("tac farki", a != a.copy(tac = 21055))
        ok("ci farki", a != a.copy(ci = 88103724))
        ok("bant farki", a != a.copy(band = 1))
        ok("bant genisligi farki", a != a.copy(bandwidthKhz = 20000))
        ok("seviye farki", a != a.copy(signalLevel = 4))
        ok("operator farki", a != a.copy(operator = "X"))
        ok("teknoloji farki", a != a.copy(netType = "5G"))
        ok("servis farki", a != a.copy(hasService = false))
        ok("bugun farki", a != a.copy(todayBytes = 1))
        ok("ay farki", a != a.copy(monthBytes = 1))
        ok("pil farki", a != a.copy(batteryPct = 21))
        ok("akim farki", a != a.copy(batteryUa = 0))
        ok("pil sicaklik farki", a != a.copy(batteryTempC = 351))
        ok("cpu sicaklik farki", a != a.copy(cpuTempC = 425))
        ok("vpn farki", a != a.copy(vpnUp = false))
        ok("istemci farki", a != a.copy(clients = 3))
        ok("dakika farki", a != a.copy(clockMinuteOfDay = 43))
        ok("root farki", a != a.copy(rootAvailable = false))
        ok("izin farki", a != a.copy(phonePermission = false))

        ok("EMPTY kendine esit", Snapshot.EMPTY == Snapshot())
    }
}
