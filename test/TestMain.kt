package com.kaandikec.u30plauncher.test

import kotlin.system.exitProcess

fun main() {
    FmtTest.run()
    ProcNetParserTest.run()
    UsageCalcTest.run()
    SnapshotTest.run()
    exitProcess(TestSupport.report())
}
