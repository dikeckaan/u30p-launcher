package com.kaandikec.u30plauncher.source

import android.content.Context
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import com.kaandikec.u30plauncher.core.Snapshot

/**
 * Telefon durumu push ile gelir — yoklama yok.
 *
 * `ServiceState` hucre kimligini ve teknolojiyi, `SignalStrength` sinyal
 * olculerini tasir; ikisi bagimsiz tetiklenir.
 */
class TelephonySource(ctx: Context) {
    private val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    var operator: String = ""; private set
    var netType: String = ""; private set
    var band: Int = Snapshot.UNKNOWN; private set
    var hasService: Boolean = false; private set
    var bandwidthKhz: Int = Snapshot.UNKNOWN; private set
    var pci: Int = Snapshot.UNKNOWN; private set
    var earfcn: Int = Snapshot.UNKNOWN; private set
    var tac: Int = Snapshot.UNKNOWN; private set
    var ci: Int = Snapshot.UNKNOWN; private set
    var rsrp: Int = Snapshot.UNKNOWN; private set
    var rsrq: Int = Snapshot.UNKNOWN; private set

    /** ondabir dB */
    var sinr: Int = Snapshot.UNKNOWN; private set
    var level: Int = 0; private set
    var permitted: Boolean = true; private set

    private var onChange: (() -> Unit)? = null
    private var callback: TelephonyCallback? = null

    private inner class Cb : TelephonyCallback(),
        TelephonyCallback.ServiceStateListener,
        TelephonyCallback.SignalStrengthsListener {

        override fun onServiceStateChanged(state: ServiceState) {
            try {
                hasService = state.state == ServiceState.STATE_IN_SERVICE
                operator = readOperator(state)
                netType = netTypeName(state)
                val bw = state.cellBandwidths
                if (bw != null && bw.isNotEmpty()) bandwidthKhz = bw[0]
                readCellIdentity(state)
            } catch (_: Throwable) {
            }
            onChange?.invoke()
        }

        override fun onSignalStrengthsChanged(ss: SignalStrength) {
            try {
                val lte = ss.getCellSignalStrengths(CellSignalStrengthLte::class.java)
                if (lte.isNotEmpty()) {
                    val s = lte[0]
                    rsrp = sane(s.rsrp)
                    rsrq = sane(s.rsrq)
                    // getRssnr() tam sayi dB dondurur; Snapshot ondabir tutar
                    val snr = sane(s.rssnr)
                    sinr = if (snr == Snapshot.UNKNOWN) snr else snr * 10
                    level = s.level
                } else {
                    val nr = ss.getCellSignalStrengths(CellSignalStrengthNr::class.java)
                    if (nr.isNotEmpty()) {
                        val s = nr[0]
                        rsrp = sane(s.ssRsrp)
                        rsrq = sane(s.ssRsrq)
                        sinr = if (s.ssSinr == Int.MAX_VALUE) Snapshot.UNKNOWN else s.ssSinr * 10
                        level = s.level
                    }
                }
            } catch (_: Throwable) {
            }
            onChange?.invoke()
        }
    }

    /** Android "bilinmiyor" icin Int.MAX_VALUE dondurur; kendi sabitimize cevir. */
    private fun sane(v: Int): Int =
        if (v == Int.MAX_VALUE || v == Int.MIN_VALUE) Snapshot.UNKNOWN else v

    /**
     * Konum servisi kapaliyken Android `ServiceState`'i izinlerden bagimsiz
     * olarak maskeler ve operator adi bos gelir. `networkOperatorName` konuma
     * bagli degildir; bu yuzden once o denenir.
     */
    private fun readOperator(state: ServiceState): String {
        val direct = try { tm.networkOperatorName?.trim() ?: "" } catch (_: Throwable) { "" }
        if (direct.isNotEmpty()) return direct
        return state.operatorAlphaLong?.trim() ?: ""
    }

    private fun netTypeName(state: ServiceState): String {
        for (nri in state.networkRegistrationInfoList) {
            if (!nri.isRegistered) continue
            return when (nri.accessNetworkTechnology) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                else -> continue
            }
        }
        return ""
    }

    private fun readCellIdentity(state: ServiceState) {
        for (nri in state.networkRegistrationInfoList) {
            when (val id = nri.cellIdentity) {
                is CellIdentityLte -> {
                    pci = sane(id.pci)
                    earfcn = sane(id.earfcn)
                    tac = sane(id.tac)
                    ci = sane(id.ci)
                    val b = id.bands
                    if (b != null && b.isNotEmpty()) band = b[0]
                    return
                }
                is CellIdentityNr -> {
                    pci = sane(id.pci)
                    earfcn = sane(id.nrarfcn)
                    tac = sane(id.tac)
                    ci = sane(id.nci.toInt())
                    val b = id.bands
                    if (b != null && b.isNotEmpty()) band = b[0]
                    return
                }
                else -> continue
            }
        }
    }

    fun start(onChange: () -> Unit) {
        this.onChange = onChange
        try {
            val cb = Cb()
            callback = cb
            tm.registerTelephonyCallback({ it.run() }, cb)
            permitted = true
        } catch (t: Throwable) {
            // SecurityException dahil: izin yoksa alanlar UNKNOWN kalir,
            // uygulama calismaya devam eder
            permitted = false
        }
    }

    fun stop() {
        callback?.let { try { tm.unregisterTelephonyCallback(it) } catch (_: Throwable) {} }
        callback = null
        onChange = null
    }
}
