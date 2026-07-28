package com.kaandikec.u30plauncher.test

object TestSupport {
    private var total = 0
    private var failed = 0

    fun <T> eq(name: String, actual: T, expected: T) {
        total++
        if (actual != expected) {
            failed++
            println("FAIL  $name\n        beklenen: <$expected>\n        gelen:    <$actual>")
        }
    }

    fun ok(name: String, cond: Boolean) = eq(name, cond, true)

    fun report(): Int {
        println("\n$total test, $failed basarisiz")
        return if (failed == 0) 0 else 1
    }
}
