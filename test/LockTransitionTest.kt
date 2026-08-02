package com.kaandikec.u30plauncher.test

import com.kaandikec.u30plauncher.core.LockTransition
import com.kaandikec.u30plauncher.test.TestSupport.ok

object LockTransitionTest {
    private const val HOLD = 0
    private const val PATTERN = 1
    private const val PIN = 2
    private const val H = "abc123"

    fun run() {
        // Basili tut hicbir zaman kayit istemez
        ok("hold kayit istemez", !LockTransition.needsEnrolment(HOLD, "", -1))
        ok("hold sirla da istemez", !LockTransition.needsEnrolment(HOLD, H, PIN))

        // Sir yokken desen/PIN kayit ister
        ok("desen sirsiz", LockTransition.needsEnrolment(PATTERN, "", -1))
        ok("pin sirsiz", LockTransition.needsEnrolment(PIN, "", -1))

        // Sir baska moda aitse yine kayit ister
        ok("pin sirriyla desen", LockTransition.needsEnrolment(PATTERN, H, PIN))
        ok("desen sirriyla pin", LockTransition.needsEnrolment(PIN, H, PATTERN))

        // Ayni mod icin sir varsa dogrudan gecilir
        ok("desen sirriyla desen", !LockTransition.needsEnrolment(PATTERN, H, PATTERN))
        ok("pin sirriyla pin", !LockTransition.needsEnrolment(PIN, H, PIN))

        // Meydan okuma: yalnizca sir gecerliyken
        ok("gecerli sir meydan okur", LockTransition.requiresChallenge(PIN, H, PIN))
        ok("hold meydan okumaz", !LockTransition.requiresChallenge(HOLD, H, HOLD))
        ok("sir yoksa meydan okumaz", !LockTransition.requiresChallenge(PIN, "", -1))
        // Yarim kalmis gecis: kimse disarida kalmamali
        ok("uyumsuz sir meydan okumaz", !LockTransition.requiresChallenge(PIN, H, PATTERN))

        // PIN -> Hold -> PIN turunda sir korunuyorsa yeniden kayit gerekmez
        ok("hold uzerinden donus", !LockTransition.needsEnrolment(PIN, H, PIN))
    }
}
