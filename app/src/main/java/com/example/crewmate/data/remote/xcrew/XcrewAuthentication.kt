package com.example.crewmate.data.remote.xcrew

import java.util.concurrent.atomic.AtomicBoolean

/** Only explicit authentication responses and login-page redirects count as expired sessions. */
object XcrewAuthenticationResponseClassifier {
    fun isExpired(statusCode: Int, contentType: String, body: String): Boolean {
        if (statusCode == 401 || statusCode == 403) return true
        if (statusCode != 200) return false

        return contentType.contains("text/html", ignoreCase = true) ||
            body.trimStart().startsWith("<", ignoreCase = true)
    }
}

/** Ensures concurrent expired API responses trigger one reauthentication flow. */
class SessionRecoveryGate {
    private val pending = AtomicBoolean(false)

    fun beginRecovery(): Boolean = pending.compareAndSet(false, true)

    fun completeRecovery() {
        pending.set(false)
    }
}
