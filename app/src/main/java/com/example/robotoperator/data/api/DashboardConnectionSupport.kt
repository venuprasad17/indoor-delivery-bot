package com.example.robotoperator.data.api

import com.example.robotoperator.data.prefs.BackendEnvironment
import com.example.robotoperator.data.prefs.validateBackendEndpoint
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Requests a fresh, short-lived ticket each time this helper is called. */
internal suspend fun dashboardConnectionUrl(
    serverUrl: String,
    environment: BackendEnvironment,
    issueTicket: suspend () -> String
): String {
    val validatedUrl = validateBackendEndpoint(serverUrl, environment)
    return dashboardWebSocketUrl(validatedUrl, issueTicket(), environment)
}

internal fun dashboardWebSocketUrl(
    serverUrl: String,
    ticket: String,
    environment: BackendEnvironment
): String {
    require(ticket.isNotBlank()) { "Dashboard WebSocket ticket must not be blank" }
    val httpUrl = validateBackendEndpoint(serverUrl, environment).toHttpUrl()
        .newBuilder()
        .addPathSegments("ws/dashboard")
        .addQueryParameter("ticket", ticket)
        .build()
    return when (httpUrl.scheme) {
        "https" -> httpUrl.toString().replaceFirst("https://", "wss://")
        "http" -> httpUrl.toString().replaceFirst("http://", "ws://")
        else -> error("Server URL must use HTTP or HTTPS")
    }
}

/** Allows the initial connection plus a finite number of exponential-backoff retries. */
internal class ReconnectBackoff(
    private val maxRetries: Int = 5,
    private val initialDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 16_000L
) {
    private var retries = 0
    private var nextDelay = initialDelayMs

    @Synchronized
    fun nextDelayMs(): Long? {
        if (retries >= maxRetries) return null
        retries++
        return nextDelay.also { nextDelay = (nextDelay * 2).coerceAtMost(maxDelayMs) }
    }

    @Synchronized
    fun reset() {
        retries = 0
        nextDelay = initialDelayMs
    }
}

internal enum class DashboardDisconnectAction {
    RETRY_WITH_NEW_TICKET,
    REQUIRE_AUTHENTICATION
}

/** Maps backend close codes to the client's bounded retry or sign-in policy. */
internal fun dashboardDisconnectAction(closeCode: Int): DashboardDisconnectAction =
    when (closeCode) {
        4003 -> DashboardDisconnectAction.REQUIRE_AUTHENTICATION
        else -> DashboardDisconnectAction.RETRY_WITH_NEW_TICKET
    }
