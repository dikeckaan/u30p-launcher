package com.kaandikec.u30plauncher.core

/**
 * Cihazin FABRIKA WiFi kimligini yeniden uretir.
 *
 * Kullanici rastgele bir ad/parola uygulayip vazgecerse geri donecek bir yer
 * olmali; cihazin uzerindeki etikette yazan degerler bunlar.
 *
 * Iki kaynak da vendor bolumunde duruyor ve fabrika kurulumundan beri
 * degismiyor:
 *   /mnt/vendor/wifimac.txt              -> "xx:xx:xx:9b:60:c4"
 *   /mnt/vendor/defaulthotspotkey_1.txt  -> 10 karakterlik parola
 *
 * SSID kurali cihazda DOGRULANDI: MAC'in son 6 onalti hanesi buyuk harfe
 * cevrilip "ZTE_" onune ekleniyor; uretilen deger cihazdaki SSID ile birebir
 * ayni cikti.
 */
object WifiFactory {
    private const val PREFIX = "ZTE_"

    /** MAC metninden fabrika SSID'si; cikarilamazsa bos. */
    fun ssidFromMac(macText: String?): String {
        if (macText == null) return ""
        val sb = StringBuilder(12)
        for (i in macText.indices) {
            val c = macText[i]
            if (c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F') sb.append(c)
        }
        if (sb.length < 6) return ""
        val tail = sb.substring(sb.length - 6)
        val out = StringBuilder(PREFIX.length + 6)
        out.append(PREFIX)
        for (i in tail.indices) {
            val c = tail[i]
            out.append(if (c in 'a'..'f') (c - 32) else c)
        }
        return out.toString()
    }

    /**
     * Parola dosyasi tek satir; sondaki satir sonu ve bosluklar atilir.
     *
     * Bos donerse cagiran sifirlamayi SUNMAMALI: bos parola AP'yi acik aga
     * cevirirdi.
     */
    fun keyFromFile(text: String?): String {
        if (text == null) return ""
        var start = 0
        var end = text.length
        while (start < end && text[start] <= ' ') start++
        while (end > start && text[end - 1] <= ' ') end--
        if (end - start < 8) return ""
        return text.substring(start, end)
    }
}
