package com.kaandikec.u30plauncher.core

/**
 * Ekranda gosterilen her seyin tek dogruluk kaynagi.
 *
 * `data class` olmasi bilincli: uretilen `equals` sayesinde "degismediyse cizme"
 * kontrolu tek karsilastirmaya iner. Alanlarin tamami ilkel — karsilastirma
 * maliyeti ihmal edilebilir.
 *
 * Bilinmeyen degerler `UNKNOWN`; cizim katmani bunu "—" olarak gosterir.
 */
data class Snapshot(
    val operator: String = "",
    val netType: String = "",
    val band: Int = UNKNOWN,
    val hasService: Boolean = false,

    val rsrp: Int = UNKNOWN,
    val rsrq: Int = UNKNOWN,
    /** ondabir dB: 60 = 6.0 dB */
    val sinr: Int = UNKNOWN,
    val pci: Int = UNKNOWN,
    val earfcn: Int = UNKNOWN,
    val tac: Int = UNKNOWN,
    val ci: Int = UNKNOWN,
    val bandwidthKhz: Int = UNKNOWN,
    /** 0..4 */
    val signalLevel: Int = 0,

    val rxSpeed: Long = 0,
    val txSpeed: Long = 0,
    val todayBytes: Long = 0,
    val monthBytes: Long = 0,

    val batteryPct: Int = UNKNOWN,
    val batteryUa: Int = UNKNOWN,
    /** ondabir °C: 350 = 35.0 °C */
    val batteryTempC: Int = UNKNOWN,
    val cpuTempC: Int = UNKNOWN,
    val batteryCharging: Boolean = false,
    /** Kalan dakika; sarjdayken veya hesaplanamiyorsa UNKNOWN */
    val batteryMinutesLeft: Int = UNKNOWN,

    val cpuPercent: Int = UNKNOWN,
    val ramUsedKb: Long = 0,
    val ramTotalKb: Long = 0,
    val storageUsedBytes: Long = 0,
    val storageTotalBytes: Long = 0,
    /** yuz kati: 1048 = 10.48 */
    val loadAvgX100: Int = UNKNOWN,
    val uptimeSec: Long = 0,

    val vpnUp: Boolean = false,
    /** -1 = bilinmiyor (root yok) */
    val clients: Int = -1,

    /**
     * Faturalama doneminin basladigi ayin gunu.
     *
     * Etiket buna gore secilir: donem ayin 1'inde baslamiyorsa "BU AY"
     * yaniltici olur, gosterilen pencere iki takvim ayina yayilir.
     */
    /** En cok CPU yiyen surecin adi; yoksa bos. */
    val topProcName: String = "",

    /** O surecin toplam CPU icindeki yuzdesi, ONDA BIRLIK; yoksa UNKNOWN. */
    val topProcPercent: Int = UNKNOWN,

    val cycleDay: Int = 1,

    /** Donem veri limiti (bayt); 0 = sinirsiz. */
    val dataLimitBytes: Long = 0,

    /** Saat dakika hassasiyetinde; saniye degisimi cizim tetiklemesin. */
    val clockMinuteOfDay: Int = 0,

    val rootAvailable: Boolean = false,
    val phonePermission: Boolean = false
) {
    companion object {
        const val UNKNOWN = Int.MIN_VALUE
        val EMPTY = Snapshot()
    }
}
