package com.example.robotoperator.state

import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.api.ApiErrorParser
import com.example.robotoperator.data.model.LoginRequest
import com.example.robotoperator.data.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AuthState {
    data object Unauthenticated : AuthState
    data object Authenticating : AuthState
    data class Authenticated(val user: User) : AuthState
    data class AdminForbidden(val message: String = "This app is for administrators") : AuthState
    data class AuthError(val message: String) : AuthState
}

class AuthManager(
    private val apiClient: ApiClient,
    private val robotStore: RobotStateHolder,
    private val onLogoutCallback: () -> Unit = {}
) {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    suspend fun login(email: String, password: String):Result<User> {
        _authState.value = AuthState.Authenticating
        return try {
            val service = apiClient.getService()
            val loginRes = service.login(LoginRequest(email.trim(), password))
            apiClient.accessToken = loginRes.access_token

            // Verify user and ensure role is ADMIN
            val me = service.getMe()
            if (!me.role.equals("ADMIN", ignoreCase = true)) {
                // App is ADMIN only
                apiClient.clearAuth()
                _authState.value = AuthState.AdminForbidden()
                Result.failure(IllegalAccessException("This app is for administrators"))
            } else {
                _authState.value = AuthState.Authenticated(me)
                Result.success(me)
            }
        } catch (e: Exception) {
            apiClient.clearAuth()
            val parsed = ApiErrorParser.parse(e)
            val msg = parsed.message
            _authState.value = AuthState.AuthError(msg)
            Result.failure(Exception(msg))
        }
    }

    suspend fun checkExistingSession(): Boolean {
        // If we have an existing session (or refresh cookie), attempt me or refresh
        return try {
            val service = apiClient.getService()
            val me = service.getMe()
            if (me.role.equals("ADMIN", ignoreCase = true)) {
                _authState.value = AuthState.Authenticated(me)
                true
            } else {
                logout()
                _authState.value = AuthState.AdminForbidden()
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun logout() {
        try {
            apiClient.getService().logout()
        } catch (e: Exception) {
            // Ignore network logout errors
        } finally {
            apiClient.clearAuth()
            _authState.value = AuthState.Unauthenticated
            onLogoutCallback()
        }
    }

    fun handleForbidden() {
        apiClient.clearAuth()
        _authState.value = AuthState.AdminForbidden()
        onLogoutCallback()
    }
}
