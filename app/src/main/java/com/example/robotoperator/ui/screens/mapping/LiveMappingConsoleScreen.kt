package com.example.robotoperator.ui.screens.mapping

import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.api.ApiErrorParser
import com.example.robotoperator.data.api.parseRobotConnectionTarget
import com.example.robotoperator.data.model.CreateSessionRequest
import com.example.robotoperator.data.model.EmergencyStopRequest
import com.example.robotoperator.data.model.FinishSessionRequest
import com.example.robotoperator.data.model.MappingSession
import com.example.robotoperator.data.model.ModeRequest
import com.example.robotoperator.data.model.RobotConnectionRequest
import com.example.robotoperator.data.model.RobotStatus
import com.example.robotoperator.data.model.SafetyStateResponse
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.robotoperator.ui.components.StatusBadge
import com.example.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

internal const val LIVE_CAMERA_STREAM_URL = "http://192.0.0.4:8080/video"
private const val LIVE_CAMERA_BASE_URL = "http://192.0.0.4:8080/"

internal fun liveCameraHtml(streamUrl: String = LIVE_CAMERA_STREAM_URL): String = """<!doctype html>
<html><head><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1" />
<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:transparent;display:flex;align-items:center;justify-content:center;position:relative}
img{width:100%;height:100%;object-fit:contain;background:transparent}
#err{display:none;position:absolute;top:0;left:0;right:0;bottom:0;color:#94a3b8;font-family:monospace;font-size:11px;text-align:center;align-items:center;justify-content:center;flex-direction:column;padding:12px;background:rgba(15,23,42,0.85);z-index:10}
</style></head>
<body>
<img src="$streamUrl" alt="Live camera feed" onerror="document.getElementById('err').style.display='flex'" />
<div id="err">
<div style="color:#ef4444;font-weight:bold;font-size:12px;margin-bottom:4px;">CAMERA STREAM UNREACHABLE</div>
<div style="color:#cbd5e1;font-size:10px;margin-bottom:6px;word-break:break-all;">$streamUrl</div>
<div style="color:#94a3b8;font-size:10px;">Tap 'SET IP' above to test & update the stream address</div>
</div>
</body></html>"""

internal suspend fun testCameraStreamConnection(streamUrl: String): Pair<Boolean, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    var connection: java.net.HttpURLConnection? = null
    try {
        val cleanUrl = if (streamUrl.startsWith("http://") || streamUrl.startsWith("https://")) streamUrl else "http://$streamUrl"
        val url = java.net.URL(cleanUrl)
        connection = (url.openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3500
            readTimeout = 3500
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "RobotOperator-Android")
        }
        val responseCode = connection.responseCode
        val contentType = connection.contentType.orEmpty()
        if (responseCode in 200..399) {
            val details = if (contentType.isNotBlank()) "HTTP $responseCode ($contentType)" else "HTTP $responseCode OK"
            Pair(true, "Reachable: $details")
        } else {
            Pair(false, "Server returned HTTP $responseCode")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val msg = e.localizedMessage ?: e.message ?: "Could not connect to host"
        Pair(false, msg)
    } finally {
        try {
            connection?.disconnect()
        } catch (_: Exception) {}
    }
}

