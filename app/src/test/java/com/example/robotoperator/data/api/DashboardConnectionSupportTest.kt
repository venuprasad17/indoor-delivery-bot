package com.example.robotoperator.data.api

import kotlinx.coroutines.runBlocking
import com.example.robotoperator.data.prefs.BackendEnvironment
import com.example.robotoperator.data.prefs.selectedBackendEndpoint
import com.example.robotoperator.data.prefs.validateBackendEndpoint
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardConnectionSupportTest {
    @Test
    fun expiredTicketCloseRetriesWithFreshTicketPolicy() {
        assertEquals(
            DashboardDisconnectAction.RETRY_WITH_NEW_TICKET,
            dashboardDisconnectAction(4001)
        )
    }

    @Test
    fun revokedOrInvalidTicketCloseRequiresAuthentication() {
        assertEquals(
            DashboardDisconnectAction.REQUIRE_AUTHENTICATION,
            dashboardDisconnectAction(4003)
        )
    }

    @Test
    fun obtainsANewTicketForEachConnectionUrl() = runBlocking {
        var issued = 0
        val issueTicket: suspend () -> String = { "ticket-${++issued}" }

        val first = dashboardConnectionUrl("https://example.test", BackendEnvironment.PRODUCTION, issueTicket)
        val second = dashboardConnectionUrl("https://example.test", BackendEnvironment.PRODUCTION, issueTicket)

        assertEquals(2, issued)
        assertEquals("wss://example.test/ws/dashboard?ticket=ticket-1", first)
        assertEquals("wss://example.test/ws/dashboard?ticket=ticket-2", second)
    }

    @Test
    fun dashboardUrlConvertsHttpsToWssAndEncodesTicket() {
        val url = dashboardWebSocketUrl("https://example.test/", "one+two", BackendEnvironment.PRODUCTION)

        assertEquals("wss://example.test/ws/dashboard?ticket=one%2Btwo", url)
        assertTrue(url.contains("ticket="))
        assertFalse(url.contains("token="))
    }

    @Test
    fun httpIsRejectedForStagingAndProduction() {
        listOf(BackendEnvironment.STAGING, BackendEnvironment.PRODUCTION).forEach { environment ->
            try {
                validateBackendEndpoint("http://192.168.1.12:8000", environment)
                throw AssertionError("Expected HTTP to be rejected for $environment")
            } catch (_: IllegalArgumentException) {
                // Expected: cleartext is restricted to development.
            }
        }
    }

    @Test
    fun httpsIsAcceptedAndHttpIsDevelopmentOnly() {
        assertEquals("https://api.example.test", validateBackendEndpoint("https://api.example.test/", BackendEnvironment.STAGING))
        assertEquals("http://localhost:8000", validateBackendEndpoint("http://localhost:8000", BackendEnvironment.DEVELOPMENT))
        assertEquals("http://127.0.0.1:8000", validateBackendEndpoint("http://127.0.0.1:8000", BackendEnvironment.DEVELOPMENT))
        assertEquals("http://10.0.2.2:8000", validateBackendEndpoint("http://10.0.2.2:8000", BackendEnvironment.DEVELOPMENT))
        assertEquals("http://192.168.1.12:8000", validateBackendEndpoint("http://192.168.1.12:8000", BackendEnvironment.DEVELOPMENT))
        try {
            validateBackendEndpoint("http://localhost:8000", BackendEnvironment.PRODUCTION)
            throw AssertionError("Production must reject HTTP, including loopback")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun developmentHttpConvertsToWs() = runBlocking {
        assertEquals(
            "ws://10.0.2.2:8000/ws/dashboard?ticket=dev-ticket",
            dashboardWebSocketUrl("http://10.0.2.2:8000", "dev-ticket", BackendEnvironment.DEVELOPMENT)
        )
        assertEquals(
            "ws://192.168.1.12:8000/ws/dashboard?ticket=dev-ticket",
            dashboardWebSocketUrl("http://192.168.1.12:8000", "dev-ticket", BackendEnvironment.DEVELOPMENT)
        )
    }

    @Test
    fun productionEndpointIsNotSelectedUntilEnvironmentIsExplicitlyChosen() {
        val configured = mapOf(BackendEnvironment.PRODUCTION to "https://prod.example")
        assertNull(selectedBackendEndpoint(null, configured))
        assertEquals("https://prod.example", selectedBackendEndpoint(BackendEnvironment.PRODUCTION, configured))
    }

    @Test
    fun reconnectBackoffIsBoundedAndResetsAfterConnection() {
        val retry = ReconnectBackoff(maxRetries = 3, initialDelayMs = 10, maxDelayMs = 20)

        assertEquals(10L, retry.nextDelayMs())
        assertEquals(20L, retry.nextDelayMs())
        assertEquals(20L, retry.nextDelayMs())
        assertNull(retry.nextDelayMs())
        retry.reset()
        assertEquals(10L, retry.nextDelayMs())
    }

    @Test
    fun authPathCookieIsSentOnlyToMatchingPaths() {
        val jar = ScopedCookieJar()
        val responseUrl = "https://api.example.test/auth/login".toHttpUrl()
        val sessionCookie = okhttp3.Cookie.Builder().name("session").value("host")
            .hostOnlyDomain("api.example.test").path("/auth").build()
        jar.saveFromResponse(responseUrl, listOf(sessionCookie))

        val authRequest = "https://api.example.test/auth/me".toHttpUrl()
        assertEquals(setOf("session"), jar.loadForRequest(authRequest).map { it.name }.toSet())
        assertTrue(jar.loadForRequest("https://api.example.test/other".toHttpUrl()).isEmpty())
    }

    @Test
    fun secureCookieIsNotSentOverHttp() {
        val jar = ScopedCookieJar()
        val responseUrl = "https://api.example.test/auth/login".toHttpUrl()
        val cookies = listOf(
            okhttp3.Cookie.Builder().name("session").value("host")
                .hostOnlyDomain("api.example.test").path("/auth").build(),
            okhttp3.Cookie.Builder().name("shared").value("domain")
                .domain("example.test").path("/").build(),
            okhttp3.Cookie.Builder().name("secure").value("yes")
                .domain("example.test").path("/").secure().build()
        )
        jar.saveFromResponse(responseUrl, cookies)

        val httpAuthRequest = "http://api.example.test/auth/me".toHttpUrl()
        assertEquals(setOf("session", "shared"), jar.loadForRequest(httpAuthRequest).map { it.name }.toSet())
    }

    @Test
    fun domainCookieMatchesSubdomainsButHostOnlyCookieDoesNot() {
        val jar = ScopedCookieJar()
        val responseUrl = "https://api.example.test/auth/login".toHttpUrl()
        val cookies = listOf(
            okhttp3.Cookie.Builder().name("session").value("host")
                .hostOnlyDomain("api.example.test").path("/auth").build(),
            okhttp3.Cookie.Builder().name("shared").value("domain")
                .domain("example.test").path("/").build()
        )
        jar.saveFromResponse(responseUrl, cookies)

        val subdomainRequest = "https://sub.example.test/auth/me".toHttpUrl()
        assertEquals(setOf("shared"), jar.loadForRequest(subdomainRequest).map { it.name }.toSet())
    }

    @Test
    fun expiredCookieIsNotSent() {
        val jar = ScopedCookieJar()
        val responseUrl = "https://api.example.test/auth/login".toHttpUrl()
        val expiredCookie = okhttp3.Cookie.Builder().name("expired").value("no")
            .domain("example.test").path("/").expiresAt(System.currentTimeMillis() - 1).build()
        jar.saveFromResponse(responseUrl, listOf(expiredCookie))

        assertTrue(jar.loadForRequest("https://api.example.test/auth/me".toHttpUrl()).isEmpty())
    }
}
