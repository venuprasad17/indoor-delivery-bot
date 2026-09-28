package com.example.robotoperator.ui.screens.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.api.ApiErrorParser
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    settingsStore: ServerSettingsStore,
    apiClient: ApiClient,
    onBack: (() -> Unit)? = null,
    onSaved: () -> Unit
) {
    var serverAddress by remember { mutableStateOf(settingsStore.serverUrl) }
    var isSimMode by remember { mutableStateOf(settingsStore.isSimulationMode) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Server settings",
                        style = MaterialTheme.typography.titleMedium,
                        color = Slate900
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("server_settings_back")) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Slate700
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureWhite)
            )
        },
        containerColor = Slate50
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Safety Simulation Mode Toggle Card
            Card(
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Safety Simulation Mode",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate900
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Uses embedded local robot engine for testing when physical robot is offline.",
                            fontSize = 12.sp,
                            color = Slate600
                        )
                    }

                    Switch(
                        checked = isSimMode,
                        onCheckedChange = {
                            isSimMode = it
                            testResult = null
                        },
                        modifier = Modifier.testTag("toggle_simulation_mode")
                    )
                }
            }

            Text(
                text = "Backend Connection",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Slate500,
                letterSpacing = 0.5.sp
            )

            // Server Address Input Card
            Card(
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Server address",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Slate900
                    )

                    OutlinedTextField(
                        value = serverAddress,
                        onValueChange = {
                            serverAddress = it
                            testResult = null
                        },
                        placeholder = { Text("http://10.0.2.2:8000") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_address_input"),
                        shape = RoundedCornerShape(6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = Slate300,
                            focusedTextColor = Slate900,
                            unfocusedTextColor = Slate900
                        )
                    )

                    Text(
                        text = "Tip: Use the computer's LAN IP or ngrok tunnel, not localhost; Android emulator uses 10.0.2.2.",
                        fontSize = 13.sp,
                        color = Slate500,
                        lineHeight = 18.sp
                    )

                    HorizontalDivider(color = Slate200, thickness = 1.dp)

                    // Derived WebSocket URL info
                    val sampleWs = remember(serverAddress, isSimMode) {
                        if (isSimMode) {
                            "Embedded Simulation Loop (ws://internal/ws/dashboard)"
                        } else {
                            val base = serverAddress.trim().trimEnd('/')
                            when {
                                base.startsWith("https://", ignoreCase = true) -> "wss://" + base.substring(8) + "/ws/dashboard"
                                base.startsWith("http://", ignoreCase = true) -> "ws://" + base.substring(7) + "/ws/dashboard"
                                else -> "ws://$base/ws/dashboard"
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("WebSocket target", fontSize = 12.sp, color = Slate500)
                        Text(
                            sampleWs,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Slate700
                        )
                    }

                    // Test Connection Button & Result
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                isTesting = true
                                testResult = null
                                scope.launch {
                                    try {
                                        settingsStore.serverUrl = serverAddress
                                        settingsStore.isSimulationMode = isSimMode
                                        val service = apiClient.getService()
                                        val res = withContext(Dispatchers.IO) {
                                            service.checkHealth()
                                        }
                                        isSuccess = true
                                        testResult = if (isSimMode) "Health check: ${res.status} (Safety Simulation Engine)" else "Health check: ${res.status}"
                                    } catch (e: Exception) {
                                        isSuccess = false
                                        val err = ApiErrorParser.parse(e)
                                        testResult = err.message
                                    } finally {
                                        isTesting = false
                                    }
                                }
                            },
                            enabled = !isTesting && (isSimMode || serverAddress.isNotBlank()),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("test_connection_button")
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = PrimaryBlue
                                )
                            } else {
                                Text("Test connection")
                            }
                        }

                        Button(
                            onClick = {
                                settingsStore.serverUrl = serverAddress
                                settingsStore.isSimulationMode = isSimMode
                                onSaved()
                            },
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("save_server_button")
                        ) {
                            Text("Save")
                        }
                    }

                    // Inline Test Result
                    if (testResult != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSuccess) StatusGreenBg else StatusRedBg,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(8.dp)
                                .testTag("test_result_box")
                        ) {
                            Icon(
                                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (isSuccess) StatusGreenText else StatusRedText,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = testResult!!,
                                fontSize = 13.sp,
                                color = if (isSuccess) StatusGreenText else StatusRedText
                            )
                        }
                    }
                }
            }
        }
    }
}
