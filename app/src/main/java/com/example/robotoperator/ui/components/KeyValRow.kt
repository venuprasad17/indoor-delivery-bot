package com.example.robotoperator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate900

@Composable
fun KeyValRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isMonospace: Boolean = false,
    valueColor: Color = Slate900,
    trailingBadge: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    testTag: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Slate500,
                fontSize = 14.sp
            )

            if (trailingBadge != null) {
                trailingBadge()
            } else {
                Text(
                    text = value,
                    color = valueColor,
                    fontSize = 14.sp,
                    fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default
                )
            }
        }
    }
    HorizontalDivider(
        color = Slate200,
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}
