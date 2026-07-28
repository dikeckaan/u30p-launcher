package com.kaandikec.u30plauncher.test

import com.kaandikec.u30plauncher.core.UsageCalc
import com.kaandikec.u30plauncher.core.UsageState
import com.kaandikec.u30plauncher.test.TestSupport.eq

object UsageCalcTest {
    fun run() {
        val d0 = 100
        val m0 = 24
        val s = UsageState()

        // ilk okuma yalnizca taban kurar — gecmis trafigi bugune yazmayalim
        UsageCalc.update(s, 1000, d0, m0)
        eq("ilk okuma gun", s.dayBytes, 0L)
        eq("ilk okuma ay", s.monthBytes, 0L)
        eq("ilk okuma taban", s.lastTotal, 1000L)

        UsageCalc.update(s, 1500, d0, m0)
        eq("artis gun", s.dayBytes, 500L)
        eq("artis ay", s.monthBytes, 500L)

        UsageCalc.update(s, 2000, d0, m0)
        eq("ikinci artis gun", s.dayBytes, 1000L)

        // reboot: sayac sifirlandi, toplam dustu — delta ham degerdir
        UsageCalc.update(s, 300, d0, m0)
        eq("reboot sonrasi gun", s.dayBytes, 1300L)
        eq("reboot sonrasi taban", s.lastTotal, 300L)

        // gun devri: gunluk sifirlanir, aylik devam eder
        UsageCalc.update(s, 800, d0 + 1, m0)
        eq("gun devri gunluk", s.dayBytes, 500L)
        eq("gun devri aylik", s.monthBytes, 1800L)

        // ay devri: ikisi de sifirlanir
        UsageCalc.update(s, 1000, d0 + 2, m0 + 1)
        eq("ay devri gunluk", s.dayBytes, 200L)
        eq("ay devri aylik", s.monthBytes, 200L)

        // ayni toplam iki kez: delta sifir
        UsageCalc.update(s, 1000, d0 + 2, m0 + 1)
        eq("delta sifir", s.dayBytes, 200L)
        eq("delta sifir ay", s.monthBytes, 200L)

        // devir aninda gelen delta yeni gune yazilmali, eskiye degil
        val t = UsageState()
        UsageCalc.update(t, 0, d0, m0)
        UsageCalc.update(t, 100, d0, m0)
        eq("devir oncesi", t.dayBytes, 100L)
        UsageCalc.update(t, 400, d0 + 1, m0)
        eq("devirde delta yeni gune", t.dayBytes, 300L)
    }
}
