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
 * Cikti tek satir ve hepsi "U30P_SOFTAP_" onekli: _OK / _INVALID / _FAIL /
 * _ERR:<mesaj>. Onek sart: cagiran RootShell stderr'i stdout'a katiyor ve
 * duz "OK" arandiginda icinde OK gecen herhangi bir kabuk metni basari
 * sayilirdi.
 *
 * ONEMLI: Yazma KALICIDIR ama CALISAN AP'ye uygulanmaz. SoftApManager
 * SSID/parola degisimi icin "requires restart" deyip yok sayiyor; uygulanmasi
 * icin hotspot yeniden baslatilmali. O karar kullanicinin.
 */
object SoftApTool {
    private const val PKG = "com.kaandikec.u30plauncher"

    /**
     * Basari isareti. Duz "OK" DEGIL: cagiran RootShell stderr'i stdout'a
     * katiyor ve icinde "OK" gecen herhangi bir kabuk/yigin metni basari
     * sayilirdi. WifiPage bu dizeyi ariyor — ikisi ayrisirsa yazma BASARILI
     * olur ama arayuz "basarisiz" der; gercekten oldu ve kullanici
     * gormedigi bir paroloya gecmis olurdu.
     */
    private const val OK = "U30P_SOFTAP_OK"

    @JvmStatic
    fun main(args: Array<String>) {
        var code = 1
        try {
            code = run(args)
        } catch (t: Throwable) {
            println("U30P_SOFTAP_ERR:" + t.javaClass.simpleName + ":" + (t.message ?: ""))
        }
        // app_process main() bitince kendiliginden CIKMAZ: binder ve Looper is
        // parcaciklari ayakta kalir, cagiran `su` kabugu okumada sonsuza kadar
        // bloke olurdu. RootShell tek bir kabuk paylastigi icin bu tum root
        // okumalarini (istemci sayisi, pil, netpolicy) kilitlerdi.
        System.exit(code)
    }

    private fun run(args: Array<String>): Int {
        if (args.size < 2) {
            println("U30P_SOFTAP_ERR:args")
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
            println("U30P_SOFTAP_INVALID")
            return 3
        }

        val ok = itf.getDeclaredMethod("setSoftApConfiguration", sacClass, String::class.java)
            .invoke(wifi, cfg, PKG) as Boolean
        if (!ok) {
            println("U30P_SOFTAP_FAIL")
            return 4
        }

        // Yazma kalici ama CALISAN AP'ye uygulanmaz: SoftApManager SSID/parola
        // degisimi icin "requires restart" deyip yok sayiyor. ZTE'nin kendi web
        // arayuzu de tam olarak burada hotspot'u devir daim ettiriyor; ayni
        // diziyi izliyoruz ki kullanici yeniden baslatmak zorunda kalmasin.
        if (args.size > 2 && args[2] == "restart") {
            if (restartTethering()) println("U30P_SOFTAP_RESTARTED")
            else println("U30P_SOFTAP_NORESTART")
        }
        println(OK)
        return 0
    }


    /**
     * ZTE'nin sirasi: stopTethering(0) -> AP kapanana kadar bekle ->
     * startTethering(0, false, cb) -> AP acilana kadar bekle. Bekleme
     * yoklamayla yapiliyor, callback beklenmiyor (ZTE de oyle yapiyor).
     *
     * Baslatma basarisiz olursa hotspot KAPALI kalir; cagiran bunu
     * "U30P_SOFTAP_NORESTART" ile ogrenir ve kullaniciya yeniden baslatmasini
     * soyleyebilir.
     */
    private fun restartTethering(): Boolean = try {
        val ctx = systemContext()
        val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
        val wm = ctx.getSystemService(android.content.Context.WIFI_SERVICE)
        val wmCls = wm.javaClass

        cm.javaClass.getMethod("stopTethering", Int::class.javaPrimitiveType)
            .also { it.isAccessible = true }
            .invoke(cm, 0)
        waitState(wm, wmCls, 11)

        // OnStartTetheringCallback soyut bir SINIF, Proxy ile uretilemez.
        // TetheringManager'in karsiligi ise arayuz; once onu deneriz.
        var started = startViaTetheringManager(ctx)
        if (!started) started = startViaConnectivity(cm)
        started && waitState(wm, wmCls, 13)
    } catch (t: Throwable) {
        println("U30P_SOFTAP_ERR:restart:" + t.javaClass.simpleName)
        false
    }

    private fun startViaTetheringManager(ctx: android.content.Context): Boolean = try {
        val tm = ctx.getSystemService("tethering")
        val cbCls = Class.forName("android.net.TetheringManager\$StartTetheringCallback")
        val cb = java.lang.reflect.Proxy.newProxyInstance(
            cbCls.classLoader, arrayOf(cbCls)
        ) { _, _, _ -> null }
        val exec = java.util.concurrent.Executor { it.run() }
        tm.javaClass.getMethod(
            "startTethering", Int::class.javaPrimitiveType,
            java.util.concurrent.Executor::class.java, cbCls
        ).also { it.isAccessible = true }.invoke(tm, 0, exec, cb)
        true
    } catch (_: Throwable) {
        false
    }

    private fun startViaConnectivity(cm: Any): Boolean = try {
        val cbCls = Class.forName("android.net.ConnectivityManager\$OnStartTetheringCallback")
        cm.javaClass.getMethod(
            "startTethering", Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType, cbCls
        ).also { it.isAccessible = true }.invoke(cm, 0, false, null)
        true
    } catch (_: Throwable) {
        false
    }

    /** 40 x 500 ms = 20 sn; ZTE ile ayni pencere. */
    private fun waitState(wm: Any, cls: Class<*>, want: Int): Boolean {
        val m = try {
            cls.getMethod("getWifiApState").also { it.isAccessible = true }
        } catch (_: Throwable) {
            return false
        }
        for (i in 0 until 40) {
            if ((m.invoke(wm) as Int) == want) return true
            Thread.sleep(500)
        }
        return false
    }

    private fun systemContext(): android.content.Context {
        val at = Class.forName("android.app.ActivityThread")
        val thread = at.getMethod("systemMain").also { it.isAccessible = true }.invoke(null)
        return at.getMethod("getSystemContext").also { it.isAccessible = true }
            .invoke(thread) as android.content.Context
    }

    private fun decode(b64: String): String =
        String(android.util.Base64.decode(b64, android.util.Base64.NO_WRAP), Charsets.UTF_8)
}
