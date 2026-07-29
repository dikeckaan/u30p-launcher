package com.kaandikec.u30plauncher.core

/** SoftAP yapilandirmasi. */
class SoftApInfo {
    var ssid: String = ""
    var passphrase: String = ""
    var maxClients: Int = Snapshot.UNKNOWN
    var band: Int = Snapshot.UNKNOWN

    val isEmpty: Boolean get() = ssid.isEmpty()
}

/**
 * `WifiConfigStoreSoftAp.xml` icinden SSID ve parolayi okur.
 *
 * Bu dosya yalnizca root ile okunabilir; framework API'si parolayi
 * `MANAGE_WIFI_*` imza izni olmadan vermez.
 *
 * Beklenen bicim:
 * `<string name="WifiSsid">&quot;<ssid>&quot;</string>`
 * `<string name="Passphrase"><parola></string>`
 */
object SoftApParser {
    fun parse(xml: String, out: SoftApInfo): Boolean {
        out.ssid = unquote(stringField(xml, "WifiSsid"))
        out.passphrase = stringField(xml, "Passphrase")
        out.maxClients = intField(xml, "MaxNumberOfClients")
        out.band = intField(xml, "Band")
        return !out.isEmpty
    }

    private fun stringField(xml: String, name: String): String {
        val key = "<string name=\"$name\">"
        val at = xml.indexOf(key)
        if (at < 0) return ""
        val start = at + key.length
        val end = xml.indexOf("</string>", start)
        if (end < 0) return ""
        return decode(xml.substring(start, end))
    }

    private fun intField(xml: String, name: String): Int {
        val key = "<int name=\"$name\" value=\""
        val at = xml.indexOf(key)
        if (at < 0) return Snapshot.UNKNOWN
        val start = at + key.length
        val end = xml.indexOf('"', start)
        if (end < 0) return Snapshot.UNKNOWN
        return xml.substring(start, end).toIntOrNull() ?: Snapshot.UNKNOWN
    }

    /** SSID XML'de tirnak icinde saklanir. */
    private fun unquote(s: String): String =
        if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) s.substring(1, s.length - 1)
        else s

    private fun decode(s: String): String = s
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}

/** Tek bir LAN istemcisi. */
class Client(val ip: String, val mac: String)

/**
 * `/proc/net/arp` ciktisindan belirtilen arayuzdeki istemcileri okur.
 *
 * Flags sutunu 0x2 = tamamlanmis girdi; 0x0 olanlar eskimis kayitlardir ve
 * bagli sayilmaz.
 */
object ArpParser {
    fun parse(text: String, iface: String, out: MutableList<Client>) {
        out.clear()
        var first = true
        for (line in text.lineSequence()) {
            if (first) {
                first = false
                continue  // baslik satiri
            }
            if (line.isBlank()) continue
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 6) continue
            if (parts[5] != iface) continue
            if (parts[2] != "0x2") continue
            out.add(Client(parts[0], parts[3]))
        }
    }
}
