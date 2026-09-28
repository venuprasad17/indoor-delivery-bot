package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.api.DashboardWebSocketClient
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.robotoperator.state.AuthManager
import com.example.robotoperator.state.AuthState
import com.example.robotoperator.state.RobotStateHolder
import com.example.robotoperator.ui.navigation.MainScaffold
import com.example.robotoperator.ui.screens.auth.LoginScreen
import com.example.robotoperator.ui.screens.server.ServerSettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Slate50

sealed interface AppScreen {
    data object Login : AppScreen
    data object ServerSettings : AppScreen
    data object Main : AppScreen
}

class MainActivity : ComponentActivity() {

    private lateinit var settingsStore: ServerSettingsStore
    private lateinit var robotStore: RobotStateHolder
    private lateinit var apiClient: ApiClient
    private lateinit var authManager: AuthManager
    private lateinit var wsClient: DashboardWebSocketClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settingsStore = ServerSettingsStore(applicationContext)
        robotStore = RobotStateHolder()

        apiClient = ApiClient(
            settingsStore = settingsStore,
            onAuthExpired = {
                authManager.handleForbidden()
                wsClient.stop()
            }
        )

        authManager = AuthManager(
            apiClient = apiClient,
            robotStore = robotStore,
            onLogoutCallback = {
                wsClient.stop()
            }
        )

        wsClient = DashboardWebSocketClient(
            settingsStore = settingsStore,
            apiClient = apiClient,
            robotStore = robotStore,
            scope = lifecycleScope,
            onForbiddenAdmin = {
                authManager.handleForbidden()
                wsClient.stop()
            }
        )

        // Lifecycle observer for WebSocket foreground/background management
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (authManager.authState.value is AuthState.Authenticated) {
                        wsClient.onAppForegrounded()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    wsClient.onAppBackgrounded()
                }
                else -> {}
            }
        })

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Slate50
                ) {
                    val authState by authManager.authState.collectAsState()
                    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Login) }

                    // Sync screen based on auth
                    LaunchedEffect(authState) {
                        if (authState is AuthState.Authenticated) {
                            currentScreen = AppScreen.Main
                            wsClient.start()
                        } else {
                            if (currentScreen == AppScreen.Main) {
                                currentScreen = AppScreen.Login
                                wsClient.stop()
                            }
                        }
                    }

                    when (currentScreen) {
                        AppScreen.ServerSettings -> {
                            ServerSettingsScreen(
                                settingsStore = settingsStore,
                                apiClient = apiClient,
                                onBack = { currentScreen = AppScreen.Login },
                                onSaved = { currentScreen = AppScreen.Login }
                            )
                        }
                        AppScreen.Login -> {
                            LoginScreen(
                                authManager = authManager,
                                settingsStore = settingsStore,
                                onNavigateServerSettings = {
                                    currentScreen = AppScreen.ServerSettings
                                },
                                onLoginSuccess = {
                                    currentScreen = AppScreen.Main
                                    wsClient.start()
                                }
                            )
                        }
                        AppScreen.Main -> {
                            MainScaffold(
                                apiClient = apiClient,
                                robotStore = robotStore,
                                settingsStore = settingsStore,
                                authManager = authManager,
                                onLogout = {
                                    wsClient.stop()
                                    currentScreen = AppScreen.Login
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wsClient.stop()
    }
}
