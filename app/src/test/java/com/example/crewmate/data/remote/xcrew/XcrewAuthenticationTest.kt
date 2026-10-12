package com.example.crewmate.data.remote.xcrew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class XcrewAuthenticationTest {
    @Test
    fun successfulJsonIsNotAnExpiredSession() {
        assertFalse(
            XcrewAuthenticationResponseClassifier.isExpired(
                200,
                "application/json; charset=UTF-8",
                "{\"data\":[]}",
            ),
        )
    }

    @Test
    fun unauthorizedStatusAndRedirectedLoginHtmlAreExpired() {
        assertTrue(XcrewAuthenticationResponseClassifier.isExpired(401, "application/json", "{}"))
        assertTrue(XcrewAuthenticationResponseClassifier.isExpired(403, "application/json", "{}"))
        assertTrue(
            XcrewAuthenticationResponseClassifier.isExpired(
                200,
                "text/html; charset=UTF-8",
                "<!doctype html><title>Login</title>",
            ),
        )
    }

    @Test
    fun networkAndServerErrorsAreNotMisclassifiedAsExpired() {
        assertFalse(XcrewAuthenticationResponseClassifier.isExpired(0, "", ""))
        assertFalse(XcrewAuthenticationResponseClassifier.isExpired(404, "text/html", "<html>missing</html>"))
        assertFalse(XcrewAuthenticationResponseClassifier.isExpired(500, "text/html", "<html>server error</html>"))
        assertFalse(XcrewAuthenticationResponseClassifier.isExpired(200, "application/json", "not-json"))
    }

    @Test
    fun concurrentExpirySignalsStartOnlyOneRecoveryAndCanResetAfterLogin() {
        val gate = SessionRecoveryGate()
        val ready = CountDownLatch(20)
        val start = CountDownLatch(1)
        val startedRecoveries = AtomicInteger()
        val threads = (1..20).map {
            Thread {
                ready.countDown()
                start.await()
                if (gate.beginRecovery()) startedRecoveries.incrementAndGet()
            }.apply { start() }
        }

        ready.await()
        start.countDown()
        threads.forEach(Thread::join)
        assertEquals(1, startedRecoveries.get())

        gate.completeRecovery()
        assertTrue(gate.beginRecovery())
    }
}
