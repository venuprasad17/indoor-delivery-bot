package com.example.robotoperator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*

@Composable
fun ModeSelectionDialog(
    currentMode: String,
    isRequiredReselection: Boolean = false,
    errorMessage: String? = null,
    isSubmitting: Boolean = false,
    onDismiss: () -> Unit,
    onConfirmMode: (selectedMode: String) -> Unit
) {
    var selectedMode by remember {
        mutableStateOf(if (currentMode == "AUTONOMOUS") "MANUAL" else "AUTONOMOUS")
    }
    var hasAcknowledged by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = {
        if (!isRequiredReselection && !isSubmitting) onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = PureWhite,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate300),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("mode_selection_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = if (isRequiredReselection) "Select Control Mode (Required)" else "Change Control Mode",
                    style = MaterialTheme.typography.titleMedium,
                    color = Slate900,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isRequiredReselection)
                        "Emergency stop was reset. Robot safety protocol requires explicit mode selection before resuming operations."
                    else
                        "Switching control mode affects autonomous navigation and safety velocity limits.",
                    fontSize = 13.sp,
                    color = Slate600
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Mode Options
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeOptionItem(
                        title = "MANUAL",
                        desc = "Teleoperation or manual joystick overrides",
                        isSelected = selectedMode == "MANUAL",
                        onClick = { selectedMode = "MANUAL" },
                        testTag = "mode_manual_option"
                    )

                    ModeOptionItem(
                        title = "AUTONOMOUS",
                        desc = "Automated waypoint navigation & mission planning",
                        isSelected = selectedMode == "AUTONOMOUS",
                        onClick = { selectedMode = "AUTONOMOUS" },
                        testTag = "mode_autonomous_option"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Acknowledgment Checkbox (Mandatory)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { hasAcknowledged = !hasAcknowledged }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = hasAcknowledged,
                        onCheckedChange = { hasAcknowledged = it },
                        modifier = Modifier.testTag("mode_ack_checkbox")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "I acknowledge the physical safety perimeter is clear",
                        fontSize = 13.sp,
                        color = Slate800
                    )
                }

                if (!errorMessage.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = StatusRedText,
                        fontSize = 12.sp,
                        modifier = Modifier.testTag("mode_error_text")
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isRequiredReselection) {
                        TextButton(
                            onClick = onDismiss,
                            enabled = !isSubmitting,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("cancel_mode_button")
                        ) {
                            Text("Cancel", color = Slate600)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Button(
                        onClick = { onConfirmMode(selectedMode) },
                        enabled = hasAcknowledged && !isSubmitting,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.testTag("confirm_mode_button")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = PureWhite
                            )
                        } else {
                            Text("Set to $selectedMode")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeOptionItem(
    title: String,
    desc: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val borderColor = if (isSelected) PrimaryBlue else Slate300
    val bgColor = if (isSelected) PrimaryBlueLight else PureWhite

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, shape = RoundedCornerShape(6.dp))
            .background(bgColor, shape = RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Slate900
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = Slate600
            )
        }
    }
}
