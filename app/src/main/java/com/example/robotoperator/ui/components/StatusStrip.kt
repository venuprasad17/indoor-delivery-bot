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
import com.example.ui.theme.*

@Composable
fun StatusStrip(
    robotStatus: RobotStatus,
    isLiveConnected: Boolean,
    onEStopClick: () -> Unit,
    onModeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOnline = robotStatus.status.equals("ONLINE", ignoreCase = true)
    val mode = robotStatus.control_mode.ifBlank { "MANUAL" }
    val safety = robotStatus.safety_state.ifBlank { "SAFE" }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Slate900)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Group: Online indicator + Live status + Control Mode + Safety
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                // Online/Offline pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            if (isOnline) StatusGreen.copy(alpha = 0.2f) else StatusGrey.copy(alpha = 0.2f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) StatusGreen else StatusGrey)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isOnline) "ONLINE" else "OFFLINE",
                        color = PureWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Control Mode (clickable to open switch sheet)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onModeClick)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .testTag("mode_selector_button")
                ) {
                    Text(
                        text = "MODE:",
                        color = Slate400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
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

                // Safety State
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    Text(
                        text = safety,
                        color = when (safety.uppercase()) {
                            "SAFE" -> StatusGreen
                            "SLOW", "REPLAN" -> StatusAmber
                            "EMERGENCY_STOP" -> StatusRed
                            else -> Slate400
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Live WS indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (isLiveConnected) StatusGreen else StatusAmber)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isLiveConnected) "LIVE" else "DISCONNECTED",
                        color = if (isLiveConnected) Slate400 else StatusAmber,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Right Group: Pinned E-STOP button (always reachable, fires immediately with no confirmation)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .defaultMinSize(minWidth = 72.dp, minHeight = 48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(StatusRed)
                    .clickable(onClick = onEStopClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("e_stop_pinned_button")
            ) {
                Text(
                    text = "E-STOP",
                    color = PureWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
