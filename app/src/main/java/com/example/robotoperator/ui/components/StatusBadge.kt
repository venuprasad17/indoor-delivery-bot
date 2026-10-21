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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val cleanStatus = status.trim().uppercase()
    val (bgColor, textColor, borderColor) = when (cleanStatus) {
        "ONLINE", "OK", "TRACKING", "SAFE", "COMPLETED", "ACTIVE" ->
            Triple(StatusGreenBg, Color(0xFF166534), StatusGreenBorder)

        "PAUSED", "SLOW", "REPLAN", "RELOCATING", "WARNING", "WARN", "STALE" ->
            Triple(StatusAmberBg, Color(0xFF92400E), StatusAmberBorder)

        "EMERGENCY_STOP", "STOP", "LOST", "FAULT", "CANCELLED", "FAILED", "ERROR", "CRITICAL" ->
            Triple(StatusRedBg, StatusRed, StatusRedBorder)

        else ->
            Triple(StatusGreyBg, StatusGrey, StatusGreyBorder)
    }

    Box(
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .background(bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = cleanStatus.replace("_", " "),
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )
    }
}
