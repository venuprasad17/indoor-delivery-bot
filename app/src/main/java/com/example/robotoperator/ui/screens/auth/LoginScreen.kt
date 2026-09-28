package com.example.robotoperator.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.robotoperator.state.AuthManager
import com.example.robotoperator.state.AuthState
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authManager: AuthManager,
    settingsStore: ServerSettingsStore,
    onNavigateServerSettings: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var email by remember { mutableStateOf(settingsStore.lastLoginEmail) }
    var password by remember { mutableStateOf("admin123") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSimMode by remember { mutableStateOf(settingsStore.isSimulationMode) }

    val authState by authManager.authState.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Authenticated -> onLoginSuccess()
            is AuthState.AdminForbidden -> errorMessage = state.message
            is AuthState.AuthError -> errorMessage = state.message
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .testTag("login_card"),
            shape = RoundedCornerShape(6.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Robot Operator",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate900
                    )
                    Text(
                        text = "Sign in with administrator credentials",
                        fontSize = 13.sp,
                        color = Slate500
                    )
                }

                // Backend Selector (Live Backend vs Safety Simulation Mode)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Slate100, shape = RoundedCornerShape(4.dp))
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (!isSimMode) PureWhite else Slate100,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable {
                                isSimMode = false
                                settingsStore.isSimulationMode = false
                                errorMessage = null
                            }
                            .padding(vertical = 6.dp)
                            .testTag("mode_live_server"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Live Server",
                            fontSize = 12.sp,
                            fontWeight = if (!isSimMode) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (!isSimMode) Slate900 else Slate600
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isSimMode) PureWhite else Slate100,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable {
                                isSimMode = true
                                settingsStore.isSimulationMode = true
                                errorMessage = null
                            }
                            .padding(vertical = 6.dp)
                            .testTag("mode_simulation"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Simulation Mode",
                            fontSize = 12.sp,
                            fontWeight = if (isSimMode) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSimMode) PrimaryBlue else Slate600
                        )
                    }
                }

                HorizontalDivider(color = Slate200, thickness = 1.dp)

                // Email
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Email",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Slate700
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            settingsStore.lastLoginEmail = it.trim()
                            errorMessage = null
                        },
                        singleLine = true,
                        placeholder = { Text("admin@example.com") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_input"),
                        shape = RoundedCornerShape(6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = Slate300
                        )
                    )
                }

                // Password
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Password",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Slate700
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (email.isNotBlank() && !isSubmitting) {
                                    isSubmitting = true
                                    settingsStore.lastLoginEmail = email.trim()
                                    scope.launch {
                                        authManager.login(email, password)
                                        isSubmitting = false
                                    }
                                }
                            }
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.testTag("toggle_password_visibility")
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = Slate500
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input"),
                        shape = RoundedCornerShape(6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = Slate300
                        )
                    )
                }

                // Inline Error and Simulation Mode Fallback Button
                if (!errorMessage.isNullOrEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StatusRedBg, shape = RoundedCornerShape(4.dp))
                            .border(1.dp, StatusRedBorder, shape = RoundedCornerShape(4.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = StatusRedText,
                            fontSize = 13.sp,
                            modifier = Modifier.testTag("login_error_text")
                        )

                        // If server cannot be reached, offer direct one-click Simulation Mode
                        if (errorMessage!!.contains("Cannot reach server", ignoreCase = true) || !isSimMode) {
                            Button(
                                onClick = {
                                    isSimMode = true
                                    settingsStore.isSimulationMode = true
                                    settingsStore.lastLoginEmail = email.trim()
                                    errorMessage = null
                                    isSubmitting = true
                                    scope.launch {
                                        authManager.login(email, password)
                                        isSubmitting = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PureWhite,
                                    contentColor = PrimaryBlue
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .testTag("start_simulation_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sign in with Safety Simulation Mode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Sign in Button
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        isSubmitting = true
                        errorMessage = null
                        settingsStore.lastLoginEmail = email.trim()
                        scope.launch {
                            val res = authManager.login(email, password)
                            if (res.isFailure) {
                                errorMessage = res.exceptionOrNull()?.message ?: "Sign in failed"
                            }
                            isSubmitting = false
                        }
                    },
                    enabled = !isSubmitting && email.isNotBlank(),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sign_in_button")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = PureWhite
                        )
                    } else {
                        Text(
                            text = if (isSimMode) "Sign in (Simulation Mode)" else "Sign in",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Target Server & Server Settings Link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSimMode) "Target: Simulation Engine" else "Target: ${settingsStore.serverUrl}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Slate500,
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(
                        onClick = onNavigateServerSettings,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.testTag("server_settings_link")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Server settings",
                            color = PrimaryBlue,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
