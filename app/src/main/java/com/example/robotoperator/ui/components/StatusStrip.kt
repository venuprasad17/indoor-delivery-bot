package com.example.robotoperator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.robotoperator.data.model.RobotStatus
import com.example.robotoperator.data.model.normalizedConnectionState
import com.example.robotoperator.data.model.safetyTelemetryContext
import com.example.robotoperator.data.model.safetyTelemetryLabel
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusStrip(
    robotStatus: RobotStatus,
    isLiveConnected: Boolean,
    canAdministerRobot: Boolean,
    onEStopClick: () -> Unit,
    onModeClick: () -> Unit,
    onOpenWifiClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val connectionState = normalizedConnectionState(robotStatus.connection_state)
    val mode = robotStatus.control_mode.ifBlank { "MANUAL" }
    val safety = safetyTelemetryLabel(robotStatus.safety_state)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Slate900)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status and Mode Pills
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                // Online/Offline pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            when (connectionState) {
                                "ONLINE" -> StatusGreen.copy(alpha = 0.2f)
                                "STALE" -> StatusAmber.copy(alpha = 0.2f)
                                else -> StatusGrey.copy(alpha = 0.2f)
                            },
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(when (connectionState) {
                                "ONLINE" -> StatusGreen
                                "STALE" -> StatusAmber
                                else -> StatusGrey
                            })
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = connectionState,
                        color = PureWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Control Mode (clickable to open switch sheet)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Slate800, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = canAdministerRobot, role = androidx.compose.ui.semantics.Role.Button, onClick = onModeClick)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("mode_selector_button")
                ) {
                    Text(
                        text = "MODE:",
                        color = Slate400,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = mode,
                        color = when (mode.uppercase()) {
                            "AUTONOMOUS" -> ActionBlueSubtle
                            "STOPPED" -> StatusAmber
                            else -> PureWhite
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Wi-Fi Quick Access Pill
                if (canAdministerRobot && onOpenWifiClick != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Slate800, RoundedCornerShape(4.dp))
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpenWifiClick)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("open_robot_wifi_settings")
                    ) {
                        Text(
                            text = "WI-FI",
                            color = Slate300,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Live Feed indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (isLiveConnected) StatusGreen else StatusAmber)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isLiveConnected) "LIVE" else "OFFLINE",
                        color = if (isLiveConnected) Slate400 else StatusAmber,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Right Group: administrator-only E-STOP button
            if (canAdministerRobot) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 84.dp, minHeight = 36.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(StatusRed)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onEStopClick)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("e_stop_pinned_button")
                ) {
                    Text(
                        text = "REQ E-STOP",
                        color = PureWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Text(
            text = "Last seen: ${robotStatus.last_seen ?: "unknown"}. ${safetyTelemetryContext(connectionState, isLiveConnected)}",
            color = Slate400,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
        )
    }
}
