package com.kaandikec.u30plauncher.test

import kotlin.system.exitProcess

fun main() {
    FmtTest.run()
    ProcNetParserTest.run()
    UsageCalcTest.run()
    CyclePeriodTest.run()
    WifiFactoryTest.run()
    HistoryTest.run()
    WifiCredGenTest.run()
    SmsTextTest.run()
    QrEncoderTest.run()
    ProcPidStatParserTest.run()
    SnapshotTest.run()
    CellIdentityParserTest.run()
    WifiInfoParserTest.run()
    RateWindowTest.run()
    BatteryEstimateTest.run()
    TimeAverageTest.run()
    BatteryHistoryTest.run()
    SystemParsersTest.run()
    LockSecretTest.run()
    LockTransitionTest.run()
    exitProcess(TestSupport.report())
}
