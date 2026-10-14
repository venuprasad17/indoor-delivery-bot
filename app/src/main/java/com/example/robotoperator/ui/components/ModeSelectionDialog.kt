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
import androidx.compose.ui.draw.clip
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
    errorMessage: String?,
    isSwitching: Boolean,
    onDismiss: () -> Unit,
    onConfirmMode: (targetMode: String) -> Unit
) {
    var selectedMode by remember { mutableStateOf(currentMode.uppercase()) }
    var acknowledgedMovement by remember { mutableStateOf(false) }

    val requiresAcknowledgment = selectedMode == "AUTONOMOUS" || selectedMode == "MANUAL"
    val canSubmit = (!requiresAcknowledgment || acknowledgedMovement) && !isSwitching

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(6.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Select Control Mode",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate900
                )
                Text(
                    text = "Current mode: $currentMode",
                    fontSize = 12.sp,
                    color = Slate500,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                )

                // Modes List: MANUAL, AUTONOMOUS per backend contract
                listOf(
                    Triple("MANUAL", "Manual remote teleoperation", Slate700),
                    Triple("AUTONOMOUS", "Autonomous SLAM navigation & dispatch", ActionBlue)
                ).forEach { (mode, desc, color) ->
                    val isSelected = selectedMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) ActionBlue else BorderHairline,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .background(if (isSelected) ActionBlueSubtle else PureWhite)
                            .clickable {
                                selectedMode = mode
                                acknowledgedMovement = false
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                selectedMode = mode
                                acknowledgedMovement = false
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = ActionBlue)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = mode,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (isSelected) Slate900 else color
                            )
                            Text(
                                text = desc,
                                fontSize = 12.sp,
                                color = Slate500
                            )
                        }
                    }
                }

                // Movement acknowledgment checkbox
                if (requiresAcknowledgment) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { acknowledgedMovement = !acknowledgedMovement }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = acknowledgedMovement,
                            onCheckedChange = { acknowledgedMovement = it },
                            colors = CheckboxDefaults.colors(checkedColor = ActionBlue),
                            modifier = Modifier.testTag("ack_movement_checkbox")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "I acknowledge the robot will move",
                            fontSize = 13.sp,
                            color = Slate800,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Error message inline
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StatusRedBg, RoundedCornerShape(4.dp))
                            .border(1.dp, StatusRedBorder, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = errorMessage,
                            color = StatusRed,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Cancel", color = Slate600)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirmMode(selectedMode) },
                        enabled = canSubmit,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("confirm_mode_button")
                    ) {
                        if (isSwitching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = PureWhite,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Apply Mode")
                        }
                    }
                }
            }
        }
    }
}
