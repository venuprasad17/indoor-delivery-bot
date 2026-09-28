package com.example.robotoperator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

enum class BadgeColorType {
    GREEN,
    AMBER,
    RED,
    GREY
}

@Composable
fun StatusBadge(
    text: String,
    type: BadgeColorType,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val (bg, border, textColor) = when (type) {
        BadgeColorType.GREEN -> Triple(StatusGreenBg, StatusGreenBorder, StatusGreenText)
        BadgeColorType.AMBER -> Triple(StatusAmberBg, StatusAmberBorder, StatusAmberText)
        BadgeColorType.RED -> Triple(StatusRedBg, StatusRedBorder, StatusRedText)
        BadgeColorType.GREY -> Triple(StatusGreyBg, StatusGreyBorder, StatusGreyText)
    }

    Box(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .background(bg, shape = RoundedCornerShape(4.dp))
            .border(1.dp, border, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )
    }
}

fun getStatusBadgeType(status: String?): BadgeColorType {
    return when (status?.uppercase()) {
        "ONLINE", "SAFE", "TRACKING", "MAPPING", "AUTONOMOUS", "DELIVERED", "HOME" -> BadgeColorType.GREEN
        "PAUSED", "SLOW", "REPLAN", "RELOCATING", "MANUAL", "GO_TO_DESTINATION", "RETURN_HOME" -> BadgeColorType.AMBER
        "EMERGENCY_STOP", "LOST", "STOPPED", "ERROR", "FAULT", "CRITICAL" -> BadgeColorType.RED
        else -> BadgeColorType.GREY
    }
}
