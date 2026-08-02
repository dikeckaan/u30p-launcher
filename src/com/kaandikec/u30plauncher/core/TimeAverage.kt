package com.kaandikec.u30plauncher.core

/**
 * Zaman sabitli ustel ortalama.
 *
 * Pil akimi anlik olarak cok dalgalaniyor (modem verici patlamalari, WiFi
 * istemcileri); ham degerden hesaplanan "kalan sure" surekli zipliyordu.
 * Ornek sayisina degil GECEN SUREYE bagli agirlik kullaniyoruz, boylece
 * yenileme araligi degisse de ortalama ayni zaman penceresini temsil eder.
 *
 * Varsayilan sabit 5 dakika. Ustel ortalamada deger hedefe `1 - e^(-t/tau)`
 * oraninda yaklasir: 5 dakikada %63, 10 dakikada %86. Tek seferlik bir
 * sicramanin etkisi ise `dt / tau` kadardir — 1 saniyelik bir ornek icin
 * binde uc.
 *
 * Tek bir Double tutar; gecmis ornek dizisi yok.
 */
class TimeAverage(private val tauMs: Long = 300_000L) {
    private var value = 0.0
    private var seeded = false

    val isReady: Boolean get() = seeded

    fun add(sample: Int, dtMs: Long) {
        if (!seeded) {
            value = sample.toDouble()
            seeded = true
            return
        }
        if (dtMs <= 0L) return
        // Agirlik 1'i asmamali: cok uzun bir bosluktan sonra ornek tamamen
        // yeni degeri temsil eder (eski ortalama artik gecersizdir).
        var w = dtMs.toDouble() / tauMs.toDouble()
        if (w > 1.0) w = 1.0
        value += (sample.toDouble() - value) * w
    }

    fun value(): Int = if (!seeded) 0 else Math.round(value).toInt()

    fun reset() {
        value = 0.0
        seeded = false
    }
}
