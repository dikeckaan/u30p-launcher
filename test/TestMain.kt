package com.kaandikec.u30plauncher.test

import kotlin.system.exitProcess

fun main() {
    FmtTest.run()
    ProcNetParserTest.run()
    UsageCalcTest.run()
    SnapshotTest.run()
    CellIdentityParserTest.run()
    WifiInfoParserTest.run()
    exitProcess(TestSupport.report())
}
