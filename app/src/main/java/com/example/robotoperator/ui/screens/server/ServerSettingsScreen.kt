package com.example.robotoperator.ui.screens.server

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.api.ApiErrorParser
import com.example.robotoperator.data.prefs.BackendEnvironment
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    settingsStore: ServerSettingsStore,
    apiClient: ApiClient,
    canNavigateBack: Boolean,
    allowEndpointChanges: Boolean = true,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit
) {
    var selectedEnvironment by rememberSaveable { mutableStateOf(settingsStore.selectedEnvironment) }
    var urlInput by rememberSaveable {
        mutableStateOf(selectedEnvironment?.let(settingsStore::endpointFor).orEmpty())
    }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val scope = rememberCoroutineScope()
    val normalizedEndpoint = selectedEnvironment?.let { environment ->
        runCatching { settingsStore.validateEndpoint(urlInput, environment) }.getOrNull()
    }
    val endpointError = when {
        selectedEnvironment == null -> "Choose an environment before configuring an endpoint."
        urlInput.isBlank() -> "Enter an HTTPS backend endpoint."
        normalizedEndpoint == null -> runCatching {
            settingsStore.validateEndpoint(urlInput, selectedEnvironment!!)
        }.exceptionOrNull()?.message ?: "Endpoint is invalid for this environment."
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Server Settings",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate900
                    )
                },
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Slate800
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PureWhite,
                    titleContentColor = Slate900
                )
            )
        },
        containerColor = SurfaceCanvas
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(6.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Backend Environment",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Slate900
                    )

                    Text(
                        text = "No environment is used until you select it and save an endpoint. Production is never selected automatically.",
                        fontSize = 12.sp,
                        color = Slate500,
                        lineHeight = 16.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BackendEnvironment.entries.forEach { environment ->
                            FilterChip(
                                selected = selectedEnvironment == environment,
                                onClick = {
                                    selectedEnvironment = environment
                                    urlInput = settingsStore.endpointFor(environment).orEmpty()
                                    testResult = null
                                },
                                enabled = allowEndpointChanges,
                                label = { Text(environment.label) },
                                modifier = Modifier.testTag("environment_${environment.name.lowercase()}_chip")
                            )
                        }
                    }

                    selectedEnvironment?.let { environment ->
                        Text(
                            text = when (environment) {
                                BackendEnvironment.DEVELOPMENT -> "HTTPS is preferred. HTTP is allowed for Development."
                                BackendEnvironment.STAGING -> "Staging endpoints must use HTTPS."
                                BackendEnvironment.PRODUCTION -> "Production endpoints must use HTTPS. Saving this selection enables production access."
                            },
                            fontSize = 12.sp,
                            color = Slate500,
                            lineHeight = 16.sp
                        )
                    }

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = {
                            urlInput = it
                            testResult = null
                        },
                        enabled = allowEndpointChanges && selectedEnvironment != null,
                        label = { Text("${selectedEnvironment?.label ?: "Backend"} endpoint") },
                        placeholder = {
                            Text(
                                if (selectedEnvironment == BackendEnvironment.DEVELOPMENT)
                                    "https://dev.example or http://192.168.1.100:8000"
                                else "https://backend.example"
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_url_input")
                    )

                    if (endpointError != null) {
                        Text(
                            text = endpointError,
                            color = StatusRed,
                            fontSize = 12.sp
                        )
                    }

                    if (!allowEndpointChanges) {
                        Text(
                            text = "Sign out before changing backend environments. This prevents an authenticated session from being sent to a different server.",
                            color = StatusAmber,
                            fontSize = 12.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isTesting = true
                                testResult = null
                                try {
                                    val environment = requireNotNull(selectedEnvironment)
                                    settingsStore.saveEndpoint(environment, urlInput)
                                    val health = apiClient.getService().checkHealth()
                                    if (health.status == "ok") {
                                        testResult = Pair(true, "Connected successfully (status: ok)")
                                    } else {
                                        testResult = Pair(false, "Server status: ${health.status}")
                                    }
                                } catch (t: Throwable) {
                                    val parsed = ApiErrorParser.parse(t)
                                    testResult = Pair(false, parsed.message)
                                } finally {
                                    isTesting = false
                                }
                            }
                        },
                        enabled = allowEndpointChanges && normalizedEndpoint != null && !isTesting,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("test_connection_button")
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = ActionBlue
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing connection...")
                        } else {
                            Text("Test connection", color = ActionBlue)
                        }
                    }

                    testResult?.let { (success, msg) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (success) StatusGreenBg else StatusRedBg)
                                .border(
                                    1.dp,
                                    if (success) StatusGreenBorder else StatusRedBorder,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = if (success) "Success" else "Error",
                                tint = if (success) StatusGreen else StatusRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = msg,
                                color = if (success) StatusGreen else StatusRed,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val environment = selectedEnvironment ?: return@Button
                    settingsStore.saveEndpoint(environment, urlInput)
                    onSaved()
                },
                enabled = allowEndpointChanges && normalizedEndpoint != null,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("save_server_button")
            ) {
                Text(
                    text = "Save & Continue",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
