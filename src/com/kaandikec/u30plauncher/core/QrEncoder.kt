package com.kaandikec.u30plauncher.core

/**
 * Kare QR matrisi; true = koyu modul. Quiet zone DAHIL DEGIL.
 *
 * Quiet zone'u disarida birakmak bilincli: 240x240 yuvarlak ekranda karekodun
 * cevresinde zaten arka plan var, sessiz bolgeyi matrise katmak modul basina
 * dusen piksel sayisini gereksiz yere kucultuyordu.
 */
class QrMatrix {
    var size: Int = 0
    var bits: BooleanArray = BooleanArray(0)

    /** Sinir disi istek acik sayilir; cizim dongusu tasmayi dert etmesin. */
    fun dark(x: Int, y: Int): Boolean {
        if (x < 0 || y < 0 || x >= size || y >= size) return false
        return bits[y * size + x]
    }
}

/**
 * ISO/IEC 18004 uyumlu QR kodlayici — bayt modu, hata duzeltme seviyesi M,
 * surum 1..10.
 *
 * Neden sifirdan: projede sifir bagimlilik kurali var, ZXing eklenemiyor.
 * Neden yalnizca bayt modu: tek musteri WiFi yuku ("WIFI:T:WPA;S:...") ve
 * icindeki ';' ile kucuk harfler alfanumerik kumede yok — sayisal/alfanumerik
 * modlar bu metin icin zaten hic secilmezdi, tablolarini tasimak bos yuk olurdu.
 * Neden seviye M: yaklasik %15 kurtarma; 240 px ekranda modul boyu kucuk
 * kaldigi icin L'nin toleransi az, Q/H ise surumu buyutup modulleri daha da
 * kuculttugu icin ters etki yapiyordu.
 *
 * Bu kod sicak yolda degil (karekod ekran acilinca bir kez uretilir), bu yuzden
 * gecici dizi ayirmak serbest; sonuc [QrMatrix] icine yazilir.
 */
object QrEncoder {

    /** Surum 10'un ustune cikmiyoruz: 57x57 modul 240 px ekranda zaten sinirda. */
    private const val MAX_VERSION = 10

    // ---------------------------------------------------------------------
    // Tablolar
    // ---------------------------------------------------------------------

    /**
     * Surum basina TOPLAM kod sozcugu (veri + hata duzeltme), surum 1..10.
     *
     * Standardin Tablo 1'inden. Modul sayisindan hesaplanabilir ama formul
     * hizalama desenlerinin kapladigi alani da cikarmak zorunda oldugu icin
     * tablodan okumak hem kisa hem hatasiz.
     */
    private val TOTAL_CODEWORDS = intArrayOf(26, 44, 70, 100, 134, 172, 196, 242, 292, 346)

    /** Seviye M: blok basina hata duzeltme sozcugu, surum 1..10 (Tablo 13-22). */
    private val ECC_PER_BLOCK = intArrayOf(10, 16, 26, 18, 24, 16, 18, 22, 22, 26)

    /** Seviye M: toplam blok sayisi, surum 1..10. */
    private val BLOCK_COUNT = intArrayOf(1, 1, 1, 2, 2, 4, 4, 4, 5, 5)

    /**
     * Hizalama deseni merkez koordinatlari, surum 1..10 (Tablo E.1).
     *
     * Genel formulle uretilebilir ama formulun yuvarlama davranisi surum 32'de
     * bozuluyor; 10 surum icin duz tablo hem daha kisa hem tartisilmaz.
     */
    private val ALIGN_POS = arrayOf(
        intArrayOf(),
        intArrayOf(6, 18),
        intArrayOf(6, 22),
        intArrayOf(6, 26),
        intArrayOf(6, 30),
        intArrayOf(6, 34),
        intArrayOf(6, 22, 38),
        intArrayOf(6, 24, 42),
        intArrayOf(6, 26, 46),
        intArrayOf(6, 28, 50)
    )

    /** Ceza puani agirliklari (Tablo 11). */
    private const val N1 = 3
    private const val N2 = 3
    private const val N3 = 40
    private const val N4 = 10

    /** Bayt modu gostergesi. */
    private const val MODE_BYTE = 0b0100

    // ---------------------------------------------------------------------
    // Genel arayuz
    // ---------------------------------------------------------------------