@Composable
fun LiveMappingConsoleScreen(
    apiClient: ApiClient,
    settingsStore: ServerSettingsStore,
    openConnectionDialogRequest: Boolean = false,
    onConnectionDialogRequestConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraView by remember { mutableStateOf<WebView?>(null) }
    var session by remember { mutableStateOf<MappingSession?>(null) }
    var robotStatus by remember { mutableStateOf<RobotStatus?>(null) }
    var safetyResponse by remember { mutableStateOf<SafetyStateResponse?>(null) }
    var modeLabel by remember { mutableStateOf("Unknown") }
    var busy by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var controlsExpanded by rememberSaveable { mutableStateOf(true) }
    var showRobotConnectionDialog by rememberSaveable { mutableStateOf(false) }
    var showCameraSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var robotIp by rememberSaveable { mutableStateOf(settingsStore.robotWifiIp.orEmpty()) }
    var robotPort by rememberSaveable { mutableStateOf(settingsStore.robotWifiPort) }
    var customStreamUrl by rememberSaveable { mutableStateOf(settingsStore.cameraStreamUrl.orEmpty()) }
    var cameraIp by rememberSaveable { mutableStateOf(settingsStore.cameraIp.orEmpty()) }
    var cameraPort by rememberSaveable { mutableStateOf(settingsStore.cameraPort) }
    var rosBridgeEnabled by remember { mutableStateOf(false) }
    var rosBridgeState by remember { mutableStateOf("UNKNOWN") }
    var connectionWorking by remember { mutableStateOf(false) }
    var connectionMessage by remember { mutableStateOf("Connection state not checked") }
    var confirmFinish by remember { mutableStateOf(false) }
    var actionVersion by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf("Loading backend session status…") }

    val currentCameraStreamUrl = remember(customStreamUrl, cameraIp, robotIp, cameraPort, settingsStore.cameraStreamUrl, settingsStore.cameraIp, settingsStore.robotWifiIp, settingsStore.cameraPort) {
        val configuredUrl = customStreamUrl.trim().ifEmpty { settingsStore.cameraStreamUrl?.trim().orEmpty() }
        if (configuredUrl.isNotBlank()) {
            if (configuredUrl.startsWith("http://") || configuredUrl.startsWith("https://")) {
                configuredUrl
            } else {
                "http://$configuredUrl"
            }
        } else {
            val effectiveCameraIp = cameraIp.trim().ifEmpty { settingsStore.cameraIp?.trim().orEmpty() }
            val effectivePort = cameraPort.trim().ifEmpty { settingsStore.cameraPort.trim() }.ifEmpty { "8080" }
            if (effectiveCameraIp.isNotBlank()) {
                if (effectiveCameraIp.startsWith("http://") || effectiveCameraIp.startsWith("https://")) {
                    effectiveCameraIp
                } else {
                    "http://$effectiveCameraIp:$effectivePort/video"
                }
            } else {
                val effectiveRobotIp = robotIp.trim().ifEmpty { settingsStore.robotWifiIp?.trim().orEmpty() }
                if (effectiveRobotIp.isNotBlank()) {
                    "http://$effectiveRobotIp:$effectivePort/video"
                } else {
                    LIVE_CAMERA_STREAM_URL
                }
            }
        }
    }

    fun refreshSession() {
        if (busy || working) return
        busy = true
        val version = actionVersion
        scope.launch {
            try {
                val status = apiClient.getService().getRobotStatus()
                if (version != actionVersion) return@launch
                robotStatus = status
                if (status.id.isNotBlank()) settingsStore.cachedRobotId = status.id
                modeLabel = status.control_mode
                val sessions = apiClient.getService().getMappingSessions()
                if (version != actionVersion) return@launch
                val activeId = settingsStore.activeSessionId
                session = sessions.firstOrNull { it.id == activeId && it.status.uppercase() in setOf("MAPPING", "PAUSED", "IDLE") }
                    ?: sessions.firstOrNull { it.status.uppercase() == "MAPPING" }
                    ?: sessions.firstOrNull { it.status.uppercase() == "PAUSED" }
                session?.let { settingsStore.activeSessionId = it.id }
                statusMessage = if (session == null) "No active mapping session" else "Session status refreshed"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (version == actionVersion) statusMessage = ApiErrorParser.parse(error).message
            } finally {
                busy = false
            }
        }
    }

    fun updateSession(action: suspend (String) -> MappingSession) {
        if (working || busy) return
        val id = session?.id ?: return
        working = true
        actionVersion++
        scope.launch {
            statusMessage = "Updating mapping session…"
            try {
                session = action(id)
                statusMessage = "Mapping session: ${session?.status ?: "updated"}"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                statusMessage = ApiErrorParser.parse(error).message
            } finally {
                working = false
            }
        }
    }

    fun changeMode(mode: String) {
        if (working || busy) return
        working = true
        actionVersion++
        scope.launch {
            try {
                modeLabel = apiClient.getService().setRobotMode(ModeRequest(mode)).mode
                statusMessage = "Backend mode request accepted: $modeLabel"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                statusMessage = ApiErrorParser.parse(error).message
            } finally {
                working = false
            }
        }
    }

    fun refreshRobotConnection() {
        if (connectionWorking) return
        connectionWorking = true
        scope.launch {
            try {
                val state = apiClient.getService().getRobotConnection()
                rosBridgeEnabled = state.enabled
                rosBridgeState = state.state
                if (robotIp.isBlank()) {
                    state.robot_ip?.let {
                        robotIp = it
                        settingsStore.robotWifiIp = it
                    }
                    state.port?.let {
                        robotPort = it.toString()
                        settingsStore.robotWifiPort = it.toString()
                    }
                }
                connectionMessage = if (state.enabled) "Backend ROSBridge: ${state.state.lowercase()}"
                else "ROSBridge is disabled in backend configuration"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                connectionMessage = ApiErrorParser.parse(error).message
            } finally {
                connectionWorking = false
            }
        }
    }

    fun requestRobotConnection() {
        if (connectionWorking) return
        val target = parseRobotConnectionTarget(robotIp, robotPort)
        if (target == null) {
            connectionMessage = "Enter a private IPv4 address and a port from 1 to 65535"
            return
        }
        settingsStore.robotWifiIp = target.ip
        settingsStore.robotWifiPort = target.port.toString()
        settingsStore.cameraIp = cameraIp.trim().ifEmpty { null }
        settingsStore.cameraPort = cameraPort.trim().ifEmpty { "8080" }
        connectionWorking = true
        scope.launch {
            try {
                val result = apiClient.getService().connectRobotBridge(
                    RobotConnectionRequest(robot_ip = target.ip, port = target.port)
                )
                rosBridgeEnabled = result.enabled
                rosBridgeState = result.state
                connectionMessage = "Backend ROSBridge connection requested; refresh to check its state"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                connectionMessage = ApiErrorParser.parse(error).message
            } finally {
                connectionWorking = false
            }
        }
    }

    LaunchedEffect(apiClient) {
        refreshSession()
        refreshRobotConnection()
    }

    LaunchedEffect(openConnectionDialogRequest) {
        if (openConnectionDialogRequest) {
            showRobotConnectionDialog = true
            refreshRobotConnection()
            onConnectionDialogRequestConsumed()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraView?.stopLoading()
            cameraView?.destroy()
            cameraView = null
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceCanvas)
    ) {
        val isLandscape = maxWidth > maxHeight && maxWidth > 600.dp

        if (isLandscape) {
            // Landscape layout: Camera stream on left, controls & metrics on right
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left 55%: Camera Stream
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Slate900)
                        .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
                ) {
                    CameraStreamView(
                        streamUrl = currentCameraStreamUrl,
                        onWebViewCreated = { cameraView = it }
                    )
                    CameraOverlayBar(
                        streamUrl = currentCameraStreamUrl,
                        busy = busy,
                        onRefreshCamera = {
                            cameraView?.clearCache(true)
                            cameraView?.loadUrl(currentCameraStreamUrl)
                            refreshSession()
                        },
                        onOpenSettings = {
                            showCameraSettingsDialog = true
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                    )
                }

                // Right 45%: Scrollable control and telemetry panels
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SessionTelemetryCard(
                        session = session,
                        robotStatus = robotStatus,
                        modeLabel = modeLabel,
                        statusMessage = statusMessage,
                        working = working,
                        busy = busy,
                        onPauseResume = {
                            if (session?.status?.uppercase() == "MAPPING") {
                                updateSession { apiClient.getService().pauseMappingSession(it) }
                            } else {
                                updateSession { apiClient.getService().resumeMappingSession(it) }
                            }
                        },
                        onStartMapping = {
                            updateSession { apiClient.getService().startMappingSession(it) }
                        },
                        onFinishMapping = { confirmFinish = true },
                        onCreateSession = {
                            val robotId = settingsStore.cachedRobotId
                            if (robotId.isNullOrBlank()) {
                                statusMessage = "No robot is registered or available to this admin account."
                            } else if (!working && !busy) {
                                working = true
                                actionVersion++
                                scope.launch {
                                    try {
                                        val created = apiClient.getService().createMappingSession(CreateSessionRequest(robot_id = robotId))
                                        session = created
                                        settingsStore.activeSessionId = created.id
                                        statusMessage = "Session created. Start mapping when ready."
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        statusMessage = ApiErrorParser.parse(error).message
                                    } finally {
                                        working = false
                                    }
                                }
                            }
                        }
                    )

                    RobotOperationsCard(
                        modeLabel = modeLabel,
                        working = working,
                        busy = busy,
                        onRequestManual = { changeMode("MANUAL") },
                        onRequestAuto = { changeMode("AUTONOMOUS") },
                        onRequestEStop = {
                            if (!working) {
                                working = true
                                actionVersion++
                                scope.launch {
                                    try {
                                        safetyResponse = apiClient.getService().emergencyStop(EmergencyStopRequest())
                                        statusMessage = "Emergency-stop request sent to backend"
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        statusMessage = "E-stop request failed: ${ApiErrorParser.parse(error).message}"
                                    } finally {
                                        working = false
                                    }
                                }
                            }
                        },
                        safetyResponse = safetyResponse
                    )

                    DriveDisabledNoticeCard(
                        controlsExpanded = controlsExpanded,
                        onToggleControls = { controlsExpanded = !controlsExpanded },
                        onOpenRobotWifi = {
                            showRobotConnectionDialog = true
                            refreshRobotConnection()
                        },
                        robotIp = robotIp,
                        robotPort = robotPort,
                        cameraIp = cameraIp,
                        cameraPort = cameraPort,
                        rosBridgeState = rosBridgeState
                    )
                }
            }
        } else {
            // Portrait layout: Clean vertical stack with camera on top and structured scrollable cards below
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Camera Viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Slate900)
                        .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
                ) {
                    CameraStreamView(
                        streamUrl = currentCameraStreamUrl,
                        onWebViewCreated = { cameraView = it }
                    )
                    CameraOverlayBar(
                        streamUrl = currentCameraStreamUrl,
                        busy = busy,
                        onRefreshCamera = {
                            cameraView?.clearCache(true)
                            cameraView?.loadUrl(currentCameraStreamUrl)
                            refreshSession()
                        },
                        onOpenSettings = {
                            showCameraSettingsDialog = true
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                    )
                }

                SessionTelemetryCard(
                    session = session,
                    robotStatus = robotStatus,
                    modeLabel = modeLabel,
                    statusMessage = statusMessage,
                    working = working,
                    busy = busy,
                    onPauseResume = {
                        if (session?.status?.uppercase() == "MAPPING") {
                            updateSession { apiClient.getService().pauseMappingSession(it) }
                        } else {
                            updateSession { apiClient.getService().resumeMappingSession(it) }
                        }
                    },
                    onStartMapping = {
                        updateSession { apiClient.getService().startMappingSession(it) }
                    },
                    onFinishMapping = { confirmFinish = true },
                    onCreateSession = {
                        val robotId = settingsStore.cachedRobotId
                        if (robotId.isNullOrBlank()) {
                            statusMessage = "No robot is registered or available to this admin account."
                        } else if (!working && !busy) {
                            working = true
                            actionVersion++
                            scope.launch {
                                try {
                                    val created = apiClient.getService().createMappingSession(CreateSessionRequest(robot_id = robotId))
                                    session = created
                                    settingsStore.activeSessionId = created.id
                                    statusMessage = "Session created. Start mapping when ready."
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    statusMessage = ApiErrorParser.parse(error).message
                                } finally {
                                    working = false
                                }
                            }
                        }
                    }
                )

                RobotOperationsCard(
                    modeLabel = modeLabel,
                    working = working,
                    busy = busy,
                    onRequestManual = { changeMode("MANUAL") },
                    onRequestAuto = { changeMode("AUTONOMOUS") },
                    onRequestEStop = {
                        if (!working) {
                            working = true
                            actionVersion++
                            scope.launch {
                                try {
                                    safetyResponse = apiClient.getService().emergencyStop(EmergencyStopRequest())
                                    statusMessage = "Emergency-stop request sent to backend"
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    statusMessage = "E-stop request failed: ${ApiErrorParser.parse(error).message}"
                                } finally {
                                    working = false
                                }
                            }
                        }
                    },
                    safetyResponse = safetyResponse
                )

                DriveDisabledNoticeCard(
                    controlsExpanded = controlsExpanded,
                    onToggleControls = { controlsExpanded = !controlsExpanded },
                    onOpenRobotWifi = {
                        showRobotConnectionDialog = true
                        refreshRobotConnection()
                    },
                    robotIp = robotIp,
                    robotPort = robotPort,
                    cameraIp = cameraIp,
                    cameraPort = cameraPort,
                    rosBridgeState = rosBridgeState
                )
            }
        }
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish this mapping session?") },
            text = { Text("This ends the current session. Use the mapping detail screen if you need to enter a map reference. Finishing does not verify a physical map.") },
            confirmButton = {
                TextButton(
                    enabled = !working && !busy && session?.status?.uppercase() in setOf("MAPPING", "PAUSED"),
                    onClick = {
                        confirmFinish = false
                        updateSession { apiClient.getService().finishMappingSession(it, FinishSessionRequest()) }
                    }
                ) { Text("Finish session") }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) { Text("Keep session") }
            }
        )
    }

    if (showRobotConnectionDialog) {
        AlertDialog(
            onDismissRequest = { if (!connectionWorking) showRobotConnectionDialog = false },
            title = { Text("ROBOT WI-FI & CAMERA") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Configure the robot's local Wi-Fi IP and communication ports.")
                    OutlinedTextField(
                        value = robotIp,
                        onValueChange = { robotIp = it },
                        label = { Text("Robot IPv4 address") },
                        singleLine = true,
                        enabled = !connectionWorking,
                        modifier = Modifier.testTag("robot_wifi_ip")
                    )
                    OutlinedTextField(
                        value = robotPort,
                        onValueChange = { robotPort = it.filter(Char::isDigit).take(5) },
                        label = { Text("ROSBridge port (default 9090)") },
                        singleLine = true,
                        enabled = !connectionWorking,
                        modifier = Modifier.testTag("robot_wifi_port")
                    )
                    OutlinedTextField(
                        value = cameraIp,
                        onValueChange = { cameraIp = it.trim() },
                        label = { Text("Camera stream IP (leave blank to follow Robot IP)") },
                        placeholder = { Text(robotIp.ifBlank { "192.0.0.4" }) },
                        singleLine = true,
                        enabled = !connectionWorking,
                        modifier = Modifier.testTag("robot_camera_ip")
                    )
                    OutlinedTextField(
                        value = cameraPort,
                        onValueChange = { cameraPort = it.filter(Char::isDigit).take(5) },
                        label = { Text("Camera port (default 8080)") },
                        singleLine = true,
                        enabled = !connectionWorking,
                        modifier = Modifier.testTag("robot_camera_port")
                    )
                    Text(
                        text = "Camera stream: $currentCameraStreamUrl",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Slate600
                    )
                    Text("Backend status: $rosBridgeState", style = MaterialTheme.typography.labelMedium)
                    Text(connectionMessage, style = MaterialTheme.typography.bodySmall)
                    if (rosBridgeState == "CONNECTED") {
                        Text(
                            "A bridge is already connected. Restart the local backend before changing its target.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!rosBridgeEnabled) {
                        Text(
                            "Backend ROSBridge is disabled. Camera stream will connect directly to the robot IP.",
                            color = Slate600,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleanIp = robotIp.trim()
                        if (cleanIp.isNotBlank()) {
                            settingsStore.robotWifiIp = cleanIp
                        }
                        settingsStore.robotWifiPort = robotPort.trim().ifEmpty { "9090" }
                        settingsStore.cameraIp = cameraIp.trim().ifEmpty { null }
                        settingsStore.cameraPort = cameraPort.trim().ifEmpty { "8080" }

                        cameraView?.clearCache(true)
                        cameraView?.loadUrl(currentCameraStreamUrl)

                        if (rosBridgeEnabled && rosBridgeState != "CONNECTED") {
                            requestRobotConnection()
                        } else {
                            connectionMessage = "Robot Wi-Fi & camera settings saved"
                            showRobotConnectionDialog = false
                        }
                    },
                    enabled = !connectionWorking && (robotIp.isNotBlank() || rosBridgeEnabled),
                    modifier = Modifier.testTag("robot_wifi_connect")
                ) {
                    Text(if (connectionWorking) "CONNECTING…" else if (rosBridgeEnabled && rosBridgeState != "CONNECTED") "CONNECT" else "SAVE")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = ::refreshRobotConnection, enabled = !connectionWorking) { Text("REFRESH") }
                    TextButton(onClick = { showRobotConnectionDialog = false }, enabled = !connectionWorking) { Text("CLOSE") }
                }
            }
        )
    }

    if (showCameraSettingsDialog) {
        var draftStreamUrl by rememberSaveable { mutableStateOf(currentCameraStreamUrl) }
        var draftCameraIp by rememberSaveable { mutableStateOf(cameraIp.ifEmpty { robotIp }) }
        var draftCameraPort by rememberSaveable { mutableStateOf(cameraPort) }
        var testingConnection by remember { mutableStateOf(false) }
        var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

        AlertDialog(
            onDismissRequest = { if (!testingConnection) showCameraSettingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = StatusGreen)
                    Text("LIVE CAMERA STREAM SETUP")
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Customize the full stream URL or configure IP & port. Use Test Connection to verify live access before applying.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate700
                    )

                    OutlinedTextField(
                        value = draftStreamUrl,
                        onValueChange = {
                            draftStreamUrl = it
                            testResult = null
                        },
                        label = { Text("Stream URL (Fully Editable)") },
                        placeholder = { Text("http://192.0.0.4:8080/video") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("camera_stream_url_input")
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Presets:", fontSize = 11.sp, color = Slate500, fontFamily = FontFamily.Monospace)
                        listOf("/video", "/stream", "/mjpeg", "/").forEach { path ->
                            OutlinedButton(
                                onClick = {
                                    val trimmed = draftStreamUrl.trim()
                                    val base = if (trimmed.contains("://")) {
                                        val proto = trimmed.substringBefore("://")
                                        val afterProto = trimmed.substringAfter("://")
                                        val hostPort = afterProto.substringBefore("/")
                                        "$proto://$hostPort"
                                    } else if (draftCameraIp.isNotBlank()) {
                                        "http://$draftCameraIp:${draftCameraPort.ifBlank { "8080" }}"
                                    } else {
                                        "http://192.0.0.4:8080"
                                    }
                                    draftStreamUrl = base + if (path == "/") "" else path
                                    testResult = null
                                },
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text(path, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = draftCameraIp,
                            onValueChange = {
                                draftCameraIp = it.trim()
                                val host = draftCameraIp.ifBlank { "192.0.0.4" }
                                val port = draftCameraPort.ifBlank { "8080" }
                                draftStreamUrl = "http://$host:$port/video"
                                testResult = null
                            },
                            label = { Text("Camera IP") },
                            placeholder = { Text("192.0.0.4") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(2f)
                                .testTag("robot_camera_ip")
                        )

                        OutlinedTextField(
                            value = draftCameraPort,
                            onValueChange = {
                                draftCameraPort = it.filter(Char::isDigit).take(5)
                                val host = draftCameraIp.ifBlank { "192.0.0.4" }
                                val port = draftCameraPort.ifBlank { "8080" }
                                draftStreamUrl = "http://$host:$port/video"
                                testResult = null
                            },
                            label = { Text("Port") },
                            placeholder = { Text("8080") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("robot_camera_port")
                        )
                    }

                    if (robotIp.isNotBlank() && draftCameraIp != robotIp) {
                        TextButton(
                            onClick = {
                                draftCameraIp = robotIp
                                val port = draftCameraPort.ifBlank { "8080" }
                                draftStreamUrl = "http://$robotIp:$port/video"
                                testResult = null
                            },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Use Robot Wi-Fi IP ($robotIp)", fontSize = 11.sp, color = ActionBlue)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val urlToTest = draftStreamUrl.trim()
                                if (urlToTest.isBlank() || testingConnection) return@Button
                                testingConnection = true
                                testResult = null
                                scope.launch {
                                    testResult = testCameraStreamConnection(urlToTest)
                                    testingConnection = false
                                }
                            },
                            enabled = !testingConnection && draftStreamUrl.isNotBlank(),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Slate800),
                            modifier = Modifier.testTag("test_camera_stream_button")
                        ) {
                            if (testingConnection) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 2.dp,
                                    color = PureWhite
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("TESTING…", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("TEST CONNECTION", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }

                        TextButton(
                            onClick = {
                                draftStreamUrl = LIVE_CAMERA_STREAM_URL
                                draftCameraIp = "192.0.0.4"
                                draftCameraPort = "8080"
                                testResult = null
                            }
                        ) {
                            Text("Reset URL", fontSize = 11.sp, color = Slate500)
                        }
                    }

                    testResult?.let { (success, message) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (success) StatusGreen.copy(alpha = 0.08f) else StatusRed.copy(alpha = 0.08f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (success) StatusGreen else StatusRed
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (success) StatusGreen else StatusRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column {
                                    Text(
                                        text = if (success) "STREAM REACHABLE" else "STREAM UNREACHABLE",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (success) StatusGreen else StatusRed,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = message,
                                        fontSize = 10.sp,
                                        color = Slate700,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            showCameraSettingsDialog = false
                            showRobotConnectionDialog = true
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Configure Robot ROSBridge (port 9090) instead →", fontSize = 11.sp, color = Slate600)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalUrl = draftStreamUrl.trim().ifEmpty { currentCameraStreamUrl }
                        val cleanUrl = if (finalUrl.startsWith("http://") || finalUrl.startsWith("https://")) finalUrl else "http://$finalUrl"
                        customStreamUrl = cleanUrl
                        settingsStore.cameraStreamUrl = cleanUrl

                        runCatching {
                            val uri = java.net.URI(cleanUrl)
                            if (!uri.host.isNullOrBlank()) {
                                cameraIp = uri.host
                                settingsStore.cameraIp = uri.host
                            }
                            if (uri.port > 0) {
                                cameraPort = uri.port.toString()
                                settingsStore.cameraPort = uri.port.toString()
                            }
                        }

                        showCameraSettingsDialog = false
                        cameraView?.clearCache(true)
                        cameraView?.loadUrl(cleanUrl)
                    },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
                ) {
                    Text("APPLY & RELOAD FEED")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCameraSettingsDialog = false },
                    enabled = !testingConnection
                ) {
                    Text("CANCEL")
                }
            }
        )
    }
}

@Composable
private fun CameraStreamView(
    streamUrl: String,
    onWebViewCreated: (WebView) -> Unit
) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            WebView(context).apply {
                setBackgroundColor(AndroidColor.BLACK)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    loadsImagesAutomatically = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                }
                webViewClient = object : WebViewClient() {
                    override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                        handler?.proceed()
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript("""
                            (function() {
                                var img = document.querySelector('img');
                                if (img) {
                                    img.style.width = '100%';
                                    img.style.height = '100%';
                                    img.style.objectFit = 'contain';
                                }
                                document.body.style.backgroundColor = '#000000';
                                document.body.style.margin = '0';
                                document.body.style.overflow = 'hidden';
                                document.body.style.display = 'flex';
                                document.body.style.alignItems = 'center';
                                document.body.style.justifyContent = 'center';
                            })();
                        """.trimIndent(), null)
                    }
                }
                onWebViewCreated(this)
                tag = streamUrl
                loadUrl(streamUrl)
            }
        },
        update = { webView ->
            onWebViewCreated(webView)
            if (webView.tag != streamUrl) {
                webView.tag = streamUrl
                webView.clearCache(true)
                webView.loadUrl(streamUrl)
            }
        }
    )
}

