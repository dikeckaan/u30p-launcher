package com.kaandikec.u30plauncher.test

import com.kaandikec.u30plauncher.core.ProcPidStat
import com.kaandikec.u30plauncher.core.ProcPidStatParser
import com.kaandikec.u30plauncher.test.TestSupport.eq
import com.kaandikec.u30plauncher.test.TestSupport.ok

object ProcPidStatParserTest {
    private fun parse(line: String): ProcPidStat? {
        val bytes = line.toByteArray(Charsets.US_ASCII)
        val out = ProcPidStat()
        return if (ProcPidStatParser.parse(bytes, bytes.size, out)) out else null
    }

    fun run() {
        // duz durum: comm, ppid, utime+stime
        // alanlar: pid comm state ppid pgrp session tty tpgid flags minflt
        //          cminflt majflt cmajflt utime stime ...
        val a = parse("999 (surfaceflinger) S 1 999 999 0 -1 4194560 100 0 0 0 500 300 7 8 20 0")
        ok("duz ayristi", a != null)
        eq("duz comm", a!!.comm, "surfaceflinger")
        eq("duz ppid", a.ppid, 1)
        eq("duz ticks", a.cpuTicks, 800L)

        // comm ic parantez iceriyor: son ')' esas alinmali
        val b = parse("800 (foo (bar)) S 3 800 800 0 -1 0 0 0 0 0 7 8 0 0")
        ok("parantezli ayristi", b != null)
        eq("parantezli comm", b!!.comm, "foo (bar)")
        eq("parantezli ppid", b.ppid, 3)
        eq("parantezli ticks", b.cpuTicks, 15L)

        // cekirdek parcacigi: ppid 2 (kthreadd cocugu)
        val k = parse("47 (kworker/0:1H) S 2 0 0 0 -1 69238880 0 0 0 0 12 34 0 0")
        ok("kworker ayristi", k != null)
        eq("kworker ppid", k!!.ppid, 2)
        eq("kworker ticks", k.cpuTicks, 46L)

        // bozuk giris: parantez yok
        ok("parantezsiz reddedildi", parse("bosluk yok parantez yok") == null)
        ok("bos reddedildi", parse("") == null)
    }
}
