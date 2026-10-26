package com.example.robotoperator.state

import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.api.ApiErrorParser
import com.example.robotoperator.data.model.LoginRequest
import com.example.robotoperator.data.model.RegisterRequest
import com.example.robotoperator.data.model.RobotStatus
import com.example.robotoperator.data.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthManager(private val apiClient: ApiClient) {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _grantedRobots = MutableStateFlow<List<RobotStatus>>(emptyList())
    val grantedRobots: StateFlow<List<RobotStatus>> = _grantedRobots.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    init {
        apiClient.onAuthRevoked = {
            logoutLocal("Session expired. Please sign in again.")
        }
    }

    suspend fun login(email: String, pass: String, expectedRole: String? = null): Boolean {
        _authError.value = null
        return try {
            val service = apiClient.getService()
            val loginResp = service.login(LoginRequest(email = email.trim(), password = pass))
            apiClient.setAuth(loginResp)

            val me = service.getMe()
            if (expectedRole != null && !matchesLoginRole(expectedRole, me.role)) {
                try { service.logout() } catch (_: Exception) { /* Always clear local credentials. */ }
                logoutLocal("This account cannot use the ${expectedRole.lowercase()} login. Choose the matching account type and sign in again.")
                return false
            }
            _currentUser.value = me

            // For USER role, fetch granted robots
            if (me.role == "USER") {
                try {
                    _grantedRobots.value = service.getMyRobots()
                } catch (_: Exception) {
                    _grantedRobots.value = emptyList()
                }
            }

            _isAuthenticated.value = true
            true
        } catch (cancelled: CancellationException) {
            logoutLocal(null)
            throw cancelled
        } catch (t: Exception) {
            val parsed = ApiErrorParser.parse(t)
            logoutLocal(parsed.message)
            false
        }
    }

    suspend fun register(name: String, email: String, pass: String): Boolean {
        _authError.value = null
        return try {
            val service = apiClient.getService()
            val regResp = service.register(
                RegisterRequest(
                    email = email.trim(),
                    password = pass,
                    name = name.trim()
                )
            )
            // Auto-login: response includes access_token directly
            apiClient.setAuth(regResp)

            val me = service.getMe()
            if (!matchesLoginRole("USER", me.role)) {
                try { service.logout() } catch (_: Exception) { }
                logoutLocal("Registration did not return a user account. Contact your administrator.")
                return false
            }
            _currentUser.value = me

            if (me.role == "USER") {
                try {
                    _grantedRobots.value = service.getMyRobots()
                } catch (_: Exception) {
                    _grantedRobots.value = emptyList()
                }
            }

            _isAuthenticated.value = true
            true
        } catch (cancelled: CancellationException) {
            logoutLocal(null)
            throw cancelled
        } catch (t: Exception) {
            val parsed = ApiErrorParser.parse(t)
            logoutLocal(parsed.message)
            false
        }
    }

    suspend fun refreshGrantedRobots() {
        val user = _currentUser.value ?: return
        if (user.role == "USER") {
            try {
                _grantedRobots.value = apiClient.getService().getMyRobots()
            } catch (_: Exception) {}
        }
    }

    suspend fun logout(): Boolean = confirmServerLogout(
        revokeServer = {
            try {
                apiClient.getService().logout().isSuccessful
            } catch (_: Exception) {
                false
            }
        },
        clearLocal = ::logoutLocal
    )

    fun logoutLocal(message: String?) {
        apiClient.clearAuth()
        _currentUser.value = null
        _grantedRobots.value = emptyList()
        _isAuthenticated.value = false
        _authError.value = message
    }

    fun clearError() {
        _authError.value = null
    }
}

internal suspend fun confirmServerLogout(
    revokeServer: suspend () -> Boolean,
    clearLocal: (String?) -> Unit
): Boolean {
    val confirmed = try { revokeServer() } catch (_: Exception) { false }
    clearLocal(
        if (confirmed) null else
            "Signed out on this device, but server session revocation could not be confirmed."
    )
    return confirmed
}