    /**
     * Byte modunda kodlar. Sigmazsa/hata olursa false doner, out bozulmaz.
     *
     * Sonuc yalnizca her sey bittikten sonra [out]'a yazilir; yarim kalan bir
     * matris cizim tarafinda bozuk karekod olarak gorunmesin.
     */
    fun encode(text: String, out: QrMatrix): Boolean {
        try {
            val data = toBytes(text)

            var version = 0
            for (v in 1..MAX_VERSION) {
                if (data.size <= byteCapacity(v)) {
                    version = v
                    break
                }
            }
            if (version == 0) return false

            val size = 17 + 4 * version
            val modules = BooleanArray(size * size)
            // Islev desenleri maskelenmez ve uzerlerine veri yazilmaz; hangi
            // modulun islev oldugunu ayri bir haritada tutmak, yerlestirme ve
            // maskeleme donguleri icinde tekrar tekrar koordinat sinamaktan
            // cok daha az hata uretiyor.
            val fixed = BooleanArray(size * size)

            drawFunctionPatterns(version, size, modules, fixed)
            drawCodewords(buildCodewords(data, version), size, modules, fixed)

            // 8 maskenin tamami denenir; XOR oldugu icin ayni maskeyi ikinci kez
            // uygulamak geri alir, matrisi kopyalamaya gerek yok.
            var bestMask = 0
            var bestPenalty = Int.MAX_VALUE
            for (mask in 0 until 8) {
                applyMask(mask, size, modules, fixed)
                drawFormatBits(mask, size, modules, fixed)
                val p = penalty(size, modules)
                if (p < bestPenalty) {
                    bestPenalty = p
                    bestMask = mask
                }
                applyMask(mask, size, modules, fixed)
            }
            applyMask(bestMask, size, modules, fixed)
            drawFormatBits(bestMask, size, modules, fixed)

            out.size = size
            out.bits = modules
            return true
        } catch (t: Throwable) {
            // Karekod ikincil bir gosterge; kodlayicidaki bir aksilik cagiran
            // sayfayi dusurmemeli.
            return false
        }
    }

    /** "WIFI:T:WPA;S:<ssid>;P:<parola>;;" — \ ; , : " karakterleri kacisli. */
    fun wifiPayload(ssid: String, passphrase: String): String {
        val sb = StringBuilder(24 + 2 * (ssid.length + passphrase.length))
        sb.append("WIFI:T:WPA;S:")
        appendEscaped(sb, ssid)
        sb.append(";P:")
        appendEscaped(sb, passphrase)
        sb.append(";;")
        return sb.toString()
    }

    /**
     * Secilen maskeyi test/dogrulama icin matristen geri okur; -1 = kodlanamadi.
     *
     * Format sozcugunde maske bitleri 10..12'dir (0..9 BCH kalanidir); yerlesim
     * kurali geregi bunlar (4,8), (3,8) ve (2,8) modulleri. 0x5412 ile
     * XOR'lanmis halde durduklari icin ayni maskeyle geri cevriliyorlar.
     */
    fun maskOf(text: String): Int {
        val m = QrMatrix()
        if (!encode(text, m)) return -1
        var bits = 0
        if (m.dark(4, 8)) bits = bits or 1
        if (m.dark(3, 8)) bits = bits or 2
        if (m.dark(2, 8)) bits = bits or 4
        return bits xor ((0x5412 ushr 10) and 7)
    }

    /** Secilen surumu test/dogrulama icin doner; -1 = kodlanamadi. */
    fun versionOf(text: String): Int {
        val m = QrMatrix()
        if (!encode(text, m)) return -1
        return (m.size - 17) / 4
    }

    // ---------------------------------------------------------------------
    // Veri hazirligi
    // ---------------------------------------------------------------------

    /**
     * Metni bayt dizisine cevirir.
     *
     * Bayt modunun varsayilan karakter kumesi ISO-8859-1; tarayicilar ECI
     * gostergesi olmadan metni oyle yorumlar. Kumeye sigmayan karakter varsa
     * UTF-8'e dusuyoruz — ECI eklemeden. Bu, yaygin tarayicilarin (ve segno'nun
     * varsayilaninin) davranisi: UTF-8 baytlari otomatik taniniyor.
     */
    private fun toBytes(text: String): ByteArray {
        var latin1 = true
        for (i in text.indices) {
            if (text[i].code > 0xFF) {
                latin1 = false
                break
            }
        }
        return if (latin1) {
            text.toByteArray(Charsets.ISO_8859_1)
        } else {
            text.toByteArray(Charsets.UTF_8)
        }
    }

