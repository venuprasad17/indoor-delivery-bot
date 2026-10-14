package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.robotoperator.state.AuthManager
import com.example.robotoperator.state.RobotStateHolder
import com.example.robotoperator.ui.MainScaffold
import com.example.robotoperator.ui.screens.auth.LoginScreen
import com.example.robotoperator.ui.screens.server.ServerSettingsScreen
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RobotOperatorTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RobotOperatorTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    color = PureWhite
                ) {
                    OperatorAppRoot(
                        settingsStore = remember { ServerSettingsStore(applicationContext) }
                    )
                }
            }
        }
    }
}

@Composable
fun OperatorAppRoot(settingsStore: ServerSettingsStore) {
    val apiClient = remember(settingsStore) { ApiClient(settingsStore) }
    val authManager = remember(apiClient) { AuthManager(apiClient) }
    val stateHolder = remember { RobotStateHolder() }

    val isAuthenticated by authManager.isAuthenticated.collectAsState()
    var showServerSettingsOnAuth by remember { mutableStateOf(false) }

    if (isAuthenticated) {
        MainScaffold(
            settingsStore = settingsStore,
            apiClient = apiClient,
            authManager = authManager,
            stateHolder = stateHolder,
            onLoggedOut = {
                showServerSettingsOnAuth = false
            }
        )
    } else {
        if (showServerSettingsOnAuth) {
            BackHandler {
                showServerSettingsOnAuth = false
            }
            ServerSettingsScreen(
                settingsStore = settingsStore,
                apiClient = apiClient,
                canNavigateBack = true,
                onNavigateBack = { showServerSettingsOnAuth = false },
                onSaved = { showServerSettingsOnAuth = false }
            )
        } else {
            LoginScreen(
                authManager = authManager,
                settingsStore = settingsStore,
                onNavigateServerSettings = {
                    showServerSettingsOnAuth = true
                },
                onLoginSuccess = {
                    // isAuthenticated updates automatically
                }
            )
        }
    }
}
