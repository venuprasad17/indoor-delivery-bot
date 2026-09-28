package com.example.robotoperator.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.api.ApiErrorParser
import com.example.robotoperator.data.model.EStopRequest
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.robotoperator.state.AuthManager
import com.example.robotoperator.state.RobotStateHolder
import com.example.robotoperator.ui.components.StatusStrip
import com.example.robotoperator.ui.screens.dashboard.DashboardScreen
import com.example.robotoperator.ui.screens.deliveries.DeliveriesScreen
import com.example.robotoperator.ui.screens.deliveries.DeliveryDetailScreen
import com.example.robotoperator.ui.screens.destinations.DestinationsScreen
import com.example.robotoperator.ui.screens.mapping.MappingDetailScreen
import com.example.robotoperator.ui.screens.mapping.MappingListScreen
import com.example.robotoperator.ui.screens.more.EventsScreen
import com.example.robotoperator.ui.screens.more.MoreScreen
import com.example.robotoperator.ui.screens.more.UsersScreen
import com.example.robotoperator.ui.screens.server.ServerSettingsScreen
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainTab(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    MAPPING("Mapping", Icons.Default.Map),
    DESTINATIONS("Destinations", Icons.Default.Place),
    DELIVERIES("Deliveries", Icons.Default.LocalShipping),
    MORE("More", Icons.Default.MoreHoriz)
}

sealed interface SubScreen {
    data class MappingDetail(val sessionId: String) : SubScreen
    data class DeliveryDetail(val deliveryId: String) : SubScreen
    data object Events : SubScreen
    data object Users : SubScreen
    data object ServerSettings : SubScreen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    apiClient: ApiClient,
    robotStore: RobotStateHolder,
    settingsStore: ServerSettingsStore,
    authManager: AuthManager,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(MainTab.DASHBOARD) }
    var activeSubScreen by remember { mutableStateOf<SubScreen?>(null) }

    val robotStatus by robotStore.robotStatus.collectAsState()
    val isWsConnected by robotStore.isWsConnected.collectAsState()
    val scope = rememberCoroutineScope()

    // Handle back button on secondary screens
    BackHandler(enabled = activeSubScreen != null) {
        activeSubScreen = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Robot Operator",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate900
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureWhite)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = PureWhite,
                tonalElevation = 0.dp,
                modifier = Modifier.testTag("bottom_tab_bar")
            ) {
                MainTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab && activeSubScreen == null
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            selectedTab = tab
                            activeSubScreen = null
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            unselectedIconColor = Slate500,
                            unselectedTextColor = Slate500,
                            indicatorColor = PrimaryBlueLight
                        ),
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                    )
                }
            }
        },
        containerColor = Slate50
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Prominent Safety Simulation Mode UI indicator if simulated hardware active
            if (settingsStore.isSimulationMode) {
                Surface(
                    color = StatusAmberBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = StatusAmberText,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Safety Simulation Mode: Virtual Telemetry",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusAmberText
                            )
                        }
                        TextButton(
                            onClick = { activeSubScreen = SubScreen.ServerSettings },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Switch Server", fontSize = 11.sp, color = StatusAmberText)
                        }
                    }
                }
            }

            // Pinned Status Strip on every screen with immediate red E-STOP button
            StatusStrip(
                robotStatus = robotStatus,
                isWsConnected = isWsConnected,
                onEStopClick = {
                    // Fires immediately with no confirmation per spec
                    robotStore.updateSafetyState("EMERGENCY_STOP")
                    robotStore.updateMode("STOPPED")
                    scope.launch {
                        try {
                            val service = apiClient.getService()
                            val res = withContext(Dispatchers.IO) {
                                service.triggerEmergencyStop(EStopRequest("Operator manual E-STOP"))
                            }
                            robotStore.updateSafetyState(res.safety_state ?: "EMERGENCY_STOP")
                            if (res.needs_mode_reselection) {
                                robotStore.setNeedsModeReselection(true)
                            }
                        } catch (e: Exception) {
                            // Already reflected in store
                        }
                    }
                }
            )

            // Content Area: Either SubScreen or Active Tab
            Box(modifier = Modifier.fillMaxSize()) {
                when (val sub = activeSubScreen) {
                    is SubScreen.MappingDetail -> {
                        MappingDetailScreen(
                            sessionId = sub.sessionId,
                            apiClient = apiClient,
                            robotStore = robotStore,
                            onBack = { activeSubScreen = null }
                        )
                    }
                    is SubScreen.DeliveryDetail -> {
                        DeliveryDetailScreen(
                            deliveryId = sub.deliveryId,
                            apiClient = apiClient,
                            onBack = { activeSubScreen = null }
                        )
                    }
                    is SubScreen.Events -> {
                        EventsScreen(
                            apiClient = apiClient,
                            onBack = { activeSubScreen = null }
                        )
                    }
                    is SubScreen.Users -> {
                        UsersScreen(
                            apiClient = apiClient,
                            onBack = { activeSubScreen = null }
                        )
                    }
                    is SubScreen.ServerSettings -> {
                        ServerSettingsScreen(
                            settingsStore = settingsStore,
                            apiClient = apiClient,
                            onBack = { activeSubScreen = null },
                            onSaved = { activeSubScreen = null }
                        )
                    }
                    null -> {
                        when (selectedTab) {
                            MainTab.DASHBOARD -> DashboardScreen(
                                apiClient = apiClient,
                                robotStore = robotStore,
                                settingsStore = settingsStore,
                                onNavigateSessionDetail = { activeSubScreen = SubScreen.MappingDetail(it) },
                                onNavigateDeliveryDetail = { activeSubScreen = SubScreen.DeliveryDetail(it) }
                            )
                            MainTab.MAPPING -> MappingListScreen(
                                apiClient = apiClient,
                                robotStore = robotStore,
                                settingsStore = settingsStore,
                                onNavigateDetail = { activeSubScreen = SubScreen.MappingDetail(it) }
                            )
                            MainTab.DESTINATIONS -> DestinationsScreen(
                                apiClient = apiClient,
                                robotStore = robotStore
                            )
                            MainTab.DELIVERIES -> DeliveriesScreen(
                                apiClient = apiClient,
                                robotStore = robotStore,
                                settingsStore = settingsStore,
                                onNavigateDetail = { activeSubScreen = SubScreen.DeliveryDetail(it) }
                            )
                            MainTab.MORE -> MoreScreen(
                                authManager = authManager,
                                settingsStore = settingsStore,
                                onNavigateEvents = { activeSubScreen = SubScreen.Events },
                                onNavigateUsers = { activeSubScreen = SubScreen.Users },
                                onNavigateServerSettings = { activeSubScreen = SubScreen.ServerSettings },
                                onLogout = onLogout
                            )
                        }
                    }
                }
            }
        }
    }
}
