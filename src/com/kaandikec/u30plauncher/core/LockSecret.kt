package com.kaandikec.u30plauncher.core

import java.security.MessageDigest

/**
 * Kilit sirrini (desen veya PIN) saklamak ve dogrulamak.
 *
 * Duz metin yerine SHA-256 ozeti saklanir. Bu bir guvenlik urunu degil —
 * root erisimi olan biri zaten her seyi okur — ama sirri ayar dosyasinda
 * okunabilir birakmamak dogru olan.
 *
 * Desen hucre sirasi olarak kodlanir ("0-4-8-7"), PIN rakam dizisi olarak.
 */
object LockSecret {
    /** Desende en az bu kadar nokta birlestirilmeli. */
    const val MIN_PATTERN = 4

    /** PIN uzunlugu. */
    const val PIN_LENGTH = 4

    fun hash(code: String): String {
        if (code.isEmpty()) return ""
        val d = MessageDigest.getInstance("SHA-256").digest(code.toByteArray())
        val sb = StringBuilder(d.size * 2)
        for (b in d) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    fun matches(code: String, storedHash: String): Boolean {
        if (storedHash.isEmpty() || code.isEmpty()) return false
        return hash(code) == storedHash
    }

    /** Desen hucrelerini kararli bir metne cevirir. */
    fun encodePattern(cells: List<Int>): String = cells.joinToString("-")

    fun isPatternValid(cells: List<Int>): Boolean = cells.size >= MIN_PATTERN

    fun isPinValid(pin: String): Boolean =
        pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    private val HEX = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    )
}
