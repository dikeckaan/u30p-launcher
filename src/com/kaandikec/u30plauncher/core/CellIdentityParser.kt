package com.kaandikec.u30plauncher.core

/** Hucre kimligi alanlari; yeniden kullanilir. */
class CellIdentity {
    var pci: Int = Snapshot.UNKNOWN
    var earfcn: Int = Snapshot.UNKNOWN
    var tac: Int = Snapshot.UNKNOWN
    var ci: Int = Snapshot.UNKNOWN
    var band: Int = Snapshot.UNKNOWN
    var bandwidthKhz: Int = Snapshot.UNKNOWN

    fun clear() {
        pci = Snapshot.UNKNOWN
        earfcn = Snapshot.UNKNOWN
        tac = Snapshot.UNKNOWN
        ci = Snapshot.UNKNOWN
        band = Snapshot.UNKNOWN
        bandwidthKhz = Snapshot.UNKNOWN
    }

    val isEmpty: Boolean
        get() = pci == Snapshot.UNKNOWN && earfcn == Snapshot.UNKNOWN &&
            tac == Snapshot.UNKNOWN && ci == Snapshot.UNKNOWN
}

/**
 * `dumpsys telephony.registry` ciktisindan hucre kimligini okur.
 *
 * Neden gerekli: cihazda konum servisi kapali oldugunda Android, izinler verilmis
 * olsa bile `ServiceState` icindeki hucre kimligini maskeler. Root ile dumpsys
 * okumak, kullanicinin sistem ayarini degistirmeden ayni veriyi verir.
 *
 * Beklenen bicim:
 * `CellIdentityLte:{ mCi=88103723 mPci=344 mTac=21054 mEarfcn=1279 mBands=[3] mBandwidth=15000 ...}`
 */
object CellIdentityParser {
    private const val LTE = "CellIdentityLte:{"
    private const val NR = "CellIdentityNr:{"

    fun parse(text: String, out: CellIdentity): Boolean {
        out.clear()
        var start = text.indexOf(LTE)
        var len = LTE.length
        if (start < 0) {
            start = text.indexOf(NR)
            len = NR.length
        }
        if (start < 0) return false

        val end = text.indexOf('}', start)
        val body = if (end < 0) text.substring(start + len) else text.substring(start + len, end)

        out.ci = intField(body, "mCi=")
        if (out.ci == Snapshot.UNKNOWN) out.ci = intField(body, "mNci=")
        out.pci = intField(body, "mPci=")
        out.tac = intField(body, "mTac=")
        out.earfcn = intField(body, "mEarfcn=")
        if (out.earfcn == Snapshot.UNKNOWN) out.earfcn = intField(body, "mNrarfcn=")
        out.bandwidthKhz = intField(body, "mBandwidth=")
        out.band = firstBand(body)

        return !out.isEmpty
    }

    /** `key` sonrasi gelen tam sayiyi okur; yoksa UNKNOWN. */
    private fun intField(body: String, key: String): Int {
        val at = body.indexOf(key)
        if (at < 0) return Snapshot.UNKNOWN
        var p = at + key.length
        var neg = false
        if (p < body.length && body[p] == '-') {
            neg = true
            p++
        }
        var v = 0L
        var digits = 0
        while (p < body.length) {
            val ch = body[p]
            if (ch < '0' || ch > '9') break
            v = v * 10 + (ch - '0')
            // 32 bit tasmasini engelle (mNci 36 bit olabilir)
            if (v > Int.MAX_VALUE) return Snapshot.UNKNOWN
            digits++
            p++
        }
        if (digits == 0) return Snapshot.UNKNOWN
        val r = v.toInt()
        return if (neg) -r else r
    }

    /** `mBands=[3]` icindeki ilk degeri okur. */
    private fun firstBand(body: String): Int {
        val at = body.indexOf("mBands=[")
        if (at < 0) return Snapshot.UNKNOWN
        var p = at + "mBands=[".length
        var v = 0
        var digits = 0
        while (p < body.length) {
            val ch = body[p]
            if (ch < '0' || ch > '9') break
            v = v * 10 + (ch - '0')
            digits++
            p++
        }
        return if (digits == 0) Snapshot.UNKNOWN else v
    }
}
