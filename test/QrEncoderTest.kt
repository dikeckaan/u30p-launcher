package com.kaandikec.u30plauncher.test

import com.kaandikec.u30plauncher.core.QrEncoder
import com.kaandikec.u30plauncher.core.QrMatrix
import com.kaandikec.u30plauncher.test.TestSupport.eq
import com.kaandikec.u30plauncher.test.TestSupport.ok

object QrEncoderTest {

    /**
     * Seviye M, bayt modu icin surum basina azami bayt sayisi (standart Tablo 7).
     *
     * Kodlayici bu degerleri tablodan degil kod sozcugu sayisindan hesapliyor;
     * burada bagimsiz bir kaynakla karsilastirmis oluyoruz.
     */
    private val CAPACITY = intArrayOf(14, 26, 42, 62, 84, 106, 122, 152, 180, 213)

    fun run() {
        val m = QrMatrix()

        // ---- boyut = 17 + 4 * surum, her surum icin ----
        // Kapasitenin tam sinirinda kalan metin o surumu, bir fazlasi bir sonraki
        // surumu secmeli; surum secimi burada tek testte dogrulaniyor.
        for (v in 1..10) {
            val exact = "X".repeat(CAPACITY[v - 1])
            ok("surum $v tam kapasite kodlanir", QrEncoder.encode(exact, m))
            eq("surum $v boyutu", m.size, 17 + 4 * v)
            eq("surum $v bit dizisi", m.bits.size, m.size * m.size)
            eq("surum $v geri okunan surum", QrEncoder.versionOf(exact), v)
            if (v < 10) {
                val overflow = "X".repeat(CAPACITY[v - 1] + 1)
                eq("surum $v tasinca ustune cikar", QrEncoder.versionOf(overflow), v + 1)
            }
        }

        // Surum 10'un kapasitesini asan metin reddedilir ve out'a dokunulmaz.
        QrEncoder.encode("HELLO", m)
        val keptSize = m.size
        val keptBits = m.bits
        ok("sigmayan metin reddedilir", !QrEncoder.encode("X".repeat(214), m))
        eq("basarisiz cagri boyutu bozmaz", m.size, keptSize)
        ok("basarisiz cagri bitleri bozmaz", m.bits === keptBits)

        // ---- islev desenleri ----
        // Surum 7'yi de deniyoruz: surum bilgisi bloklari orada devreye giriyor
        // ve finder/timing ile cakismamalari gerek.
        val texts = arrayOf("HELLO", "X".repeat(42), "X".repeat(122), "X".repeat(213))
        for (i in texts.indices) {
            val t = texts[i]
            ok("kodlanir [$i]", QrEncoder.encode(t, m))
            val last = m.size - 4
            ok("sol ust finder [$i]", finderOk(m, 3, 3))
            ok("sag ust finder [$i]", finderOk(m, last, 3))
            ok("sol alt finder [$i]", finderOk(m, 3, last))
            // Surum 2'den itibaren sag alt kosede hizalama deseni var; dorduncu
            // kosede finder YOK, tarayici yonelimi bu asimetriden anliyor.
            if (m.size > 21) ok("sag alt hizalama [$i]", alignmentOk(m, m.size - 7, m.size - 7))
            ok("yatay timing [$i]", timingOk(m, true))
            ok("dikey timing [$i]", timingOk(m, false))
            // 7.9.1: (8, 4*surum+9) modulu her zaman koyu
            ok("sabit koyu modul [$i]", m.dark(8, m.size - 8))
        }

        // Sinir disi istek acik doner; cizim dongusu tasmayi dert etmesin.
        QrEncoder.encode("HELLO", m)
        ok("sinir disi acik", !m.dark(-1, 0) && !m.dark(0, -1) && !m.dark(m.size, 0))

        // ---- deterministiklik ----
        val a = QrMatrix()
        val b = QrMatrix()
        val payload = QrEncoder.wifiPayload("ZTE U30 Pro", "kaandikec2026")
        ok("determinizm a", QrEncoder.encode(payload, a))
        ok("determinizm b", QrEncoder.encode(payload, b))
        eq("determinizm boyut", a.size, b.size)
        var same = true
        for (i in a.bits.indices) if (a.bits[i] != b.bits[i]) same = false
        ok("ayni girdi ayni cikti", same)

        // ---- bilinen metin: secilen surum ve maske ----
        // Regresyon capasi: veri bitleri, blok serpistirmesi veya ceza puani
        // degisirse bu iki deger kayar.
        eq("bilinen metin surumu", QrEncoder.versionOf(WIFI_KNOWN), 3)
        eq("bilinen metin maskesi", QrEncoder.maskOf(WIFI_KNOWN), 2)
        eq("kisa metin surumu", QrEncoder.versionOf("HELLO"), 1)
        eq("kisa metin maskesi", QrEncoder.maskOf("HELLO"), 4)

        // ---- wifiPayload ----
        eq(
            "duz yuk",
            QrEncoder.wifiPayload("U30P", "12345678"),
            "WIFI:T:WPA;S:U30P;P:12345678;;"
        )
        eq(
            "noktali virgul kacisi",
            QrEncoder.wifiPayload("a;b", "c;d"),
            "WIFI:T:WPA;S:a\\;b;P:c\\;d;;"
        )
        eq(
            "bes ozel karakter",
            QrEncoder.wifiPayload("\\;,:\"", "x"),
            "WIFI:T:WPA;S:\\\\\\;\\,\\:\\\";P:x;;"
        )
        eq(
            "bos ssid ve parola",
            QrEncoder.wifiPayload("", ""),
            "WIFI:T:WPA;S:;P:;;"
        )
        eq(
            "kacilmayan karakterler oldugu gibi",
            QrEncoder.wifiPayload("Ev Wifi-5G_2", "p@ss w0rd!"),
            "WIFI:T:WPA;S:Ev Wifi-5G_2;P:p@ss w0rd!;;"
        )

        // ISO-8859-1 disi SSID UTF-8 baytlarina duser. Kaynak dosyanin kodlamasina
        // bagli kalmamak icin kacis dizisiyle yaziliyor: "Sukru Ag" (S-cedilla,
        // u-umlaut, g-breve).
        val trSsid = "\u015e\u00fckr\u00fc A\u011f"
        ok("ascii disi ssid kodlanir", QrEncoder.encode(QrEncoder.wifiPayload(trSsid, "1234abcd"), m))
    }