    private fun appendEscaped(sb: StringBuilder, s: String) {
        for (i in s.indices) {
            val c = s[i]
            // Bu bes karakter WIFI yukunde alan ayraci/sinirlayici sayilir;
            // kacirilmazsa SSID icindeki tek bir ';' tarayicida parolayi boluyor.
            if (c == '\\' || c == ';' || c == ',' || c == ':' || c == '"') sb.append('\\')
            sb.append(c)
        }
    }

    private fun dataCodewords(version: Int): Int {
        val i = version - 1
        return TOTAL_CODEWORDS[i] - ECC_PER_BLOCK[i] * BLOCK_COUNT[i]
    }

    /** Karakter sayisi gostergesi bayt modunda surum 1-9'da 8, 10+'da 16 bit. */
    private fun countBits(version: Int): Int = if (version <= 9) 8 else 16

    private fun byteCapacity(version: Int): Int =
        (dataCodewords(version) * 8 - 4 - countBits(version)) / 8

    /** Bit akisini dogrudan kod sozcugu dizisine yazan kucuk yardimci. */
    private class BitBuf(size: Int) {
        val cw = IntArray(size)
        var bitLen = 0

        fun put(value: Int, count: Int) {
            for (i in count - 1 downTo 0) {
                if ((value ushr i) and 1 != 0) {
                    cw[bitLen ushr 3] = cw[bitLen ushr 3] or (0x80 ushr (bitLen and 7))
                }
                bitLen++
            }
        }
    }

    /**
     * Veri bitlerini olusturur, blok bolusumu + Reed-Solomon uygular ve nihai
     * serpistirilmis kod sozcugu dizisini doner.
     */
    private fun buildCodewords(data: ByteArray, version: Int): IntArray {
        val vi = version - 1
        val totalCw = TOTAL_CODEWORDS[vi]
        val eccLen = ECC_PER_BLOCK[vi]
        val blocks = BLOCK_COUNT[vi]
        val dataCw = totalCw - eccLen * blocks
        val capBits = dataCw * 8

        val b = BitBuf(dataCw)
        b.put(MODE_BYTE, 4)
        b.put(data.size, countBits(version))
        for (i in data.indices) b.put(data[i].toInt() and 0xFF, 8)

        // Sonlandirici 4 bittir ama kalan yer daha azsa kisalir.
        val term = if (capBits - b.bitLen < 4) capBits - b.bitLen else 4
        b.put(0, term)
        while (b.bitLen % 8 != 0) b.put(0, 1)

        // Kalan bosluk 11101100 / 00010001 ile donusumlu doldurulur (7.4.10).
        var padEc = true
        while (b.bitLen < capBits) {
            b.put(if (padEc) 0xEC else 0x11, 8)
            padEc = !padEc
        }

        // Blok uzunluklari: kalan sozcukler SON bloklara dagitilir. Bu kural
        // standardin blok tablosunu birebir uretiyor, ayri tablo tutmaya gerek yok.
        val shortLen = dataCw / blocks
        val longBlocks = dataCw % blocks
        val starts = IntArray(blocks + 1)
        for (i in 0 until blocks) {
            starts[i + 1] = starts[i] + shortLen + if (i >= blocks - longBlocks) 1 else 0
        }

        val gen = rsGenerator(eccLen)
        val ecc = arrayOfNulls<IntArray>(blocks)
        for (i in 0 until blocks) {
            ecc[i] = rsRemainder(b.cw, starts[i], starts[i + 1] - starts[i], gen)
        }

        // Serpistirme: her bloktan sirayla birer sozcuk. Amaci nokta hasarini
        // bloklara yaymak — tek bir leke tek blogu batirmasin.
        val out = IntArray(totalCw)
        var k = 0
        for (j in 0..shortLen) {
            for (i in 0 until blocks) {
                if (j < starts[i + 1] - starts[i]) out[k++] = b.cw[starts[i] + j]
            }
        }
        for (j in 0 until eccLen) {
            for (i in 0 until blocks) out[k++] = ecc[i]!![j]
        }
        return out
    }

