package com.kaandikec.u30plauncher.test

import com.kaandikec.u30plauncher.core.IfCounters
import com.kaandikec.u30plauncher.core.ProcNetParser
import com.kaandikec.u30plauncher.test.TestSupport.eq
import com.kaandikec.u30plauncher.test.TestSupport.ok

object ProcNetParserTest {
    // Cihazdan alinmis gercek /proc/net/dev ciktisi
    private val SAMPLE = """
Inter-|   Receive                                                |  Transmit
 face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
    lo: 2008371024 1519591    0    0    0     0          0         0 2008371024 1519591    0    0    0     0       0          0
sipa_eth0:  238844     677    0    0    0     0          0         0   243741     752   39    0    0     0       0          0
   br0:  151954    1887    0    0    0     0          0       201  2705176    3245    0    0    0     0       0          0
  tun0:  120109     335    0    0    0     0          0         0   151961     419    0    0    0     0       0          0
""".trimIndent().toByteArray()

    fun run() {
        val c = IfCounters()

        ok("sipa_eth0 bulundu", ProcNetParser.parse(SAMPLE, SAMPLE.size, "sipa_eth0", c))
        eq("sipa_eth0 rx", c.rx, 238844L)
        eq("sipa_eth0 tx", c.tx, 243741L)

        ok("br0 bulundu", ProcNetParser.parse(SAMPLE, SAMPLE.size, "br0", c))
        eq("br0 rx", c.rx, 151954L)
        eq("br0 tx", c.tx, 2705176L)

        ok("tun0 bulundu", ProcNetParser.parse(SAMPLE, SAMPLE.size, "tun0", c))
        eq("tun0 tx", c.tx, 151961L)

        ok("lo bulundu", ProcNetParser.parse(SAMPLE, SAMPLE.size, "lo", c))
        eq("lo rx", c.rx, 2008371024L)

        ok("wlan0 yok", !ProcNetParser.parse(SAMPLE, SAMPLE.size, "wlan0", c))

        // kismi onek eslesmemeli: "br" diye bir arayuz yok
        ok("br yok", !ProcNetParser.parse(SAMPLE, SAMPLE.size, "br", c))
        // "sipa_eth" de "sipa_eth0"i eslestirmemeli
        ok("sipa_eth yok", !ProcNetParser.parse(SAMPLE, SAMPLE.size, "sipa_eth", c))

        val junk = "garbage without colons\n".toByteArray()
        ok("bozuk girdi", !ProcNetParser.parse(junk, junk.size, "br0", c))
        ok("bos girdi", !ProcNetParser.parse(ByteArray(0), 0, "br0", c))

        // sondaki satirsonu olmayan girdi de calismali
        val noNewline = "  br0: 10 1 0 0 0 0 0 0 20 2 0 0 0 0 0 0".toByteArray()
        ok("satirsonu yok", ProcNetParser.parse(noNewline, noNewline.size, "br0", c))
        eq("satirsonu yok rx", c.rx, 10L)
        eq("satirsonu yok tx", c.tx, 20L)
    }
}
