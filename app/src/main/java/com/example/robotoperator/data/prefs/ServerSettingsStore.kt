package com.example.robotoperator.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.robotoperator.data.api.dashboardWebSocketUrl

class ServerSettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("robot_operator_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SELECTED_ENVIRONMENT = "selected_backend_environment"
        private const val KEY_CACHED_ROBOT_ID = "cached_robot_id"
        private const val KEY_ACTIVE_SESSION_ID = "active_session_id"
        private const val KEY_ROBOT_WIFI_IP = "robot_wifi_ip"
        private const val KEY_ROBOT_WIFI_PORT = "robot_wifi_port"
        private const val KEY_CAMERA_IP = "robot_camera_ip"
        private const val KEY_CAMERA_PORT = "robot_camera_port"
        private const val KEY_CAMERA_STREAM_URL = "robot_camera_stream_url"
    }

    var robotWifiIp: String?
        get() = prefs.getString(KEY_ROBOT_WIFI_IP, null)
        set(value) = prefs.edit().putString(KEY_ROBOT_WIFI_IP, value).apply()

    var robotWifiPort: String
        get() = prefs.getString(KEY_ROBOT_WIFI_PORT, "9090") ?: "9090"
        set(value) = prefs.edit().putString(KEY_ROBOT_WIFI_PORT, value).apply()

    var cameraIp: String?
        get() = prefs.getString(KEY_CAMERA_IP, null)
        set(value) = prefs.edit().putString(KEY_CAMERA_IP, value).apply()

    var cameraPort: String
        get() = prefs.getString(KEY_CAMERA_PORT, "8080") ?: "8080"
        set(value) = prefs.edit().putString(KEY_CAMERA_PORT, value).apply()

    var cameraStreamUrl: String?
        get() = prefs.getString(KEY_CAMERA_STREAM_URL, null)
        set(value) = prefs.edit().putString(KEY_CAMERA_STREAM_URL, value).apply()

    private fun environmentKey(environment: BackendEnvironment) = "backend_url_${environment.name.lowercase()}"

    private fun buildConfiguredEndpoint(environment: BackendEnvironment): String = when (environment) {
        BackendEnvironment.DEVELOPMENT -> BuildConfig.DEVELOPMENT_BACKEND_URL
        BackendEnvironment.STAGING -> BuildConfig.STAGING_BACKEND_URL
        BackendEnvironment.PRODUCTION -> BuildConfig.PRODUCTION_BACKEND_URL
    }

    val selectedEnvironment: BackendEnvironment?
        get() = prefs.getString(KEY_SELECTED_ENVIRONMENT, null)
            ?.let { name -> BackendEnvironment.entries.firstOrNull { it.name == name } }

    val isConfigured: Boolean
        get() = selectedEnvironment?.let { endpointFor(it) != null } ?: false

    /** Empty until an environment has been explicitly selected and configured. */
    val serverUrl: String
        get() = selectedEnvironment?.let(::endpointFor).orEmpty()

    fun endpointFor(environment: BackendEnvironment): String? {
        val raw = prefs.getString(environmentKey(environment), null)
            ?: buildConfiguredEndpoint(environment).takeIf { it.isNotBlank() }
            ?: return null
        return runCatching { validateBackendEndpoint(raw, environment) }.getOrNull()
    }

    fun validateEndpoint(raw: String, environment: BackendEnvironment): String =
        validateBackendEndpoint(raw, environment)

    fun saveEndpoint(environment: BackendEnvironment, raw: String) {
        val normalized = validateBackendEndpoint(raw, environment)
        prefs.edit()
            .putString(environmentKey(environment), normalized)
            .putString(KEY_SELECTED_ENVIRONMENT, environment.name)
            .apply()
    }

    fun requireSelectedEnvironment(): BackendEnvironment = selectedEnvironment
        ?: throw IllegalStateException("Select and configure a backend environment before connecting.")

    fun requireServerUrl(): String = selectedEnvironment?.let(::endpointFor)
        ?: throw IllegalStateException("Select and configure a backend environment before connecting.")

    var cachedRobotId: String?
        get() = prefs.getString(KEY_CACHED_ROBOT_ID, null)
        set(value) = prefs.edit().putString(KEY_CACHED_ROBOT_ID, value).apply()

    var activeSessionId: String?
        get() = prefs.getString(KEY_ACTIVE_SESSION_ID, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_SESSION_ID, value).apply()

    fun getWebSocketUrl(ticket: String): String = dashboardWebSocketUrl(
        requireServerUrl(),
        ticket,
        requireSelectedEnvironment()
    )
}
