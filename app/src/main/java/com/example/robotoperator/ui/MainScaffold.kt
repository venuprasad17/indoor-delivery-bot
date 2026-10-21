package com.example.robotoperator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.api.ApiErrorParser
import com.example.robotoperator.data.api.DashboardWebSocketClient
import com.example.robotoperator.data.model.EmergencyStopRequest
import com.example.robotoperator.data.model.canAccessRobotDashboard
import com.example.robotoperator.data.model.initialMainTabName
import com.example.robotoperator.data.model.primaryMainTabNames
import com.example.robotoperator.data.model.ModeRequest
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.robotoperator.state.AuthManager
import com.example.robotoperator.state.RobotStateHolder
import com.example.robotoperator.ui.components.EStopBanner
import com.example.robotoperator.ui.components.ModeSelectionDialog
import com.example.robotoperator.ui.components.StatusStrip
import com.example.robotoperator.ui.screens.dashboard.DashboardScreen
import com.example.robotoperator.ui.screens.deliveries.DeliveriesScreen
import com.example.robotoperator.ui.screens.deliveries.DeliveryDetailScreen
import com.example.robotoperator.ui.screens.destinations.DestinationsScreen
import com.example.robotoperator.ui.screens.mapping.MappingDetailScreen
import com.example.robotoperator.ui.screens.mapping.LiveMappingConsoleScreen
import com.example.robotoperator.ui.screens.mapping.MappingListScreen
import com.example.robotoperator.ui.screens.more.EventsScreen
import com.example.robotoperator.ui.screens.more.MoreScreen
import com.example.robotoperator.ui.screens.more.UsersScreen
import com.example.robotoperator.ui.screens.server.ServerSettingsScreen
import com.example.ui.theme.ActionBlue
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Slate600
import com.example.ui.theme.StatusRed
import kotlinx.coroutines.launch

enum class MainTab(val title: String, val icon: ImageVector, val tag: String) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard, "tab_dashboard"),
    MAPPING("Mapping", Icons.Default.Map, "tab_mapping"),
    LIVE_CONSOLE("Console", Icons.Default.Videocam, "tab_live_console"),
    DESTINATIONS("Destinations", Icons.Default.Place, "tab_destinations"),
    DELIVERIES("Deliveries", Icons.Default.LocalShipping, "tab_deliveries"),
    MORE("More", Icons.Default.MoreHoriz, "tab_more")
}

sealed class SubScreen {
    object None : SubScreen()
    data class MappingDetail(val sessionId: String) : SubScreen()
    data class DeliveryDetail(val deliveryId: String) : SubScreen()
    object Events : SubScreen()
    object Users : SubScreen()
    object ServerSettings : SubScreen()
}