@Composable
private fun CameraOverlayBar(
    streamUrl: String,
    busy: Boolean,
    onRefreshCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Slate900.copy(alpha = 0.85f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .clickable { onOpenSettings() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Camera",
                    tint = StatusGreen,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "LIVE CAMERA FEED",
                    color = PureWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = "($streamUrl)",
                color = Slate400,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier
                    .height(30.dp)
                    .testTag("console_camera_settings"),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Slate800,
                    contentColor = PureWhite
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate600)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configure Camera Stream IP",
                    tint = PureWhite,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("SET IP", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }

            OutlinedButton(
                onClick = onRefreshCamera,
                enabled = !busy,
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier
                    .height(30.dp)
                    .testTag("console_camera"),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Slate800,
                    contentColor = PureWhite
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate600)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Camera",
                    tint = PureWhite,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("RELOAD", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun SessionTelemetryCard(
    session: MappingSession?,
    robotStatus: RobotStatus?,
    modeLabel: String,
    statusMessage: String,
    working: Boolean,
    busy: Boolean,
    onPauseResume: () -> Unit,
    onStartMapping: () -> Unit,
    onFinishMapping: () -> Unit,
    onCreateSession: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MAPPING SESSION & SLAM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate500,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = session?.status?.uppercase() ?: "NO ACTIVE SESSION",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = Slate900
                    )
                }

                StatusBadge(
                    status = robotStatus?.connection_state ?: "UNKNOWN"
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate100, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = statusMessage,
                    fontSize = 11.sp,
                    color = Slate700,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Metrics row: Poses count, Elapsed time, Control mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricBox(
                    label = "POSES",
                    value = session?.pose_update_count?.toString() ?: "—",
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = "TIME",
                    value = formatElapsed(session?.elapsed_ms),
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = "MODE",
                    value = modeLabel.uppercase(),
                    modifier = Modifier.weight(1f)
                )
            }

            // Contextual Session Actions
            when (session?.status?.uppercase()) {
                "MAPPING" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onPauseResume,
                            enabled = !working && !busy,
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusAmber),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("console_pause")
                        ) {
                            Text("Ⅱ  PAUSE", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Button(
                            onClick = onFinishMapping,
                            enabled = !working && !busy,
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("console_finish")
                        ) {
                            Text("✓  FINISH MAP", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                "PAUSED" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onPauseResume,
                            enabled = !working && !busy,
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("console_pause")
                        ) {
                            Text("▶  RESUME", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Button(
                            onClick = onFinishMapping,
                            enabled = !working && !busy,
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("console_finish")
                        ) {
                            Text("✓  FINISH MAP", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                "IDLE" -> {
                    Button(
                        onClick = onStartMapping,
                        enabled = !working && !busy,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("console_start")
                    ) {
                        Text("▶  START MAPPING SESSION", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
                else -> {
                    Button(
                        onClick = onCreateSession,
                        enabled = !working && !busy,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("console_create")
                    ) {
                        Text("+  CREATE MAPPING SESSION", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Slate50, RoundedCornerShape(4.dp))
            .border(1.dp, BorderHairline, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Slate500,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = Slate900,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
private fun RobotOperationsCard(
    modeLabel: String,
    working: Boolean,
    busy: Boolean,
    onRequestManual: () -> Unit,
    onRequestAuto: () -> Unit,
    onRequestEStop: () -> Unit,
    safetyResponse: SafetyStateResponse?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "CONTROL MODE & SAFETY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Slate500,
                fontFamily = FontFamily.Monospace
            )

            // Mode Selector: Manual vs Autonomous
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isManual = modeLabel.uppercase() == "MANUAL"
                OutlinedButton(
                    onClick = onRequestManual,
                    enabled = !working && !busy,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isManual) ActionBlue else PureWhite,
                        contentColor = if (isManual) PureWhite else Slate800
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isManual) ActionBlue else Slate300),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("console_manual")
                ) {
                    Text(
                        text = if (isManual) "● MANUAL (ACTIVE)" else "MANUAL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                val isAuto = modeLabel.uppercase() == "AUTONOMOUS"
                OutlinedButton(
                    onClick = onRequestAuto,
                    enabled = !working && !busy,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isAuto) ActionBlue else PureWhite,
                        contentColor = if (isAuto) PureWhite else Slate800
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isAuto) ActionBlue else Slate300),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("console_auto")
                ) {
                    Text(
                        text = if (isAuto) "● AUTONOMOUS" else "AUTONOMOUS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // High-visibility Emergency Stop Button
            Button(
                onClick = onRequestEStop,
                enabled = !working,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("console_estop_request")
            ) {
                Text(
                    text = "🛑  REQUEST EMERGENCY STOP",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = PureWhite
                )
            }

            Text(
                text = safetyResponse?.let {
                    if (it.physical_execution_confirmed == true) "Backend reports execution confirmed"
                    else "Physical execution unconfirmed"
                } ?: "Emergency stop sends request to backend. Physical execution is verified by backend response.",
                fontSize = 10.sp,
                color = Slate500,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun DriveDisabledNoticeCard(
    controlsExpanded: Boolean,
    onToggleControls: () -> Unit,
    onOpenRobotWifi: () -> Unit,
    robotIp: String,
    robotPort: String,
    cameraIp: String = "",
    cameraPort: String,
    rosBridgeState: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ROSBRIDGE TELEOPERATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate500,
                        fontFamily = FontFamily.Monospace
                    )
                    val effectiveCam = if (cameraIp.isNotBlank()) "$cameraIp:$cameraPort" else if (robotIp.isNotBlank()) "$robotIp:$cameraPort" else "192.0.0.4:$cameraPort"
                    Text(
                        text = if (robotIp.isNotBlank()) "$robotIp:$robotPort (CAM: $effectiveCam)" else "CAM: $effectiveCam",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Slate700
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onOpenRobotWifi,
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("console_robot_wifi")
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WI-FI / IP", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    OutlinedButton(
                        onClick = onToggleControls,
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("console_controls")
                    ) {
                        Text(
                            text = if (controlsExpanded) "HIDE" else "SHOW",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            if (controlsExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Slate100, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Backend bridge: $rosBridgeState · Motor commands active when ROSBridge is connected",
                            color = Slate700,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                DisabledDriveKey("◀", "motion_disabled_left")
                                DisabledDriveKey("▶", "motion_disabled_right")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                DisabledPedal("BRAKE", "motion_disabled_brake")
                                DisabledPedal("THROTTLE", "motion_disabled_throttle")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisabledDriveKey(label: String, tag: String) {
    OutlinedButton(
        onClick = {},
        enabled = false,
        modifier = Modifier
            .size(36.dp)
            .testTag(tag),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate300),
        colors = ButtonDefaults.outlinedButtonColors(
            disabledContainerColor = Slate200,
            disabledContentColor = Slate500
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DisabledPedal(label: String, tag: String) {
    Column(
        modifier = Modifier
            .width(62.dp)
            .height(36.dp)
            .background(Slate200, RoundedCornerShape(6.dp))
            .border(1.dp, Slate300, RoundedCornerShape(6.dp))
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, color = Slate700, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text("LOCKED", color = StatusAmber, fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

private fun formatElapsed(milliseconds: Long?): String {
    if (milliseconds == null || milliseconds <= 0L) return "--:--"
    val totalSeconds = milliseconds / 1000L
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}
