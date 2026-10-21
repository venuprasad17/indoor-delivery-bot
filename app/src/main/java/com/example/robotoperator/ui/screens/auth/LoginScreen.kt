package com.example.robotoperator.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.robotoperator.state.AuthManager
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authManager: AuthManager,
    settingsStore: ServerSettingsStore,
    onNavigateServerSettings: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var selectedRole by rememberSaveable { mutableStateOf<String?>(null) }
    var registering by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val authError by authManager.authError.collectAsState()
    val scope = rememberCoroutineScope()
    val isAdmin = selectedRole == "ADMIN"

    fun chooseRole(role: String?) {
        if (loading) return
        selectedRole = role
        registering = false
        email = ""
        name = ""
        password = ""
        showPassword = false
        validationError = null
        authManager.clearError()
    }

    fun submit() {
        val role = selectedRole ?: return
        if (loading) return
        validationError = when {
            !settingsStore.isConfigured -> "Your organization’s connection has not been set up. Ask your administrator to open Connection settings."
            email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email address."
            password.isBlank() -> "Enter your password."
            registering && name.isBlank() -> "Enter your full name."
            registering && password.length < 6 -> "Use at least 6 characters for your password."
            else -> null
        }
        if (validationError != null) return
        loading = true
        scope.launch {
            try {
                val success = if (registering && role == "USER") {
                    authManager.register(name, email, password)
                } else {
                    authManager.login(email, password, expectedRole = role)
                }
                if (success) onLoginSuccess()
            } finally { loading = false }
        }
    }

    BackHandler(enabled = selectedRole != null) { if (!loading) chooseRole(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().imePadding().padding(20.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text("Robot Operator", style = MaterialTheme.typography.headlineLarge)
                Text(
                    if (selectedRole == null) "Welcome. Choose how you use the app." else if (isAdmin) "Admin login" else if (registering) "Create your user account" else "User login",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.testTag("auth_heading")
                )
                if (selectedRole == null) {
                    Text("Use your account to access the tools meant for you.", style = MaterialTheme.typography.bodyLarge)
                    ElevatedCard(onClick = { chooseRole("USER") }, modifier = Modifier.fillMaxWidth().testTag("choose_user_login")) {
                        Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("User", style = MaterialTheme.typography.titleLarge)
                                Text("View your deliveries and confirm package receipt.", style = MaterialTheme.typography.bodyMedium)
                                Text("Continue as user", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    ElevatedCard(onClick = { chooseRole("ADMIN") }, modifier = Modifier.fillMaxWidth().testTag("choose_admin_login")) {
                        Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Admin", style = MaterialTheme.typography.titleLarge)
                                Text("Manage robots, maps, destinations, deliveries and accounts.", style = MaterialTheme.typography.bodyMedium)
                                Text("Continue as admin", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else {
                    Text(
                        if (isAdmin) "Sign in with an administrator account provided by your organization." else "Sign in to view and manage your deliveries.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    (validationError ?: authError)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("auth_error"))
                    }
                    if (registering && !isAdmin) OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("Full name") },
                        enabled = !loading, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("register_name_input")
                    )
                    OutlinedTextField(
                        value = email, onValueChange = { email = it; validationError = null; authManager.clearError() },
                        label = { Text("Email address") }, enabled = !loading, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth().testTag("login_email_input")
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it; validationError = null; authManager.clearError() },
                        label = { Text("Password") }, enabled = !loading, singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (showPassword) "Hide password" else "Show password")
                        } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth().testTag("login_password_input")
                    )
                    Button(onClick = { submit() }, enabled = !loading, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("sign_in_button")) {
                        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(if (registering) "Create user account" else if (isAdmin) "Sign in as admin" else "Sign in as user")
                    }
                    if (!isAdmin) TextButton(enabled = !loading, onClick = {
                        registering = !registering
                        password = ""
                        validationError = null
                        authManager.clearError()
                    }) { Text(if (registering) "Already registered? Sign in" else "New user? Create an account") }
                    if (isAdmin) Text("Admin accounts are created by an authorized administrator. Contact your organization if you need access.", style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = !loading, onClick = { chooseRole(null) }) { Text("Change account type") }
                }
                HorizontalDivider()
                TextButton(onClick = onNavigateServerSettings, enabled = !loading, modifier = Modifier.testTag("server_settings_link")) {
                    Text("Connection settings")
                }
            }
        }
    }
}
