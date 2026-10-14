package com.example.robotoperator.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

class ServerSettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("robot_operator_settings", Context.MODE_PRIVATE)

    companion object {
        const val LIVE_RENDER_BACKEND = "https://indoor-delivery-bot.onrender.com"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_CACHED_ROBOT_ID = "cached_robot_id"
        private const val KEY_ACTIVE_SESSION_ID = "active_session_id"
    }

    // Always prefer the live backend unless BuildConfig explicitly specifies an external non-localhost URL
    private val defaultBackendUrl: String =
        if (BuildConfig.BACKEND_URL.isNotBlank() &&
            !BuildConfig.BACKEND_URL.contains("localhost") &&
            !BuildConfig.BACKEND_URL.contains("10.0.2.2") &&
            !BuildConfig.BACKEND_URL.contains("127.0.0.1")
        ) {
            sanitizeUrl(BuildConfig.BACKEND_URL)
        } else {
            LIVE_RENDER_BACKEND
        }

    init {
        // One-time startup migration: if the saved URL points to localhost or emulator loopback,
        // immediately overwrite it with the live Render backend URL.
        val existing = prefs.getString(KEY_SERVER_URL, null)
        if (existing == null ||
            existing.contains("localhost") ||
            existing.contains("10.0.2.2") ||
            existing.contains("127.0.0.1")
        ) {
            prefs.edit().putString(KEY_SERVER_URL, LIVE_RENDER_BACKEND).apply()
        }
    }

    var serverUrl: String
        get() {
            val stored = prefs.getString(KEY_SERVER_URL, null)?.trim()
            if (stored.isNullOrBlank() ||
                stored.contains("localhost") ||
                stored.contains("10.0.2.2") ||
                stored.contains("127.0.0.1")
            ) {
                prefs.edit().putString(KEY_SERVER_URL, defaultBackendUrl).apply()
                return defaultBackendUrl
            }
            return sanitizeUrl(stored)
        }
        set(value) {
            val sanitized = sanitizeUrl(value)
            prefs.edit().putString(KEY_SERVER_URL, sanitized).apply()
        }

    fun sanitizeUrl(raw: String): String {
        var trimmed = raw.trim().removeSuffix("/")
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed = if (trimmed.startsWith("localhost") || trimmed.startsWith("10.0.2.2") || trimmed.startsWith("127.0.0.1")) {
                "http://$trimmed"
            } else {
                "https://$trimmed"
            }
        }
        return trimmed
    }

    fun resetToLiveBackend() {
        prefs.edit().putString(KEY_SERVER_URL, LIVE_RENDER_BACKEND).apply()
    }

    var cachedRobotId: String?
        get() = prefs.getString(KEY_CACHED_ROBOT_ID, null)
        set(value) = prefs.edit().putString(KEY_CACHED_ROBOT_ID, value).apply()

    var activeSessionId: String?
        get() = prefs.getString(KEY_ACTIVE_SESSION_ID, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_SESSION_ID, value).apply()

    fun getWebSocketUrl(token: String?): String {
        val base = serverUrl
        val wsBase = when {
            base.startsWith("https://", ignoreCase = true) -> base.replaceFirst("https://", "wss://", ignoreCase = true)
            base.startsWith("http://", ignoreCase = true) -> base.replaceFirst("http://", "ws://", ignoreCase = true)
            else -> "wss://$base"
        }
        return if (!token.isNullOrBlank()) {
            "$wsBase/ws/dashboard?token=$token"
        } else {
            "$wsBase/ws/dashboard"
        }
    }
}
