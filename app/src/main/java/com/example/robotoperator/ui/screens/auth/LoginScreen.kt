package com.example.robotoperator.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.theme.*
import kotlinx.coroutines.launch

enum class AuthMode {
    SIGN_IN,
    REGISTER
}

@Composable
fun LoginScreen(
    authManager: AuthManager,
    settingsStore: ServerSettingsStore,
    onNavigateServerSettings: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var authMode by remember { mutableStateOf(AuthMode.SIGN_IN) }

    // Sign in fields
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    // Register fields (name, email, password - no role selector per backend contract)
    var regName by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var showRegPassword by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var localValidationMessage by remember { mutableStateOf<String?>(null) }

    val authError by authManager.authError.collectAsState()
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceCanvas)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Column {
                    Text(
                        text = "Robot Operator",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900,
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        text = "Autonomous Indoor Delivery Fleet",
                        fontSize = 13.sp,
                        color = Slate500,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Server Target Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Slate100)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HOST: ${settingsStore.serverUrl}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Slate700,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "Edit",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ActionBlue,
                        modifier = Modifier
                            .clickable(onClick = onNavigateServerSettings)
                            .padding(4.dp)
                            .testTag("edit_host_button")
                    )
                }

                // Auth Mode Tabs: Sign In vs Register
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Slate100)
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (authMode == AuthMode.SIGN_IN) PureWhite else Slate100)
                            .clickable {
                                authMode = AuthMode.SIGN_IN
                                authManager.clearError()
                                localValidationMessage = null
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sign In",
                            fontSize = 13.sp,
                            fontWeight = if (authMode == AuthMode.SIGN_IN) FontWeight.Bold else FontWeight.Medium,
                            color = if (authMode == AuthMode.SIGN_IN) Slate900 else Slate500
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (authMode == AuthMode.REGISTER) PureWhite else Slate100)
                            .clickable {
                                authMode = AuthMode.REGISTER
                                authManager.clearError()
                                localValidationMessage = null
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Register",
                            fontSize = 13.sp,
                            fontWeight = if (authMode == AuthMode.REGISTER) FontWeight.Bold else FontWeight.Medium,
                            color = if (authMode == AuthMode.REGISTER) Slate900 else Slate500
                        )
                    }
                }

                // Display Error (Network or Validation)
                val displayedError = localValidationMessage ?: authError
                if (!displayedError.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(StatusRedBg)
                            .border(1.dp, StatusRedBorder, RoundedCornerShape(4.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = displayedError,
                            color = StatusRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                if (authMode == AuthMode.SIGN_IN) {
                    // Unified Sign In Form
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            authManager.clearError()
                            localValidationMessage = null
                        },
                        label = { Text("Email") },
                        placeholder = { Text("operator@example.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_email_input")
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            authManager.clearError()
                            localValidationMessage = null
                        },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password",
                                    tint = Slate500
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (email.isNotBlank() && password.isNotBlank() && !isLoading) {
                                    scope.launch {
                                        isLoading = true
                                        val ok = authManager.login(email = email, pass = password)
                                        isLoading = false
                                        if (ok) onLoginSuccess()
                                    }
                                }
                            }
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_password_input")
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                val ok = authManager.login(email = email, pass = password)
                                isLoading = false
                                if (ok) onLoginSuccess()
                            }
                        },
                        enabled = email.isNotBlank() && password.isNotBlank() && !isLoading,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("sign_in_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = PureWhite,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Sign In",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Need an account? ",
                            fontSize = 13.sp,
                            color = Slate600
                        )
                        Text(
                            text = "Register here",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ActionBlue,
                            modifier = Modifier
                                .clickable {
                                    authMode = AuthMode.REGISTER
                                    authManager.clearError()
                                    localValidationMessage = null
                                }
                                .padding(4.dp)
                                .testTag("switch_to_register_link")
                        )
                    }

                } else {
                    // Unified Register Form (email, password, name - no role selection)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(ActionBlueSubtle)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "The first account registered on the server becomes ADMIN. All subsequent registrations become standard USER accounts.",
                                fontSize = 11.sp,
                                color = ActionBlue,
                                lineHeight = 16.sp
                            )
                        }

                        OutlinedTextField(
                            value = regName,
                            onValueChange = {
                                regName = it
                                authManager.clearError()
                                localValidationMessage = null
                            },
                            label = { Text("Full Name") },
                            placeholder = { Text("Alex Rivera") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_name_input")
                        )

                        OutlinedTextField(
                            value = regEmail,
                            onValueChange = {
                                regEmail = it
                                authManager.clearError()
                                localValidationMessage = null
                            },
                            label = { Text("Work Email") },
                            placeholder = { Text("alex@example.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_email_input")
                        )

                        OutlinedTextField(
                            value = regPassword,
                            onValueChange = {
                                regPassword = it
                                authManager.clearError()
                                localValidationMessage = null
                            },
                            label = { Text("Password (min 6 characters)") },
                            singleLine = true,
                            visualTransformation = if (showRegPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showRegPassword = !showRegPassword }) {
                                    Icon(
                                        imageVector = if (showRegPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showRegPassword) "Hide password" else "Show password",
                                        tint = Slate500
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_password_input")
                        )

                        Button(
                            onClick = {
                                if (regName.isBlank()) {
                                    localValidationMessage = "Please enter your full name."
                                    return@Button
                                }
                                if (regEmail.isBlank() || !regEmail.contains("@")) {
                                    localValidationMessage = "Please enter a valid email address."
                                    return@Button
                                }
                                if (regPassword.length < 6) {
                                    localValidationMessage = "Password must be at least 6 characters long."
                                    return@Button
                                }

                                scope.launch {
                                    isLoading = true
                                    val ok = authManager.register(
                                        name = regName,
                                        email = regEmail,
                                        pass = regPassword
                                    )
                                    isLoading = false
                                    if (ok) onLoginSuccess()
                                }
                            },
                            enabled = regName.isNotBlank() && regEmail.isNotBlank() && regPassword.isNotBlank() && !isLoading,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("register_submit_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = PureWhite,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Register & Sign In",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Already have an account? ",
                                fontSize = 13.sp,
                                color = Slate600
                            )
                            Text(
                                text = "Sign in",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ActionBlue,
                                modifier = Modifier
                                    .clickable {
                                        authMode = AuthMode.SIGN_IN
                                        authManager.clearError()
                                        localValidationMessage = null
                                    }
                                    .padding(4.dp)
                                    .testTag("switch_to_signin_link")
                            )
                        }
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = BorderHairline)

                // Server settings link footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Configure Server Endpoint",
                        color = ActionBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable(onClick = onNavigateServerSettings)
                            .padding(8.dp)
                            .testTag("server_settings_link")
                    )
                }
            }
        }
    }
}
