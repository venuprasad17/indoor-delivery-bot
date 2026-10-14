package com.example.robotoperator.state

import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.api.ApiErrorParser
import com.example.robotoperator.data.model.LoginRequest
import com.example.robotoperator.data.model.RegisterRequest
import com.example.robotoperator.data.model.RobotStatus
import com.example.robotoperator.data.model.User
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

    suspend fun login(email: String, pass: String): Boolean {
        _authError.value = null
        return try {
            val service = apiClient.getService()
            val loginResp = service.login(LoginRequest(email = email.trim(), password = pass))
            apiClient.setAuth(loginResp)

            val me = service.getMe()
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
        } catch (t: Throwable) {
            val parsed = ApiErrorParser.parse(t)
            _authError.value = parsed.message
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
        } catch (t: Throwable) {
            val parsed = ApiErrorParser.parse(t)
            _authError.value = parsed.message
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

    suspend fun logout() {
        try {
            apiClient.getService().logout()
        } catch (_: Exception) {
            // Ignore error on server logout
        } finally {
            logoutLocal(null)
        }
    }

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
