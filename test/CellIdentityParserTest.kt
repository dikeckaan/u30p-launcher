package com.kaandikec.u30plauncher.test

import com.kaandikec.u30plauncher.core.CellIdentity
import com.kaandikec.u30plauncher.core.CellIdentityParser
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.test.TestSupport.eq
import com.kaandikec.u30plauncher.test.TestSupport.ok

object CellIdentityParserTest {
    // Cihazdan alinmis gercek dumpsys parcasi
    private const val REAL =
        "NetworkRegistrationInfo{ domain=PS transportType=WWAN registrationState=HOME " +
            "accessNetworkTechnology=LTE rejectCause=0 emergencyEnabled=false " +
            "availableServices=[DATA,MMS] cellIdentity=CellIdentityLte:{ mCi=88103723 " +
            "mPci=344 mTac=21054 mEarfcn=1279 mBands=[3] mBandwidth=15000 mMcc=286 " +
            "mMnc=03 mAlphaLong=KAANCELL  mAlphaShort=TT mAdditionalPlmns={} mCsgInfo=null} " +
            "voiceSpecificInfo=null}"

    fun run() {
        val c = CellIdentity()

        ok("gercek cikti ayristi", CellIdentityParser.parse(REAL, c))
        eq("ci", c.ci, 88103723)
        eq("pci", c.pci, 344)
        eq("tac", c.tac, 21054)
        eq("earfcn", c.earfcn, 1279)
        eq("band", c.band, 3)
        eq("bandwidth", c.bandwidthKhz, 15000)

        // maskelenmis cikti: alanlar var ama degerler yok
        val masked = "cellIdentity=CellIdentityLte:{ mCi=2147483647 mPci=2147483647 }"
        CellIdentityParser.parse(masked, c)
        eq("maskeli ci ham okunur", c.ci, 2147483647)

        // hic hucre kimligi yok
        ok("kimlik yok", !CellIdentityParser.parse("cellIdentity=null", c))
        ok("bos metin", !CellIdentityParser.parse("", c))

        // eksik alanlar UNKNOWN kalmali
        val partial = "CellIdentityLte:{ mPci=100 }"
        ok("kismi ayristi", CellIdentityParser.parse(partial, c))
        eq("kismi pci", c.pci, 100)
        eq("kismi tac yok", c.tac, Snapshot.UNKNOWN)
        eq("kismi band yok", c.band, Snapshot.UNKNOWN)

        // negatif deger
        val neg = "CellIdentityLte:{ mCi=-1 mPci=7 }"
        ok("negatif ayristi", CellIdentityParser.parse(neg, c))
        eq("negatif ci", c.ci, -1)

        // 5G bicimi
        val nr = "CellIdentityNr:{ mNci=12345 mPci=55 mTac=99 mNrarfcn=633984 mBands=[78] }"
        ok("nr ayristi", CellIdentityParser.parse(nr, c))
        eq("nr pci", c.pci, 55)
        eq("nr earfcn", c.earfcn, 633984)
        eq("nr band", c.band, 78)

        // Int sinirini asan mNci UNKNOWN olmali, cokmemeli
        val huge = "CellIdentityNr:{ mNci=123456789012 mPci=7 }"
        ok("buyuk nci ayristi", CellIdentityParser.parse(huge, c))
        eq("buyuk nci UNKNOWN", c.ci, Snapshot.UNKNOWN)
        eq("buyuk nci pci", c.pci, 7)
    }
}
