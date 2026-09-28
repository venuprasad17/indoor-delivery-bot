package com.example.robotoperator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    isWsConnected: Boolean,
    onEStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("status_strip"),
        color = Slate100,
        border = null
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Status Indicators Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // Robot Online/Offline
                    StatusBadge(
                        text = robotStatus.status,
                        type = if (robotStatus.status == "ONLINE") BadgeColorType.GREEN else BadgeColorType.GREY,
                        testTag = "strip_status_badge"
                    )

                    // Control Mode
                    StatusBadge(
                        text = robotStatus.control_mode,
                        type = when (robotStatus.control_mode) {
                            "AUTONOMOUS" -> BadgeColorType.GREEN
                            "MANUAL" -> BadgeColorType.AMBER
                            else -> BadgeColorType.GREY
                        },
                        testTag = "strip_mode_badge"
                    )

                    // Safety State
                    StatusBadge(
                        text = robotStatus.safety_state,
                        type = when (robotStatus.safety_state) {
                            "SAFE" -> BadgeColorType.GREEN
                            "SLOW", "REPLAN" -> BadgeColorType.AMBER
                            "EMERGENCY_STOP" -> BadgeColorType.RED
                            else -> BadgeColorType.GREY
                        },
                        testTag = "strip_safety_badge"
                    )

                    // WS Connection Dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isWsConnected) StatusGreenText else Slate400)
                            .testTag("strip_ws_indicator")
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Pinned red E-STOP button - fires immediately with no confirmation
                Button(
                    onClick = onEStopClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EStopRed,
                        contentColor = PureWhite
                    ),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("estop_button")
                ) {
                    Text(
                        text = "E-STOP",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            HorizontalDivider(color = Slate300, thickness = 1.dp)
        }
    }
}