    // ---------------------------------------------------------------------
    // GF(256) ve Reed-Solomon
    // ---------------------------------------------------------------------

    // QR'nin kullandigi ilkel polinom x^8+x^4+x^3+x^2+1 = 0x11D. Log/antilog
    // tablosu carpmayi tek toplama + dizi okumasina indiriyor.
    private val GF_EXP = IntArray(512)
    private val GF_LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            GF_EXP[i] = x
            GF_LOG[x] = i
            x = x shl 1
            if (x >= 256) x = x xor 0x11D
        }
        // Ust yari, indeks toplaminda mod almadan okuyabilmek icin tekrar.
        for (i in 255 until 512) GF_EXP[i] = GF_EXP[i - 255]
    }

    private fun gfMul(a: Int, b: Int): Int =
        if (a == 0 || b == 0) 0 else GF_EXP[GF_LOG[a] + GF_LOG[b]]

    /**
     * Derecesi [degree] olan uretec polinomu: (x - a^0)(x - a^1)...
     *
     * GF(2^8)'de cikarma toplamaya (XOR) esit oldugu icin isaret onemsiz.
     * Katsayilar en yuksek dereceden baslar, poly[0] her zaman 1.
     */
    private fun rsGenerator(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor gfMul(poly[j], GF_EXP[i])
            }
            poly = next
        }
        return poly
    }

    /** Veri polinomunun uretece bolumunden kalan = hata duzeltme sozcukleri. */
    private fun rsRemainder(data: IntArray, from: Int, len: Int, gen: IntArray): IntArray {
        val deg = gen.size - 1
        val rem = IntArray(deg)
        for (i in 0 until len) {
            val factor = data[from + i] xor rem[0]
            for (j in 0 until deg - 1) rem[j] = rem[j + 1]
            rem[deg - 1] = 0
            for (j in 0 until deg) rem[j] = rem[j] xor gfMul(gen[j + 1], factor)
        }
        return rem
    }

    // ---------------------------------------------------------------------
    // Islev desenleri
    // ---------------------------------------------------------------------

    private fun setFn(m: BooleanArray, f: BooleanArray, size: Int, x: Int, y: Int, dark: Boolean) {
        if (x < 0 || y < 0 || x >= size || y >= size) return
        m[y * size + x] = dark
        f[y * size + x] = true
    }

    private fun drawFunctionPatterns(version: Int, size: Int, m: BooleanArray, f: BooleanArray) {
        // Zamanlama desenleri: 6. satir ve 6. sutun boyunca degisimli moduller;
        // tarayici modul boyunu bunlardan olcer.
        for (i in 0 until size) {
            setFn(m, f, size, 6, i, i % 2 == 0)
            setFn(m, f, size, i, 6, i % 2 == 0)
        }

        // Uc kosede konum bulma deseni. Dorduncu kose alignment ile isaretlenir;
        // eksik kose tarayicinin yonelimi anlamasini saglar.
        drawFinder(m, f, size, 3, 3)
        drawFinder(m, f, size, size - 4, 3)
        drawFinder(m, f, size, 3, size - 4)

        val pos = ALIGN_POS[version - 1]
        val n = pos.size
        for (i in 0 until n) {
            for (j in 0 until n) {
                // Uc kose finder desenine denk gelir, oralara hizalama konmaz.
                val corner = (i == 0 && j == 0) ||
                    (i == 0 && j == n - 1) ||
                    (i == n - 1 && j == 0)
                if (!corner) drawAlignment(m, f, size, pos[i], pos[j])
            }
        }

        // Sahte format bitleri: gercek deger maske secildikten sonra yazilacak,
        // ama konumlarin simdiden "islev" isaretlenmesi gerek — yoksa veri
        // yerlestirme dongusu buralari da doldururdu.
        drawFormatBits(0, size, m, f)
        drawVersionBits(version, size, m, f)
    }

    private fun drawFinder(m: BooleanArray, f: BooleanArray, size: Int, cx: Int, cy: Int) {
        // 7x7 desen + 1 modul ayirici: merkeze Chebyshev uzakligi 2 ve 4 olan
        // halkalar acik, digerleri koyu.
        for (dy in -4..4) {
            for (dx in -4..4) {
                val d = if (Math.abs(dx) > Math.abs(dy)) Math.abs(dx) else Math.abs(dy)
                setFn(m, f, size, cx + dx, cy + dy, d != 2 && d != 4)
            }
        }
    }

    private fun drawAlignment(m: BooleanArray, f: BooleanArray, size: Int, cx: Int, cy: Int) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                val d = if (Math.abs(dx) > Math.abs(dy)) Math.abs(dx) else Math.abs(dy)
                setFn(m, f, size, cx + dx, cy + dy, d != 1)
            }
        }
    }

    /**
     * 15 bitlik format bilgisi: 2 bit seviye + 3 bit maske, BCH(15,5) ile
     * genisletilir ve 0x5412 ile XOR'lanir.
     *
     * XOR maskesi olmasa sifir format bilgisi tamamen acik bir bolge uretirdi;
     * standart bunu onlemek icin sabit bir desen ekliyor.
     */
    private fun formatBits(mask: Int): Int {
        // Seviye M'nin gostergesi 0b00.
        val data = (0b00 shl 3) or mask
        var rem = data
        for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        return ((data shl 10) or rem) xor 0x5412
    }

    private fun bitOf(value: Int, i: Int): Boolean = ((value ushr i) and 1) != 0

    private fun drawFormatBits(mask: Int, size: Int, m: BooleanArray, f: BooleanArray) {
        val bits = formatBits(mask)

        // Birinci kopya: sol ust finder cevresinde L seklinde.
        for (i in 0 until 6) setFn(m, f, size, 8, i, bitOf(bits, i))
        setFn(m, f, size, 8, 7, bitOf(bits, 6))
        setFn(m, f, size, 8, 8, bitOf(bits, 7))
        setFn(m, f, size, 7, 8, bitOf(bits, 8))
        for (i in 9 until 15) setFn(m, f, size, 14 - i, 8, bitOf(bits, i))

        // Ikinci kopya: sag ust ve sol alt. Kopya olmasi, bir kosesi hasar
        // gormus etikette bile format bilgisinin okunabilmesi icin.
        for (i in 0 until 8) setFn(m, f, size, size - 1 - i, 8, bitOf(bits, i))
        for (i in 8 until 15) setFn(m, f, size, 8, size - 15 + i, bitOf(bits, i))

        // Her zaman koyu olan modul (7.9.1).
        setFn(m, f, size, 8, size - 8, true)
    }

    /**
     * Surum 7 ve uzerinde 18 bitlik surum bilgisi iki blok halinde yazilir.
     *
     * BCH(18,6): 6 bit surum + 12 bit kalan, uretec 0x1F25.
     */
    private fun drawVersionBits(version: Int, size: Int, m: BooleanArray, f: BooleanArray) {
        if (version < 7) return
        var rem = version
        for (i in 0 until 12) rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
        val bits = (version shl 12) or rem

        for (i in 0 until 18) {
            val bit = bitOf(bits, i)
            val a = size - 11 + i % 3
            val b = i / 3
            setFn(m, f, size, a, b, bit)
            setFn(m, f, size, b, a, bit)
        }
    }

    // ---------------------------------------------------------------------
    // Veri yerlestirme ve maskeleme
    // ---------------------------------------------------------------------

    /**
     * Kod sozcuklerini sag alttan baslayarak iki modul genisliginde zikzak
     * seritler halinde yerlestirir.
     *
     * 6. sutun zamanlama deseni oldugu icin serit sayaci oradan bir atlar;
     * atlanmazsa tum yerlesim bir sutun kayar.
     */
    private fun drawCodewords(cw: IntArray, size: Int, m: BooleanArray, f: BooleanArray) {
        var i = 0
        val totalBits = cw.size * 8
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) {
                for (j in 0 until 2) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!f[y * size + x] && i < totalBits) {
                        m[y * size + x] = bitOf(cw[i ushr 3], 7 - (i and 7))
                        i++
                    }
                }
            }
            right -= 2
        }
    }

    /**
     * Maskeyi islev olmayan modullere XOR'lar.
     *
     * Ayni maskeyi ikinci kez uygulamak islemi geri alir; deneme dongusu bunu
     * kullanarak matris kopyalamadan calisir.
     */
    private fun applyMask(mask: Int, size: Int, m: BooleanArray, f: BooleanArray) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                val idx = y * size + x
                if (f[idx]) continue
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (invert) m[idx] = !m[idx]
            }
        }
    }

    // ---------------------------------------------------------------------
    // Ceza puani
    // ---------------------------------------------------------------------

    /**
     * Standardin dort ceza kuralini toplar; en dusuk puanli maske secilir.
     *
     * Amac tarayicinin kafasini karistiran desenleri elemek: uzun tek renkli
     * kosular (N1), 2x2 tek renk bloklar (N2), finder desenine benzeyen
     * 1:1:3:1:1 dizilimler (N3) ve koyu/acik dengesizligi (N4).
     */
    private fun penalty(size: Int, m: BooleanArray): Int {
        var result = 0
        val hist = IntArray(7)

        // Satirlar: N1 ve N3
        for (y in 0 until size) {
            java.util.Arrays.fill(hist, 0)
            var runColor = false
            var runLen = 0
            for (x in 0 until size) {
                if (m[y * size + x] == runColor) {
                    runLen++
                    if (runLen == 5) result += N1 else if (runLen > 5) result++
                } else {
                    pushRun(hist, runLen, size)
                    if (!runColor) result += finderLike(hist) * N3
                    runColor = m[y * size + x]
                    runLen = 1
                }
            }
            result += finishRuns(hist, runColor, runLen, size) * N3
        }

        // Sutunlar: ayni iki kural, dikey yonde
        for (x in 0 until size) {
            java.util.Arrays.fill(hist, 0)
            var runColor = false
            var runLen = 0
            for (y in 0 until size) {
                if (m[y * size + x] == runColor) {
                    runLen++
                    if (runLen == 5) result += N1 else if (runLen > 5) result++
                } else {
                    pushRun(hist, runLen, size)
                    if (!runColor) result += finderLike(hist) * N3
                    runColor = m[y * size + x]
                    runLen = 1
                }
            }
            result += finishRuns(hist, runColor, runLen, size) * N3
        }

        // N2: 2x2 tek renk bloklar
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val c = m[y * size + x]
                if (c == m[y * size + x + 1] &&
                    c == m[(y + 1) * size + x] &&
                    c == m[(y + 1) * size + x + 1]
                ) result += N2
            }
        }

        // N4: koyu modul oraninin %50'den sapmasi, her %5'lik dilim icin ceza
        var dark = 0
        for (i in m.indices) if (m[i]) dark++
        val total = size * size
        val k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1
        result += k * N4

        return result
    }

    /**
     * Kosu uzunlugunu gecmis penceresine iter (hist[0] = en yeni).
     *
     * Ilk kosuda pencereye sessiz bolge genisligi eklenir: standart N3'u
     * "onunde veya arkasinda 4 modul acik alan" diye tanimlar ve quiet zone
     * bu alanin parcasidir. Eklenmezse kenardaki gercek finder desenleri
     * cezalandirilmiyor, maske secimi kayiyordu.
     */
    private fun pushRun(hist: IntArray, runLen: Int, size: Int) {
        var len = runLen
        if (hist[0] == 0) len += size
        for (i in hist.size - 1 downTo 1) hist[i] = hist[i - 1]
        hist[0] = len
    }

    /** Pencerede 1:1:3:1:1 + 4 modul acik alan kalibi kac kez gorunuyor. */
    private fun finderLike(hist: IntArray): Int {
        val n = hist[1]
        val core = n > 0 && hist[2] == n && hist[3] == n * 3 && hist[4] == n && hist[5] == n
        var c = 0
        if (core && hist[0] >= n * 4 && hist[6] >= n) c++
        if (core && hist[6] >= n * 4 && hist[0] >= n) c++
        return c
    }

    /** Satir/sutun sonunda kalan kosuyu ve sondaki sessiz bolgeyi isler. */
    private fun finishRuns(hist: IntArray, runColor: Boolean, runLen: Int, size: Int): Int {
        var len = runLen
        if (runColor) {
            pushRun(hist, len, size)
            len = 0
        }
        len += size
        pushRun(hist, len, size)
        return finderLike(hist)
    }
}
