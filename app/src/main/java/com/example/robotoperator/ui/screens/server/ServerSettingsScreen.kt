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
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    settingsStore: ServerSettingsStore,
    apiClient: ApiClient,
    canNavigateBack: Boolean,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit
) {
    var urlInput by remember { mutableStateOf(settingsStore.serverUrl) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val scope = rememberCoroutineScope()

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
                        text = "Robot Backend Address",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Slate900
                    )

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = {
                            urlInput = it
                            testResult = null
                        },
                        label = { Text("Server address") },
                        placeholder = { Text("https://indoor-delivery-bot.onrender.com") },
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                urlInput = ServerSettingsStore.LIVE_RENDER_BACKEND
                                testResult = null
                            }
                        ) {
                            Text("Use Default Render Cloud", fontSize = 12.sp, color = ActionBlue)
                        }
                    }

                    Text(
                        text = "Live backend: https://indoor-delivery-bot.onrender.com\nDo not use localhost on Android emulator.",
                        fontSize = 12.sp,
                        color = Slate500,
                        lineHeight = 16.sp
                    )

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isTesting = true
                                testResult = null
                                settingsStore.serverUrl = urlInput
                                try {
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
                        enabled = urlInput.isNotBlank() && !isTesting,
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
                    settingsStore.serverUrl = urlInput
                    onSaved()
                },
                enabled = urlInput.isNotBlank(),
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
