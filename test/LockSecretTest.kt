package com.kaandikec.u30plauncher.test

import com.kaandikec.u30plauncher.core.LockSecret
import com.kaandikec.u30plauncher.test.TestSupport.eq
import com.kaandikec.u30plauncher.test.TestSupport.ok

object LockSecretTest {
    fun run() {
        // Ozet duz metni sizdirmamali ve kararli olmali
        val h = LockSecret.hash("0-4-8")
        eq("ozet uzunlugu", h.length, 64)
        ok("duz metin degil", !h.contains("0-4-8"))
        eq("kararli", h, LockSecret.hash("0-4-8"))
        ok("farkli girdi farkli ozet", h != LockSecret.hash("0-4-7"))

        ok("dogru kod eslesir", LockSecret.matches("0-4-8", h))
        ok("yanlis kod eslesmez", !LockSecret.matches("0-4-7", h))

        // Bos degerler asla acmamali
        ok("bos kod", !LockSecret.matches("", h))
        ok("bos ozet", !LockSecret.matches("0-4-8", ""))
        ok("ikisi de bos", !LockSecret.matches("", ""))
        eq("bos girdinin ozeti bos", LockSecret.hash(""), "")

        // Desen kodlama
        eq("desen kodlandi", LockSecret.encodePattern(listOf(0, 4, 8, 7)), "0-4-8-7")
        ok("4 nokta yeterli", LockSecret.isPatternValid(listOf(0, 1, 2, 3)))
        ok("3 nokta yetersiz", !LockSecret.isPatternValid(listOf(0, 1, 2)))
        ok("bos desen", !LockSecret.isPatternValid(emptyList()))

        // PIN dogrulama
        ok("4 hane gecerli", LockSecret.isPinValid("1234"))
        ok("3 hane gecersiz", !LockSecret.isPinValid("123"))
        ok("5 hane gecersiz", !LockSecret.isPinValid("12345"))
        ok("harf gecersiz", !LockSecret.isPinValid("12a4"))
        ok("bos gecersiz", !LockSecret.isPinValid(""))

        // Ayni hucre dizisi farkli sirayla farkli sirdir
        ok("sira onemli",
            LockSecret.hash(LockSecret.encodePattern(listOf(0, 1, 2, 3))) !=
                LockSecret.hash(LockSecret.encodePattern(listOf(3, 2, 1, 0))))
    }
}
