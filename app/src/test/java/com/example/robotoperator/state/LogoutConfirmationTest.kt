package com.example.robotoperator.state

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogoutConfirmationTest {
    @Test
    fun failedServerLogoutIsReportedAndLocalSessionIsCleared() = runBlocking {
        var clearMessage: String? = null

        val confirmed = confirmServerLogout(
            revokeServer = { false },
            clearLocal = { clearMessage = it }
        )

        assertFalse(confirmed)
        assertEquals(
            "Signed out on this device, but server session revocation could not be confirmed.",
            clearMessage
        )
    }

    @Test
    fun logoutTransportFailureIsNotReportedAsRevoked() = runBlocking {
        var clearMessage: String? = null

        val confirmed = confirmServerLogout(
            revokeServer = { throw java.io.IOException("offline") },
            clearLocal = { clearMessage = it }
        )

        assertFalse(confirmed)
        assertTrue(clearMessage?.contains("revocation could not be confirmed") == true)
    }

    @Test
    fun successfulServerLogoutClearsLocalSessionWithoutWarning() = runBlocking {
        var clearMessage: String? = "old warning"

        val confirmed = confirmServerLogout(
            revokeServer = { true },
            clearLocal = { clearMessage = it }
        )

        assertTrue(confirmed)
        assertNull(clearMessage)
    }
}
