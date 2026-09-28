package com.example.robotoperator.data.prefs

import android.content.Context
import android.content.SharedPreferences

class ServerSettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("robot_operator_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_CACHED_ROBOT_ID = "cached_robot_id"
        private const val KEY_ACTIVE_SESSION_ID = "active_session_id"
        private const val KEY_SIMULATION_MODE = "is_simulation_mode"
        private const val KEY_LAST_LOGIN_EMAIL = "last_login_email"
        // TODO: confirm refresh strategy with backend and store in secure store if provided in response body
        private const val KEY_SAVED_REFRESH_TOKEN = "saved_refresh_token"

        const val DEFAULT_SERVER_URL = "http://10.0.2.2:8000"
    }

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) {
            val sanitized = sanitizeUrl(value)
            prefs.edit().putString(KEY_SERVER_URL, sanitized).apply()
        }

    var isSimulationMode: Boolean
        get() = prefs.getBoolean(KEY_SIMULATION_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_SIMULATION_MODE, value).apply()

    var lastLoginEmail: String
        get() = prefs.getString(KEY_LAST_LOGIN_EMAIL, "operator@robotics.local") ?: "operator@robotics.local"
        set(value) = prefs.edit().putString(KEY_LAST_LOGIN_EMAIL, value).apply()

    var cachedRobotId: String?
        get() = prefs.getString(KEY_CACHED_ROBOT_ID, null)
        set(value) = prefs.edit().putString(KEY_CACHED_ROBOT_ID, value).apply()

    var activeSessionId: String?
        get() = prefs.getString(KEY_ACTIVE_SESSION_ID, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_SESSION_ID, value).apply()

    // TODO: confirm refresh strategy with backend
    var savedRefreshToken: String?
        get() = prefs.getString(KEY_SAVED_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_SAVED_REFRESH_TOKEN, value).apply()

    fun getWsUrl(path: String = "/ws/dashboard"): String {
        val base = serverUrl.trimEnd('/')
        val wsBase = when {
            base.startsWith("https://", ignoreCase = true) -> "wss://" + base.substring(8)
            base.startsWith("http://", ignoreCase = true) -> "ws://" + base.substring(7)
            else -> "ws://$base"
        }
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "$wsBase$cleanPath"
    }

    private fun sanitizeUrl(input: String): String {
        var clean = input.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "http://$clean"
        }
        return clean.trimEnd('/')
    }
}
