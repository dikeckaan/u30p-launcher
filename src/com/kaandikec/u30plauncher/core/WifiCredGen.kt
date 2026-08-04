package com.kaandikec.u30plauncher.core

import java.security.SecureRandom
import java.util.Random

/** Uretilen SoftAP kimligi. */
class WifiCred(val ssid: String, val passphrase: String)

/**
 * Rastgele ama okunabilir SoftAP adi ve parolasi uretir.
 *
 * Alfabeden birbirine benzeyen isaretler cikarildi (0/O, 1/I, 2/Z, 5/S, 8/B):
 * parola cogu zaman 240 px'lik ekrandan bakilarak baska bir cihaza ELLE
 * yaziliyor ve "0 mi O mu" sorusu tek basina birkac basarisiz denemeye mal
 * oluyor. Kucuk harf hic kullanilmiyor; telefon klavyesinde buyuk/kucuk
 * gecisi ayni hatanin baska bir kaynagi.
 *
 * Uretim [SecureRandom] ile yapilir. `Random` alan varyantlar YALNIZCA test
 * icin: tohum verilebildigi icin uretim tekrarlanabilir sinanabiliyor.
 */
object WifiCredGen {
    /** Karisabilecek isaretler cikarilmis 26 karakterlik alfabe. */
    const val ALPHABET = "ACDEFGHJKLMNPQRTUVWXY34679"

    /** Ad, listede hangi cihaz oldugu tek bakista anlasilsin diye sabit onekli. */
    const val SSID_PREFIX = "U30P-"
    private const val SSID_RANDOM_LEN = 4

    /**
     * 14 karakter x 26 isaret ~ 65 bit. 12 istenen alt sinirdi; iki fazlasi
     * bedava, cunku parola zaten karekodla okutuluyor.
     */
    const val PASS_LEN = 14

    private val secure = SecureRandom()

    fun generate(): WifiCred = generate(secure)

    fun generate(rnd: Random): WifiCred = WifiCred(ssid(rnd), passphrase(rnd))

    fun ssid(rnd: Random): String {
        val sb = StringBuilder(SSID_PREFIX.length + SSID_RANDOM_LEN)
        sb.append(SSID_PREFIX)
        appendRandom(sb, rnd, SSID_RANDOM_LEN)
        return sb.toString()
    }

    fun passphrase(rnd: Random): String {
        val sb = StringBuilder(PASS_LEN)
        appendRandom(sb, rnd, PASS_LEN)
        return sb.toString()
    }

    /**
     * Cerceve SSID'yi 1..32 BAYT olarak dogruluyor, karakter olarak degil.
     *
     * Kendi urettigimiz degerler zaten sinirin cok altinda; bu sinama yazma
     * yolunun onunde duruyor ki ileride disaridan gelen bir deger sessizce
     * reddedilen bir yapilandirmaya donusmesin.
     */
    fun isValidSsid(s: String): Boolean {
        val n = s.toByteArray(Charsets.UTF_8).size
        return n in 1..32
    }

    /** WPA2/WPA3-gecis parolasi 8..63 ASCII bayt. */
    fun isValidPassphrase(s: String): Boolean {
        val n = s.toByteArray(Charsets.UTF_8).size
        return n in 8..63
    }

    private fun appendRandom(sb: StringBuilder, rnd: Random, n: Int) {
        // Random.nextInt(bound) reddetme ornekleme yapar; % ile alinan artik
        // 26 alfabede son karakterleri hafifce sik cikarirdi.
        for (i in 0 until n) sb.append(ALPHABET[rnd.nextInt(ALPHABET.length)])
    }
}