    private const val WIFI_KNOWN = "WIFI:T:WPA;S:U30P;P:12345678;;"

    /**
     * Konum bulma deseni + ayirici: merkeze Chebyshev uzakligi 2 ve 4 olan
     * halkalar acik, digerleri koyu. Ayirici matrisin disina tasabilir.
     */
    private fun finderOk(m: QrMatrix, cx: Int, cy: Int): Boolean {
        for (dy in -4..4) {
            for (dx in -4..4) {
                val x = cx + dx
                val y = cy + dy
                if (x < 0 || y < 0 || x >= m.size || y >= m.size) continue
                val ax = if (dx < 0) -dx else dx
                val ay = if (dy < 0) -dy else dy
                val d = if (ax > ay) ax else ay
                if (m.dark(x, y) != (d != 2 && d != 4)) return false
            }
        }
        return true
    }

    /** Hizalama deseni: uzaklik 1 halkasi acik, merkez ve uzaklik 2 koyu. */
    private fun alignmentOk(m: QrMatrix, cx: Int, cy: Int): Boolean {
        for (dy in -2..2) {
            for (dx in -2..2) {
                val ax = if (dx < 0) -dx else dx
                val ay = if (dy < 0) -dy else dy
                val d = if (ax > ay) ax else ay
                if (m.dark(cx + dx, cy + dy) != (d != 1)) return false
            }
        }
        return true
    }

    /**
     * Zamanlama deseni: 6. satir/sutunda, finder desenleri arasinda kalan
     * bolumde ciftler koyu. Tarayici modul boyunu buradan olcuyor.
     */
    private fun timingOk(m: QrMatrix, horizontal: Boolean): Boolean {
        for (i in 8..m.size - 9) {
            val d = if (horizontal) m.dark(i, 6) else m.dark(6, i)
            if (d != (i % 2 == 0)) return false
        }
        return true
    }
}
