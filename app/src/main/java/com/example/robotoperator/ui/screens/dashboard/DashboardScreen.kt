package com.example.robotoperator.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.api.ApiErrorParser
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.robotoperator.state.RobotStateHolder
import com.example.robotoperator.ui.components.KeyValRow
import com.example.robotoperator.ui.components.PosePlotCanvas
import com.example.robotoperator.ui.components.StatusBadge
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    stateHolder: RobotStateHolder,
    apiClient: ApiClient,
    settingsStore: ServerSettingsStore,
    onOpenModeDialog: () -> Unit
) {
    val robotStatus by stateHolder.robotStatus.collectAsState()
    val currentPose by stateHolder.currentPose.collectAsState()
    val poseTrail by stateHolder.poseTrail.collectAsState()
    val isLiveConnected by stateHolder.isLiveConnected.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }
    var restError by remember { mutableStateOf<String?>(null) }
    var noRobotRegistered by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refreshStatus() {
        scope.launch {
            isRefreshing = true
            restError = null
            try {
                val status = apiClient.getService().getRobotStatus()
                noRobotRegistered = false
                stateHolder.updateRobotStatus(status)
                if (status.id.isNotBlank()) {
                    settingsStore.cachedRobotId = status.id
                }
            } catch (t: Throwable) {
                val parsed = ApiErrorParser.parse(t)
                if (parsed.errorCode == "NO_ROBOT_REGISTERED" || parsed.statusCode == 404) {
                    noRobotRegistered = true
                } else {
                    restError = parsed.message
                }
            } finally {
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshStatus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceCanvas)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!isLiveConnected) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(StatusAmberBg)
                    .border(1.dp, StatusAmberBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live updates disconnected. Polling mode.",
                        fontSize = 12.sp,
                        color = StatusAmber,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(
                        onClick = { refreshStatus() },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text("Retry", fontSize = 11.sp, color = ActionBlue)
                    }
                }
            }
        }

        if (noRobotRegistered) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(PureWhite)
                    .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No robot registered",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate900
                    )
                    Text(
                        text = "Register a robot on the backend to begin telemetry tracking.",
                        fontSize = 13.sp,
                        color = Slate500,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else if (!restError.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(StatusRedBg)
                    .border(1.dp, StatusRedBorder, RoundedCornerShape(4.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = restError ?: "Error fetching status",
                    color = StatusRed,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        PosePlotCanvas(
            currentPose = currentPose,
            poseTrail = poseTrail,
            modifier = Modifier.fillMaxWidth()
        )

        Card(
            shape = RoundedCornerShape(6.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ROBOT STATUS OVERVIEW",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Slate500
                    )
                    IconButton(
                        onClick = { refreshStatus() },
                        enabled = !isRefreshing,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("refresh_dashboard_button")
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = ActionBlue
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Slate600,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = BorderHairline)

                KeyValRow(
                    label = "Robot Name",
                    value = robotStatus.name.ifBlank { "Unregistered" }
                )
                KeyValRow(
                    label = "System State",
                    value = null,
                    trailingContent = {
                        StatusBadge(status = robotStatus.status)
                    }
                )
                KeyValRow(
                    label = "Control Mode",
                    value = null,
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .border(1.dp, ActionBlue.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .background(ActionBlueSubtle, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = robotStatus.control_mode,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = ActionBlue
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Change",
                                fontSize = 11.sp,
                                color = ActionBlue
                            )
                        }
                    },
                    onClick = onOpenModeDialog
                )
                KeyValRow(
                    label = "Safety State",
                    value = null,
                    trailingContent = {
                        StatusBadge(status = robotStatus.safety_state)
                    }
                )
                KeyValRow(
                    label = "SLAM State",
                    value = null,
                    trailingContent = {
                        StatusBadge(status = robotStatus.slam_state)
                    }
                )
                KeyValRow(
                    label = "Battery Level",
                    value = robotStatus.battery_level?.let { "${it.toInt()}%" } ?: "N/A",
                    isMonospace = true,
                    valueColor = when {
                        robotStatus.battery_level == null -> Slate500
                        robotStatus.battery_level!! < 20f -> StatusRed
                        robotStatus.battery_level!! < 50f -> StatusAmber
                        else -> StatusGreen
                    }
                )
                KeyValRow(
                    label = "Last Seen",
                    value = robotStatus.last_seen ?: "Never",
                    isMonospace = true
                )
                KeyValRow(
                    label = "Firmware Version",
                    value = robotStatus.firmware_version ?: "Unknown",
                    isMonospace = true,
                    showDivider = false
                )
            }
        }
    }
}
