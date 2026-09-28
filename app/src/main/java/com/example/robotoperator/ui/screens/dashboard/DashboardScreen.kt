package com.example.robotoperator.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.api.ApiErrorParser
import com.example.robotoperator.data.model.EStopRequest
import com.example.robotoperator.data.model.ModeChangeRequest
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.robotoperator.state.RobotStateHolder
import com.example.robotoperator.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(
    apiClient: ApiClient,
    robotStore: RobotStateHolder,
    settingsStore: ServerSettingsStore,
    onNavigateSessionDetail: (String) -> Unit,
    onNavigateDeliveryDetail: (String) -> Unit
) {
    val robotStatus by robotStore.robotStatus.collectAsState()
    val currentPose by robotStore.currentPose.collectAsState()
    val poseTrail by robotStore.poseTrail.collectAsState()
    val isWsConnected by robotStore.isWsConnected.collectAsState()
    val activeSessionId by robotStore.activeSessionId.collectAsState()
    val activeDeliveryId by robotStore.activeDeliveryId.collectAsState()
    val needsModeReselection by robotStore.needsModeReselection.collectAsState()

    val scope = rememberCoroutineScope()

    var showModeDialog by remember { mutableStateOf(false) }
    var isChangingMode by remember { mutableStateOf(false) }
    var modeDialogError by remember { mutableStateOf<String?>(null) }
    var isResettingEStop by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    // Fetch initial status on mount
    LaunchedEffect(Unit) {
        try {
            val service = apiClient.getService()
            val status = withContext(Dispatchers.IO) { service.getRobotStatus() }
            robotStore.updateRobotStatus(status)
            if (status.id.isNotEmpty()) {
                settingsStore.cachedRobotId = status.id
            }
        } catch (e: Exception) {
            val err = ApiErrorParser.parse(e)
            actionError = err.message
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .testTag("dashboard_screen")
    ) {
        // Persistent Emergency Stop Banner
        if (robotStatus.safety_state == "EMERGENCY_STOP") {
            EStopBanner(
                isResetting = isResettingEStop,
                onResetClick = {
                    isResettingEStop = true
                    actionError = null
                    scope.launch {
                        try {
                            val service = apiClient.getService()
                            val resetRes = withContext(Dispatchers.IO) {
                                service.resetEmergencyStop()
                            }
                            robotStore.updateSafetyState(resetRes.safety_state ?: "SAFE")
                            if (resetRes.needs_mode_reselection) {
                                robotStore.setNeedsModeReselection(true)
                                showModeDialog = true
                            }
                        } catch (e: Exception) {
                            val err = ApiErrorParser.parse(e)
                            actionError = err.message
                        } finally {
                            isResettingEStop = false
                        }
                    }
                }
            )
        }

        // Live connection warning bar if WS is down
        if (!isWsConnected) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate200)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Live updates disconnected",
                    color = Slate700,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Main Content Scrollable
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error banner if any action failed
            if (!actionError.isNullOrEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StatusRedBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusRedBorder),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = actionError!!,
                            color = StatusRedText,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { actionError = null },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Dismiss", color = StatusRedText, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Robot State Metrics Card
            Card(
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header with Mode switch button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Robot Telemetry",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate900
                        )

                        // Mode Switch Button (disabled if ws down or estop)
                        OutlinedButton(
                            onClick = {
                                modeDialogError = null
                                showModeDialog = true
                            },
                            enabled = isWsConnected && robotStatus.safety_state != "EMERGENCY_STOP",
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("change_mode_button")
                        ) {
                            Text("Change mode", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = Slate200, thickness = 1.dp)

                    KeyValRow(label = "Robot name", value = robotStatus.name)
                    KeyValRow(
                        label = "Status",
                        value = robotStatus.status,
                        trailingBadge = {
                            StatusBadge(
                                text = robotStatus.status,
                                type = if (robotStatus.status == "ONLINE") BadgeColorType.GREEN else BadgeColorType.GREY
                            )
                        }
                    )
                    KeyValRow(label = "Last seen", value = robotStatus.last_seen ?: "Never")
                    KeyValRow(label = "Firmware", value = robotStatus.firmware_version ?: "Unknown")
                    KeyValRow(
                        label = "Battery",
                        value = if (robotStatus.battery_level != null)
                            "${robotStatus.battery_level!!.toInt()}%" else "Not reported"
                    )
                    KeyValRow(
                        label = "Control mode",
                        value = robotStatus.control_mode,
                        trailingBadge = {
                            StatusBadge(
                                text = robotStatus.control_mode,
                                type = when (robotStatus.control_mode) {
                                    "AUTONOMOUS" -> BadgeColorType.GREEN
                                    "MANUAL" -> BadgeColorType.AMBER
                                    else -> BadgeColorType.GREY
                                }
                            )
                        }
                    )
                    KeyValRow(
                        label = "SLAM state",
                        value = robotStatus.slam_state,
                        trailingBadge = {
                            StatusBadge(
                                text = robotStatus.slam_state,
                                type = when (robotStatus.slam_state) {
                                    "TRACKING" -> BadgeColorType.GREEN
                                    "LOST" -> BadgeColorType.RED
                                    "RELOCATING" -> BadgeColorType.AMBER
                                    else -> BadgeColorType.GREY
                                }
                            )
                        }
                    )
                    KeyValRow(
                        label = "Safety state",
                        value = robotStatus.safety_state,
                        trailingBadge = {
                            StatusBadge(
                                text = robotStatus.safety_state,
                                type = when (robotStatus.safety_state) {
                                    "SAFE" -> BadgeColorType.GREEN
                                    "SLOW", "REPLAN" -> BadgeColorType.AMBER
                                    "EMERGENCY_STOP" -> BadgeColorType.RED
                                    else -> BadgeColorType.GREY
                                }
                            )
                        }
                    )
                    KeyValRow(
                        label = "Current pose",
                        value = "(${currentPose.formattedX()}, ${currentPose.formattedY()}, ${currentPose.formattedTheta()} rad)",
                        isMonospace = true
                    )
                    KeyValRow(
                        label = "Active mapping session",
                        value = activeSessionId?.take(8) ?: "None",
                        valueColor = if (activeSessionId != null) PrimaryBlue else Slate500,
                        onClick = if (activeSessionId != null) {
                            { onNavigateSessionDetail(activeSessionId!!) }
                        } else null,
                        testTag = "row_active_session"
                    )
                    KeyValRow(
                        label = "Active delivery",
                        value = activeDeliveryId?.take(8) ?: "None",
                        valueColor = if (activeDeliveryId != null) PrimaryBlue else Slate500,
                        onClick = if (activeDeliveryId != null) {
                            { onNavigateDeliveryDetail(activeDeliveryId!!) }
                        } else null,
                        testTag = "row_active_delivery"
                    )
                }
            }

            // 2D Pose Plot
            PosePlotCanvas(
                currentPose = currentPose,
                poseTrail = poseTrail,
                heightDp = 260
            )
        }
    }

    // Mode Selection Dialog
    if (showModeDialog) {
        ModeSelectionDialog(
            currentMode = robotStatus.control_mode,
            isRequiredReselection = needsModeReselection,
            errorMessage = modeDialogError,
            isSubmitting = isChangingMode,
            onDismiss = {
                if (!needsModeReselection) {
                    showModeDialog = false
                    modeDialogError = null
                }
            },
            onConfirmMode = { newMode ->
                isChangingMode = true
                modeDialogError = null
                scope.launch {
                    try {
                        val service = apiClient.getService()
                        val res = withContext(Dispatchers.IO) {
                            service.setRobotMode(ModeChangeRequest(newMode, acknowledgment = true))
                        }
                        if (res.isSuccessful) {
                            robotStore.updateMode(newMode)
                            robotStore.setNeedsModeReselection(false)
                            showModeDialog = false
                        } else {
                            val parsed = ApiErrorParser.parseHttpError(res.code(), res.errorBody()?.string())
                            modeDialogError = parsed.message
                        }
                    } catch (e: Exception) {
                        val parsed = ApiErrorParser.parse(e)
                        modeDialogError = parsed.message
                    } finally {
                        isChangingMode = false
                    }
                }
            }
        )
    }
}