@Composable
fun MainScaffold(
    settingsStore: ServerSettingsStore,
    apiClient: ApiClient,
    authManager: AuthManager,
    stateHolder: RobotStateHolder,
    onLoggedOut: () -> Unit
) {
    val currentUser by authManager.currentUser.collectAsState()
    val isAdmin = canAccessRobotDashboard(currentUser?.role)
    val visibleTabs = primaryMainTabNames(currentUser?.role)
    var currentTab by rememberSaveable(currentUser?.role) {
        mutableStateOf(MainTab.valueOf(initialMainTabName(currentUser?.role)))
    }
    var currentSubScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }

    val robotStatus by stateHolder.robotStatus.collectAsState()
    val isLiveConnected by stateHolder.isLiveConnected.collectAsState()
    val emergencyStopError by stateHolder.emergencyStopError.collectAsState()
    val needsModeReselection by stateHolder.needsModeReselection.collectAsState()

    var showModeDialog by remember { mutableStateOf(false) }
    var isSwitchingMode by remember { mutableStateOf(false) }
    var modeErrorMessage by remember { mutableStateOf<String?>(null) }
    var isResettingEStop by remember { mutableStateOf(false) }
    var dashboardAuthorizationDenied by remember { mutableStateOf(false) }
    var openRobotConnectionOnConsole by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // WebSocket telemetry client & initial fetch
    val wsClient = remember(settingsStore, apiClient, stateHolder) {
        DashboardWebSocketClient(settingsStore, apiClient, stateHolder).apply {
            onAdminForbidden = {
                // HTTP 403 means authorization was denied; it is not an expired login.
                dashboardAuthorizationDenied = true
                stateHolder.setLiveConnected(false)
            }
            onAuthenticationRequired = { message ->
                authManager.logoutLocal(message)
                onLoggedOut()
            }
        }
    }

    LaunchedEffect(wsClient, isAdmin) {
        if (!isAdmin) {
            wsClient.stop()
            return@LaunchedEffect
        }
        var dashboardStatusAllowed = true
        try {
            val service = apiClient.getService()
            val status = service.getRobotStatus()
            stateHolder.updateRobotStatus(status)
            if (status.id.isNotBlank()) {
                settingsStore.cachedRobotId = status.id
            }
        } catch (error: Exception) {
            if ((error as? retrofit2.HttpException)?.code() == 403) {
                dashboardAuthorizationDenied = true
                dashboardStatusAllowed = false
            }
        }
        if (dashboardStatusAllowed) wsClient.start()
    }

    DisposableEffect(wsClient) {
        onDispose {
            wsClient.stop()
        }
    }

    LaunchedEffect(needsModeReselection, isAdmin) {
        if (needsModeReselection && isAdmin) {
            showModeDialog = true
        }
    }

    LaunchedEffect(currentUser?.role) {
        dashboardAuthorizationDenied = false
    }

    // Handle back button for sub-screens
    if (currentSubScreen !is SubScreen.None) {
        BackHandler {
            currentSubScreen = SubScreen.None
        }
    }

    BackHandler(enabled = currentSubScreen is SubScreen.None && currentTab.name !in visibleTabs) {
        currentTab = MainTab.MORE
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isAdmin) StatusStrip(
                    robotStatus = robotStatus,
                    isLiveConnected = isLiveConnected,
                    canAdministerRobot = isAdmin,
                    onEStopClick = {
                        scope.launch {
                            try {
                                val resp = apiClient.getService().emergencyStop(EmergencyStopRequest())
                                stateHolder.updateControlMode(resp.control_mode)
                                stateHolder.updateSafetyState(resp.safety_state, physicalExecutionConfirmed = resp.physical_execution_confirmed)
                                stateHolder.setNeedsModeReselection(resp.needs_mode_reselection)
                                stateHolder.setEmergencyStopError(null)
                            } catch (e: Exception) {
                                stateHolder.setEmergencyStopError(ApiErrorParser.parseErrorMessage(e))
                            }
                        }
                    },
                    onModeClick = {
                        modeErrorMessage = null
                        showModeDialog = true
                    }
                )

                if (isAdmin && (robotStatus.safety_state.equals("EMERGENCY_STOP", ignoreCase = true) || emergencyStopError != null)) {
                    EStopBanner(
                        errorMessage = emergencyStopError,
                        physicalExecutionConfirmed = robotStatus.physical_execution_confirmed,
                        canReset = isAdmin,
                        isResetting = isResettingEStop,
                        onResetClick = {
                            scope.launch {
                                isResettingEStop = true
                                try {
                                    val resp = apiClient.getService().resetEmergencyStop()
                                    stateHolder.updateControlMode(resp.control_mode)
                                    stateHolder.updateSafetyState(resp.safety_state, physicalExecutionConfirmed = resp.physical_execution_confirmed)
                                    stateHolder.setNeedsModeReselection(resp.needs_mode_reselection)
                                    stateHolder.setEmergencyStopError(null)
                                    if (resp.needs_mode_reselection) {
                                        showModeDialog = true
                                    }
                                } catch (e: Exception) {
                                    stateHolder.setEmergencyStopError(ApiErrorParser.parseErrorMessage(e))
                                } finally {
                                    isResettingEStop = false
                                }
                            }
                        }
                    )
                }

                if (isAdmin && currentSubScreen is SubScreen.None) {
                    TextButton(
                        onClick = {
                            currentTab = MainTab.LIVE_CONSOLE
                            openRobotConnectionOnConsole = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("open_robot_wifi_settings")
                    ) {
                        Text("ROBOT WI-FI · IP / PORT")
                    }
                }
            }
        },
        bottomBar = {
            if (currentSubScreen is SubScreen.None) {
                NavigationBar(
                    containerColor = PureWhite,
                    tonalElevation = 0.dp,
                    modifier = Modifier.heightIn(min = 80.dp)
                ) {
                    MainTab.values().filter { tab -> tab.name in visibleTabs }.forEach { tab ->
                        val selected = currentTab == tab || (tab == MainTab.MORE && currentTab.name !in visibleTabs)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                currentTab = tab
                                currentSubScreen = SubScreen.None
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title
                                )
                            },
                            label = { Text(tab.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ActionBlue,
                                selectedTextColor = ActionBlue,
                                indicatorColor = PureWhite,
                                unselectedIconColor = Slate600,
                                unselectedTextColor = Slate600
                            ),
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
            }
        },
        containerColor = PureWhite
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (dashboardAuthorizationDenied && isAdmin && currentTab == MainTab.DASHBOARD && currentSubScreen is SubScreen.None) {
                Text(
                    text = "Dashboard access is restricted for this account (HTTP 403). Your sign-in remains active.",
                    modifier = Modifier.padding(24.dp),
                    color = StatusRed
                )
            } else when (val sub = currentSubScreen) {
                is SubScreen.MappingDetail -> {
                    MappingDetailScreen(
                        sessionId = sub.sessionId,
                        apiClient = apiClient,
                        stateHolder = stateHolder,
                        onNavigateBack = { currentSubScreen = SubScreen.None }
                    )
                }
                is SubScreen.DeliveryDetail -> {
                    DeliveryDetailScreen(
                        deliveryId = sub.deliveryId,
                        apiClient = apiClient,
                        userRole = currentUser?.role,
                        onNavigateBack = { currentSubScreen = SubScreen.None }
                    )
                }
                is SubScreen.Events -> {
                    EventsScreen(
                        apiClient = apiClient,
                        onNavigateBack = { currentSubScreen = SubScreen.None }
                    )
                }
                is SubScreen.Users -> {
                    UsersScreen(
                        apiClient = apiClient,
                        onNavigateBack = { currentSubScreen = SubScreen.None }
                    )
                }
                is SubScreen.ServerSettings -> {
                    ServerSettingsScreen(
                        settingsStore = settingsStore,
                        apiClient = apiClient,
                        canNavigateBack = true,
                        allowEndpointChanges = false,
                        onNavigateBack = { currentSubScreen = SubScreen.None },
                        onSaved = { currentSubScreen = SubScreen.None }
                    )
                }
                is SubScreen.None -> {
                    when (currentTab) {
                        MainTab.DASHBOARD -> {
                            DashboardScreen(
                                stateHolder = stateHolder,
                                apiClient = apiClient,
                                settingsStore = settingsStore,
                                canAdministerRobot = isAdmin,
                                onOpenModeDialog = {
                                    modeErrorMessage = null
                                    if (isAdmin) showModeDialog = true
                                }
                            )
                        }
                        MainTab.MAPPING -> {
                            MappingListScreen(
                                apiClient = apiClient,
                                settingsStore = settingsStore,
                                onSelectSession = { sessionId ->
                                    currentSubScreen = SubScreen.MappingDetail(sessionId)
                                }
                            )
                        }
                        MainTab.LIVE_CONSOLE -> {
                            LiveMappingConsoleScreen(
                                apiClient = apiClient,
                                settingsStore = settingsStore,
                                openConnectionDialogRequest = openRobotConnectionOnConsole,
                                onConnectionDialogRequestConsumed = { openRobotConnectionOnConsole = false }
                            )
                        }
                        MainTab.DESTINATIONS -> {
                            DestinationsScreen(
                                apiClient = apiClient,
                                stateHolder = stateHolder
                            )
                        }
                        MainTab.DELIVERIES -> {
                            DeliveriesScreen(
                                apiClient = apiClient,
                                stateHolder = stateHolder,
                                settingsStore = settingsStore,
                                authManager = authManager,
                                onSelectDelivery = { deliveryId ->
                                    currentSubScreen = SubScreen.DeliveryDetail(deliveryId)
                                }
                            )
                        }
                        MainTab.MORE -> {
                            MoreScreen(
                                authManager = authManager,
                                settingsStore = settingsStore,
                                onNavigateToEvents = {
                                    if (authManager.currentUser.value?.role == "ADMIN") {
                                        currentSubScreen = SubScreen.Events
                                    }
                                },
                                onNavigateToUsers = {
                                    if (authManager.currentUser.value?.role == "ADMIN") {
                                        currentSubScreen = SubScreen.Users
                                    }
                                },
                                onNavigateToServerSettings = {
                                    currentSubScreen = SubScreen.ServerSettings
                                },
                                onLoggedOut = onLoggedOut,
                                onNavigateToMapping = { if (isAdmin) currentTab = MainTab.MAPPING },
                                onNavigateToConsole = { if (isAdmin) currentTab = MainTab.LIVE_CONSOLE },
                                onNavigateToDestinations = { if (isAdmin) currentTab = MainTab.DESTINATIONS }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showModeDialog && isAdmin) {
        ModeSelectionDialog(
            currentMode = robotStatus.control_mode,
            errorMessage = modeErrorMessage,
            isSwitching = isSwitchingMode,
            onDismiss = {
                if (!isSwitchingMode) {
                    showModeDialog = false
                    stateHolder.setNeedsModeReselection(false)
                }
            },
            onConfirmMode = { targetMode ->
                scope.launch {
                    isSwitchingMode = true
                    modeErrorMessage = null
                    try {
                        val updated = apiClient.getService().setRobotMode(
                            ModeRequest(mode = targetMode, acknowledgment = true)
                        )
                        stateHolder.updateControlMode(updated.mode)
                        stateHolder.updateSafetyState(updated.safety_state)
                        stateHolder.setNeedsModeReselection(false)
                        showModeDialog = false
                    } catch (e: Exception) {
                        modeErrorMessage = ApiErrorParser.parseErrorMessage(e)
                    } finally {
                        isSwitchingMode = false
                    }
                }
            }
        )
    }
}
