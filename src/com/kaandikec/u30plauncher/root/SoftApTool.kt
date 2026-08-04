package com.kaandikec.u30plauncher.root

import android.os.IBinder

/**
 * SoftAP kimligini degistiren kucuk yardimci — UYGULAMA SURECINDE DEGIL,
 * `app_process` ile ROOT altinda calisir.
 *
 * Neden boyle: `WifiManager.setSoftApConfiguration` NETWORK_SETTINGS imza izni
 * istiyor ve bu uygulama platform imzali degil. Izin denetimi CAGIRAN UID
 * uzerinden yapiliyor; cihazda uid 0 icin NETWORK_SETTINGS ve
 * OVERRIDE_WIFI_CONFIG GRANTED olculdu, dolayisiyla root altindan gecer.
 * `cmd wifi set-soft-ap-configuration` alt komutu bu Wi-Fi modulu surumunde
 * yok; yapilandirma XML'ini elle duzenlemek ise cerceve bellekteki kopyayi
 * geri yazdiginda sessizce kayboluyor.
 *
 * Cagri bicimi:
 *   CLASSPATH=<apk> app_process / com.kaandikec.u30plauncher.root.SoftApTool \
 *       <base64 ssid> <base64 parola>
 *
 * Degerler base64 ile geciyor: kabuk satirina giren bir parolada $ ` " \ veya
 * bosluk kacisi unutuldugunda parola sessizce baska bir seye donusurdu.
 *
 * Cikti tek satir: OK / INVALID / FAIL / ERR:<mesaj>. Cagiran yalnizca "OK"
 * gordugunde basari sayar — "OK" gecen bir yigin izini basariymis gibi
 * okumamak icin isaret bilerek kisa ve tam eslesmeli tutuldu.
 *
 * ONEMLI: Yazma KALICIDIR ama CALISAN AP'ye uygulanmaz. SoftApManager
 * SSID/parola degisimi icin "requires restart" deyip yok sayiyor; uygulanmasi
 * icin hotspot yeniden baslatilmali. O karar kullanicinin.
 */
object SoftApTool {
    private const val PKG = "com.kaandikec.u30plauncher"

    @JvmStatic
    fun main(args: Array<String>) {
        var code = 1
        try {
            code = run(args)
        } catch (t: Throwable) {
            println("ERR:" + t.javaClass.simpleName + ":" + (t.message ?: ""))
        }
        // app_process main() bitince kendiliginden CIKMAZ: binder ve Looper is
        // parcaciklari ayakta kalir, cagiran `su` kabugu okumada sonsuza kadar
        // bloke olurdu. RootShell tek bir kabuk paylastigi icin bu tum root
        // okumalarini (istemci sayisi, pil, netpolicy) kilitlerdi.
        System.exit(code)
    }

    private fun run(args: Array<String>): Int {
        if (args.size < 2) {
            println("ERR:args")
            return 2
        }
        val ssid = decode(args[0])
        val pass = decode(args[1])

        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, "wifi") as IBinder

        val itf = Class.forName("android.net.wifi.IWifiManager")
        val stub = Class.forName("android.net.wifi.IWifiManager\$Stub")
        val wifi = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)

        val sacClass = Class.forName("android.net.wifi.SoftApConfiguration")
        val current = itf.getDeclaredMethod("getSoftApConfiguration").invoke(wifi)

        // Guvenlik turu KORUNUR. Sabit bir tur yazmak, cihaz WPA3 gecis
        // kipindeyken onu WPA2'ye dusurur ve kullanicinin haberi olmaz.
        val security = sacClass.getMethod("getSecurityType").invoke(current) as Int

        val builderClass = Class.forName("android.net.wifi.SoftApConfiguration\$Builder")
        val builder = builderClass.getConstructor(sacClass).newInstance(current)
        builderClass.getMethod("setSsid", String::class.java).invoke(builder, ssid)
        builderClass.getMethod("setPassphrase", String::class.java, Int::class.javaPrimitiveType)
            .invoke(builder, if (pass.isEmpty()) null else pass, if (pass.isEmpty()) 0 else security)
        val cfg = builderClass.getMethod("build").invoke(builder)

        // Once dogrula: gecersiz yapilandirma hicbir sey degistirmeden reddedilsin.
        val valid = try {
            itf.getDeclaredMethod("validateSoftApConfiguration", sacClass).invoke(wifi, cfg) as Boolean
        } catch (_: NoSuchMethodException) {
            true
        }
        if (!valid) {
            println("INVALID")
            return 3
        }

        val ok = itf.getDeclaredMethod("setSoftApConfiguration", sacClass, String::class.java)
            .invoke(wifi, cfg, PKG) as Boolean
        println(if (ok) "OK" else "FAIL")
        return if (ok) 0 else 4
    }

    private fun decode(b64: String): String =
        String(android.util.Base64.decode(b64, android.util.Base64.NO_WRAP), Charsets.UTF_8)
}
