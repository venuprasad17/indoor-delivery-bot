package com.example.robotoperator.data.model

fun normalizedConnectionState(connectionState: String?): String {
    val normalized = connectionState?.trim()?.uppercase() ?: return "UNKNOWN"
    return when (normalized) {
        "ONLINE", "STALE", "OFFLINE" -> normalized
        else -> "UNKNOWN"
    }
}

fun safetyTelemetryLabel(safetyState: String?): String =
    safetyState?.takeIf { it.isNotBlank() }?.let { "LAST REPORTED ${it.uppercase()}" } ?: "UNKNOWN"

fun safetyTelemetryContext(
    connectionState: String?,
    dashboardFeedConnected: Boolean? = null
): String {
    val normalized = normalizedConnectionState(connectionState)
    if (dashboardFeedConnected == false) {
        return if (normalized == "UNKNOWN") {
            "Dashboard feed is disconnected; current robot and safety status are unknown, and physical condition is unverified."
        } else {
            "Dashboard feed is disconnected; robot connection and safety values are last reported and stale. Physical condition is unverified."
        }
    }
    return when (normalized) {
        "ONLINE" -> "Latest safety value is reported telemetry; physical condition is unverified."
        "STALE" -> "Safety telemetry is stale; physical condition is unverified."
        "OFFLINE" -> "Robot is offline; last safety value is historical and physical condition is unverified."
        else -> "Robot connection is unknown; physical condition is unverified."
    }
}
