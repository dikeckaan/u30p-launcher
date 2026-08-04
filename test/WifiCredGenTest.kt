package com.kaandikec.u30plauncher.test

import com.kaandikec.u30plauncher.core.QrEncoder
import com.kaandikec.u30plauncher.core.WifiCredGen
import com.kaandikec.u30plauncher.test.TestSupport.eq
import com.kaandikec.u30plauncher.test.TestSupport.ok
import java.util.Random

object WifiCredGenTest {
    fun run() {
        // ---- alfabe ----
        // Bu isaretler cikarilmis olmali; ekrandan bakip elle yazarken
        // birbirine karisiyorlar. Kucuk harf de yok.
        for (c in "01258BIOSZabkl") {
            ok("alfabede '$c' yok", WifiCredGen.ALPHABET.indexOf(c) < 0)
        }
        eq("alfabe uzunlugu", WifiCredGen.ALPHABET.length, 26)
        for (i in WifiCredGen.ALPHABET.indices) {
            eq(
                "alfabede tekrar yok: ${WifiCredGen.ALPHABET[i]}",
                WifiCredGen.ALPHABET.indexOf(WifiCredGen.ALPHABET[i]), i
            )
        }
        // Yuk icindeki ayraclar kacis gerektirir; alfabede hicbiri olmamali
        for (c in "\\;,:\"") {
            ok("alfabede ayrac '$c' yok", WifiCredGen.ALPHABET.indexOf(c) < 0)
        }

        // ---- bicim ----
        val cred = WifiCredGen.generate(Random(42))
        ok("ssid oneki", cred.ssid.startsWith(WifiCredGen.SSID_PREFIX))
        eq("ssid uzunlugu", cred.ssid.length, WifiCredGen.SSID_PREFIX.length + 4)
        eq("parola uzunlugu", cred.passphrase.length, WifiCredGen.PASS_LEN)
        ok("parola en az 12", cred.passphrase.length >= 12)
        ok("ssid gecerli", WifiCredGen.isValidSsid(cred.ssid))
        ok("parola gecerli", WifiCredGen.isValidPassphrase(cred.passphrase))

        val random = cred.ssid.substring(WifiCredGen.SSID_PREFIX.length)
        for (i in random.indices) {
            ok("ssid[$i] alfabede", WifiCredGen.ALPHABET.indexOf(random[i]) >= 0)
        }
        for (i in cred.passphrase.indices) {
            ok("parola[$i] alfabede", WifiCredGen.ALPHABET.indexOf(cred.passphrase[i]) >= 0)
        }

        // ---- sinir dogrulamalari ----
        ok("bos ssid gecersiz", !WifiCredGen.isValidSsid(""))
        ok("32 bayt ssid gecerli", WifiCredGen.isValidSsid("A".repeat(32)))
        ok("33 bayt ssid gecersiz", !WifiCredGen.isValidSsid("A".repeat(33)))
        // U+015F (s cedilla) UTF-8'de 2 bayt: sinir karakterle degil baytla
        // olculmeli. Kaynak dosyanin kodlamasina bagli kalmamak icin escape.
        val twoByte = "\u015F"
        ok("16 karakterlik cok baytli ssid", WifiCredGen.isValidSsid(twoByte.repeat(16)))
        ok("17 karakterlik cok baytli ssid gecersiz", !WifiCredGen.isValidSsid(twoByte.repeat(17)))
        ok("7 karakter parola gecersiz", !WifiCredGen.isValidPassphrase("A".repeat(7)))
        ok("8 karakter parola gecerli", WifiCredGen.isValidPassphrase("A".repeat(8)))
        ok("63 karakter parola gecerli", WifiCredGen.isValidPassphrase("A".repeat(63)))
        ok("64 karakter parola gecersiz", !WifiCredGen.isValidPassphrase("A".repeat(64)))

        // ---- tekrarlanabilirlik ----
        // Ayni tohum ayni sonucu vermeli; yoksa uretimin nereden geldigini
        // sinayamayiz. Farkli tohum farkli sonuc vermeli; ayni cikmasi
        // rastgeleligin hic kullanilmadigi anlamina gelirdi.
        val a = WifiCredGen.generate(Random(7))
        val b = WifiCredGen.generate(Random(7))
        val c2 = WifiCredGen.generate(Random(8))
        eq("ayni tohum ayni ssid", a.ssid, b.ssid)
        eq("ayni tohum ayni parola", a.passphrase, b.passphrase)
        ok("farkli tohum farkli ssid", a.ssid != c2.ssid)
        ok("farkli tohum farkli parola", a.passphrase != c2.passphrase)

        // ---- dagilim ----
        // Alfabenin bir bolumu hic cikmasaydi (orn. yanlis ust sinir) parola
        // sanildigindan zayif olurdu. Tohum sabit oldugu icin bu deterministik.
        val hist = IntArray(WifiCredGen.ALPHABET.length)
        val rnd = Random(2026)
        for (i in 0 until 60) {
            val p = WifiCredGen.passphrase(rnd)
            for (j in p.indices) hist[WifiCredGen.ALPHABET.indexOf(p[j])]++
        }
        for (i in hist.indices) {
            ok("'${WifiCredGen.ALPHABET[i]}' uretilebiliyor", hist[i] > 0)
        }

        // ---- karekoda sigiyor mu ----
        // Yuk 41 bayt: surum 3'un 42 baytlik siniriyla arasinda tek bayt var.
        // Onek veya uzunluk buyurse surum atlar, moduller kuculur ve 240 px
        // ekranda okunmaz hale gelir; sinir burada tutuluyor.
        val payload = QrEncoder.wifiPayload(cred.ssid, cred.passphrase)
        ok("yukta kacis gerekmiyor", payload.indexOf('\\') < 0)
        val version = QrEncoder.versionOf(payload)
        ok("yuk kodlanabiliyor", version > 0)
        ok("surum 4'u asmiyor (gercek: $version)", version <= 4)
    }
}
