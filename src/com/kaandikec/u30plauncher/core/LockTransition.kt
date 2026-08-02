package com.kaandikec.u30plauncher.core

/**
 * Kilit modu degisiminde ne yapilacagina karar verir.
 *
 * Kural: mevcut sir, YENI kayit basariyla tamamlanana kadar silinmez. Once
 * mod hemen degistirilip sir siliniyordu; kullanici kaydi yarida birakinca
 * "desen modundayim ama desenim yok" gibi tutarsiz bir durum kaliyordu.
 */
object LockTransition {
    /** Basili tut sir gerektirmez. */
    const val HOLD = 0

    /**
     * Yeni mod icin kullanilabilir bir sir var mi?
     *
     * Desen ve PIN sirlari birbirinin yerine gecemez, bu yuzden sirrin hangi
     * mod icin kaydedildigi de karsilastirilir.
     */
    fun hasUsableSecret(mode: Int, storedSecret: String, storedMode: Int): Boolean =
        storedSecret.isNotEmpty() && storedMode == mode

    /** Bu moda gecmek icin yeniden kayit gerekiyor mu? */
    fun needsEnrolment(nextMode: Int, storedSecret: String, storedMode: Int): Boolean =
        nextMode != HOLD && !hasUsableSecret(nextMode, storedSecret, storedMode)

    /**
     * Kilit acilirken meydan okuma gosterilmeli mi?
     *
     * Sir eksikse veya baska bir moda aitse kimseyi disarida birakmamak icin
     * dogrudan acilir.
     */
    fun requiresChallenge(mode: Int, storedSecret: String, storedMode: Int): Boolean =
        mode != HOLD && hasUsableSecret(mode, storedSecret, storedMode)
}
