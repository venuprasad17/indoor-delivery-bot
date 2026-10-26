package com.example.robotoperator.data.prefs

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class BackendEnvironment(val label: String) {
    DEVELOPMENT("Development"),
    STAGING("Staging"),
    PRODUCTION("Production")
}

/**
 * Normalizes a backend URL and permits cleartext only for development targets.
 */
internal fun validateBackendEndpoint(raw: String, environment: BackendEnvironment): String {
    val input = raw.trim()
    require(input.isNotBlank()) { "Enter a backend URL." }

    val withScheme = if (input.contains("://")) input else "https://$input"
    val url = withScheme.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Enter a valid backend URL.")
    require(url.username.isEmpty() && url.password.isEmpty()) {
        "Backend URLs must not contain credentials."
    }
    require(url.query == null && url.fragment == null) {
        "Backend URLs must not contain a query string or fragment."
    }

    if (url.scheme == "http") {
        require(environment == BackendEnvironment.DEVELOPMENT) {
            "HTTP is allowed only for Development."
        }
    } else {
        require(url.scheme == "https") { "Backend endpoints must use HTTPS." }
    }

    return url.toString().removeSuffix("/")
}

/** A build-provided endpoint is a suggestion only; selecting an environment remains explicit. */
internal fun selectedBackendEndpoint(
    selectedEnvironment: BackendEnvironment?,
    configuredEndpoints: Map<BackendEnvironment, String?>
): String? = selectedEnvironment?.let(configuredEndpoints::get)?.takeIf { it.isNotBlank() }
