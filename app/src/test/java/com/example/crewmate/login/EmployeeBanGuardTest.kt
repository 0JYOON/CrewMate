package com.example.crewmate.login

import com.example.crewmate.ui.theme.AppFontOption
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class EmployeeBanGuardTest {

    @Test
    fun hashEmployeeNumber_isDeterministicSaltedSha256AndTrimsWhitespace() {
        val h1 = EmployeeBanGuard.hashEmployeeNumber("1234567")
        val h2 = EmployeeBanGuard.hashEmployeeNumber("  1234567  ")
        assertEquals(64, h1.length)
        assertEquals(h1, h2)

        val unsalted = MessageDigest.getInstance("SHA-256")
            .digest("1234567".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertNotEquals(unsalted, h1)
    }

    @Test
    fun parseRemoteHashes_filtersValid64CharHexHashes() {
        val sampleHash = EmployeeBanGuard.hashEmployeeNumber("9999999").uppercase()
        val json = """{"hashes": ["$sampleHash", "invalid-short", "12345"]}"""
        val parsed = EmployeeBanGuard.parseRemoteHashes(json)

        assertEquals(1, parsed.size)
        assertTrue(parsed.contains(sampleHash.lowercase()))
        assertTrue(EmployeeBanGuard.parseRemoteHashes("").isEmpty())
    }

    @Test
    fun isEmployeeBanned_skipsRemoteFetchWhenAutoLogin() = runBlocking {
        val banned = EmployeeBanGuard.isEmployeeBanned(
            employeeNumber = "1234567",
            fetchRemote = false,
            remoteUrl = "http://127.0.0.1:1/unreachable"
        )
        assertFalse(banned)
    }

    @Test
    fun appFontOption_containsFourEnglishSingleLineTitles() {
        val titles = AppFontOption.entries.map { it.title }
        assertEquals(listOf("System", "Pretendard", "SUIT", "Spoqa Han Sans Neo"), titles)
    }
}
