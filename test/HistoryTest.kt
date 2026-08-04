package com.kaandikec.u30plauncher.test

import com.kaandikec.u30plauncher.core.History
import com.kaandikec.u30plauncher.test.TestSupport.eq
import com.kaandikec.u30plauncher.test.TestSupport.ok

object HistoryTest {
    fun run() {
        // bos tampon: olcum yok, min/max bilinmiyor
        val e = History(4)
        eq("bos size", e.size, 0)
        eq("bos min", e.min(), History.UNKNOWN)
        eq("bos max", e.max(), History.UNKNOWN)
        eq("bos newest", e.newest(), History.UNKNOWN)
        eq("aralik disi get", e.get(0), History.UNKNOWN)

        // dolmadan once: en eski indeks 0
        val h = History(4)
        h.add(10)
        h.add(20)
        h.add(30)
        eq("size 3", h.size, 3)
        eq("get eski", h.get(0), 10L)
        eq("get yeni", h.get(2), 30L)
        eq("newest", h.newest(), 30L)
        eq("min", h.min(), 10L)
        eq("max", h.max(), 30L)

        // tasma: en eskiler dusuruluyor, size kapasitede sabit
        h.add(40)
        h.add(50) // 10 dusmeli
        eq("dolu size", h.size, 4)
        eq("tasmada en eski", h.get(0), 20L)
        eq("tasmada en yeni", h.get(3), 50L)
        eq("tasma min", h.min(), 20L)
        eq("tasma max", h.max(), 50L)

        // birden fazla tam tur: sira korunmali
        val w = History(3)
        for (i in 1..10) w.add(i.toLong()) // son ucu: 8,9,10
        eq("cok tur size", w.size, 3)
        eq("cok tur eski", w.get(0), 8L)
        eq("cok tur orta", w.get(1), 9L)
        eq("cok tur yeni", w.get(2), 10L)

        // UNKNOWN ornekler min/max disinda; egride bosluk olur
        val g = History(5)
        g.add(-90)
        g.add(History.UNKNOWN)
        g.add(-100)
        g.add(History.UNKNOWN)
        g.add(-80)
        eq("bosluklu min", g.min(), -100L)
        eq("bosluklu max", g.max(), -80L)
        ok("bosluk korundu", g.get(1) == History.UNKNOWN && g.get(3) == History.UNKNOWN)

        // tamami bilinmiyorsa min/max da bilinmiyor
        val u = History(3)
        u.add(History.UNKNOWN)
        u.add(History.UNKNOWN)
        eq("hepsi bosluk min", u.min(), History.UNKNOWN)
        eq("hepsi bosluk max", u.max(), History.UNKNOWN)
        eq("hepsi bosluk newest", u.newest(), History.UNKNOWN)

        // negatif degerler (RSRP dBm) dogru siralanir
        val r = History(4)
        r.add(-95)
        r.add(-70)
        r.add(-115)
        eq("negatif min", r.min(), -115L)
        eq("negatif max", r.max(), -70L)

        // clear tampani bosaltir
        r.clear()
        eq("clear size", r.size, 0)
        eq("clear min", r.min(), History.UNKNOWN)
    }
}
