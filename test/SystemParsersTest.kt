package com.kaandikec.u30plauncher.test

import com.kaandikec.u30plauncher.core.CpuTimes
import com.kaandikec.u30plauncher.core.MemInfo
import com.kaandikec.u30plauncher.core.MemInfoParser
import com.kaandikec.u30plauncher.core.ProcStatParser
import com.kaandikec.u30plauncher.core.Snapshot
import com.kaandikec.u30plauncher.test.TestSupport.eq
import com.kaandikec.u30plauncher.test.TestSupport.ok

object SystemParsersTest {
    // Cihazdan alinmis gercek satirlar
    private val STAT = """cpu  82876 9839 118280 4971940 5653 24447 11349 0 0 0
cpu0 20000 2000 30000 1200000 1400 6000 2800 0 0 0
intr 12345
""".toByteArray()

    private val MEMINFO = """MemTotal:        1468576 kB
MemFree:          120344 kB
MemAvailable:     480768 kB
Buffers:           12000 kB
""".toByteArray()

    fun run() {
        val c = CpuTimes()
        ok("stat ayristi", ProcStatParser.parse(STAT, STAT.size, c))
        // 82876+9839+118280+4971940+5653+24447+11349 = 5224384
        eq("toplam", c.total, 5_224_384L)
        // idle + iowait = 4971940 + 5653
        eq("bosta", c.idle, 4_977_593L)

        // Iki ornek arasi kullanim
        val prev = CpuTimes().apply { total = 1_000_000; idle = 900_000 }
        val now = CpuTimes().apply { total = 1_001_000; idle = 900_400 }
        eq("kullanim %60", ProcStatParser.usagePercent(prev, now), 60)

        val same = CpuTimes().apply { total = 1_000_000; idle = 900_000 }
        eq("fark yok", ProcStatParser.usagePercent(same, same), Snapshot.UNKNOWN)

        // Sayac sifirlanmis (yeniden baslatma)
        val reset = CpuTimes().apply { total = 500; idle = 400 }
        eq("sayac sifirlandi", ProcStatParser.usagePercent(prev, reset), Snapshot.UNKNOWN)

        // Tamamen bosta / tamamen mesgul
        eq("tam bosta", ProcStatParser.usagePercent(
            CpuTimes().apply { total = 0; idle = 0 },
            CpuTimes().apply { total = 1000; idle = 1000 }), 0)
        eq("tam mesgul", ProcStatParser.usagePercent(
            CpuTimes().apply { total = 0; idle = 0 },
            CpuTimes().apply { total = 1000; idle = 0 }), 100)

        // Cekirdek bazli satirla baslayan girdi kabul edilmemeli
        val perCore = "cpu0 1 2 3 4 5 6 7\n".toByteArray()
        ok("cpu0 reddedildi", !ProcStatParser.parse(perCore, perCore.size, c))
        ok("bos girdi", !ProcStatParser.parse(ByteArray(0), 0, c))

        // meminfo
        val m = MemInfo()
        ok("meminfo ayristi", MemInfoParser.parse(MEMINFO, MEMINFO.size, m))
        eq("toplam bellek", m.totalKb, 1_468_576L)
        eq("kullanilabilir", m.availableKb, 480_768L)
        eq("kullanilan", m.usedKb, 1_468_576L - 480_768L)

        // MemAvailable yoksa toplam yine okunmali
        val noAvail = "MemTotal:  1000 kB\nMemFree: 100 kB\n".toByteArray()
        val m2 = MemInfo()
        ok("MemAvailable yok", MemInfoParser.parse(noAvail, noAvail.size, m2))
        eq("toplam okundu", m2.totalKb, 1000L)

        // Bozuk girdi cokmemeli
        val junk = "alakasiz satir\n".toByteArray()
        ok("bozuk meminfo", !MemInfoParser.parse(junk, junk.size, MemInfo()))
    }
}
