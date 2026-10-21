package com.example.robotoperator.ui.screens.mapping

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.launch
import java.util.Locale

internal const val LIVE_CAMERA_STREAM_URL = "http://192.0.0.4:8080/video"
private const val LIVE_CAMERA_BASE_URL = "http://192.0.0.4:8080/"

internal fun liveCameraHtml(): String = """<!doctype html>
<html><head><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1" />
<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:transparent}
img{width:100%;height:100%;object-fit:contain;background:transparent}</style></head>
<body><img src="$LIVE_CAMERA_STREAM_URL" alt="Live camera feed" /></body></html>"""

private val Cyan = Color(0xFF00DDEB)
private val Amber = Color(0xFFFFA51B)
private val Glass = Color(0xBB11191C)
private val Muted = Color(0xFFD2DADD)

@Composable
fun LiveMappingConsoleScreen(
    apiClient: ApiClient,
    settingsStore: ServerSettingsStore,
    openConnectionDialogRequest: Boolean = false,
    onConnectionDialogRequestConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    var cameraView by remember { mutableStateOf<WebView?>(null) }
    var session by remember { mutableStateOf<MappingSession?>(null) }
    var robotStatus by remember { mutableStateOf<RobotStatus?>(null) }
    var safetyResponse by remember { mutableStateOf<SafetyStateResponse?>(null) }
    var modeLabel by remember { mutableStateOf("Unknown") }
    var busy by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var controlsExpanded by rememberSaveable { mutableStateOf(false) }
    var showRobotConnectionDialog by rememberSaveable { mutableStateOf(false) }
    var robotIp by rememberSaveable { mutableStateOf("") }
    var robotPort by rememberSaveable { mutableStateOf("9090") }
    var rosBridgeEnabled by remember { mutableStateOf(false) }
    var rosBridgeState by remember { mutableStateOf("UNKNOWN") }
    var connectionWorking by remember { mutableStateOf(false) }
    var connectionMessage by remember { mutableStateOf("Connection state not checked") }
    var confirmFinish by remember { mutableStateOf(false) }
    var actionVersion by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf("Loading backend session status…") }

    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            if (previousOrientation != null) activity?.requestedOrientation = previousOrientation
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
                    state.robot_ip?.let { robotIp = it }
                    state.port?.let { robotPort = it.toString() }
                }
                connectionMessage = if (state.enabled) "Backend ROSBridge: ${state.state.lowercase()}"
                    else "ROSBridge is disabled in backend configuration"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                connectionMessage = ApiErrorParser.parse(error).message
            } finally { connectionWorking = false }
        }
    }

    fun requestRobotConnection() {
        if (connectionWorking) return
        val target = parseRobotConnectionTarget(robotIp, robotPort)
        if (target == null) {
            connectionMessage = "Enter a private IPv4 address and a port from 1 to 65535"
            return
        }
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val compact = maxWidth < 700.dp || maxHeight < 420.dp
        val edge = if (compact) 8.dp else 16.dp
        val panelHeight = (maxHeight - 160.dp).coerceAtLeast(96.dp)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                WebView(context).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    settings.javaScriptEnabled = false
                    settings.domStorageEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    webViewClient = WebViewClient()
                    cameraView = this
                    loadDataWithBaseURL(LIVE_CAMERA_BASE_URL, liveCameraHtml(), "text/html", "UTF-8", null)
                }
            },
            update = { cameraView = it }
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.48f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.50f))
        )))

        // Top HUD: reported values only; unavailable motion telemetry stays blank.
        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(edge),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GaugeDial(
                title = "SPEED",
                value = "—",
                detail = "m/s · no telemetry",
                dialSize = if (compact) 76.dp else 128.dp
            )
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = if (compact) 6.dp else 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Top
            ) {
                HudCard("POSES", session?.pose_update_count?.toString() ?: "—", Modifier.weight(1f))
                Spacer(Modifier.width(if (compact) 5.dp else 12.dp))
                HudAction(
                    label = if (session?.status?.uppercase() == "MAPPING") "Ⅱ  PAUSE" else "▶  RESUME",
                    enabled = !working && !busy && session?.status?.uppercase() in setOf("MAPPING", "PAUSED"),
                    modifier = Modifier.weight(1f),
                    tag = "console_pause"
                ) {
                    if (session?.status?.uppercase() == "MAPPING") updateSession { apiClient.getService().pauseMappingSession(it) }
                    else updateSession { apiClient.getService().resumeMappingSession(it) }
                }
                Spacer(Modifier.width(if (compact) 5.dp else 12.dp))
                HudCard("TIME", formatElapsed(session?.elapsed_ms), Modifier.weight(1f))
            }
            GaugeDial(
                title = "ROBOT",
                value = robotStatus?.connection_state?.uppercase() ?: "UNKNOWN",
                detail = robotStatus?.safety_state?.let { "Last safety: $it" } ?: "Safety unknown",
                dialSize = if (compact) 76.dp else 128.dp
            )
        }

        // Mapping actions float over the camera instead of taking over the page.
        GlassPanel(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = edge, top = if (compact) 64.dp else 86.dp)
                .width(if (compact) 156.dp else 214.dp).heightIn(max = panelHeight).verticalScroll(rememberScrollState()), compact = compact
        ) {
            Text("MAPPING", color = Cyan, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                fontSize = if (compact) 12.sp else 16.sp)
            Text(session?.status?.uppercase() ?: "NO SESSION", color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = if (compact) 15.sp else 20.sp)
            Text(statusMessage, color = Muted, fontSize = if (compact) 10.sp else 12.sp, lineHeight = 15.sp)
            when (session?.status?.uppercase()) {
                "MAPPING" -> {
                    HudAction("FINISH MAP", !working && !busy, Modifier.fillMaxWidth(), "console_finish") {
                        confirmFinish = true
                    }
                }
                "PAUSED" -> {
                    HudAction("FINISH MAP", !working && !busy, Modifier.fillMaxWidth(), "console_finish") {
                        confirmFinish = true
                    }
                }
                "IDLE" -> HudAction("START MAP", !working && !busy, Modifier.fillMaxWidth(), "console_start") {
                    updateSession { apiClient.getService().startMappingSession(it) }
                }
                else -> HudAction("NEW SESSION", !working && !busy, Modifier.fillMaxWidth(), "console_create") {
                    val robotId = settingsStore.cachedRobotId
                    if (robotId.isNullOrBlank()) statusMessage = "No robot is registered or available to this admin account."
                    else if (!working && !busy) {
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
                        } catch (error: Exception) { statusMessage = ApiErrorParser.parse(error).message }
                        finally { working = false }
                        }
                    }
                }
            }
            Text("${robotStatus?.connection_state ?: "UNKNOWN"} · backend mode $modeLabel", color = Muted,
                fontSize = if (compact) 9.sp else 11.sp)
        }

        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = edge, top = if (compact) 64.dp else 86.dp)
                .heightIn(max = panelHeight).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
            horizontalAlignment = Alignment.End
        ) {
            HudAction("◉  CAMERA", !busy, Modifier.width(if (compact) 120.dp else 164.dp), "console_camera") {
                cameraView?.reload()
                refreshSession()
            }
            HudAction("☷  CONTROLS", true, Modifier.width(if (compact) 120.dp else 164.dp), "console_controls") {
                controlsExpanded = !controlsExpanded
            }
            HudAction("⌁  ROBOT WI-FI", true, Modifier.width(if (compact) 120.dp else 164.dp), "console_robot_wifi") {
                showRobotConnectionDialog = true
                refreshRobotConnection()
            }
            if (controlsExpanded) {
                GlassPanel(Modifier.width(if (compact) 160.dp else 210.dp), compact) {
                    Text("BACKEND REQUESTS", color = Cyan, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                        fontSize = if (compact) 10.sp else 12.sp)
                    HudAction("REQUEST MANUAL", !working && !busy, Modifier.fillMaxWidth(), "console_manual") { changeMode("MANUAL") }
                    HudAction("REQUEST AUTO", !working && !busy, Modifier.fillMaxWidth(), "console_auto") { changeMode("AUTONOMOUS") }
                    Button(
                        onClick = {
                            if (working) return@Button
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
                                } finally { working = false }
                            }
                        },
                        enabled = !working,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)),
                        modifier = Modifier.fillMaxWidth().testTag("console_estop_request")
                    ) { Text("REQUEST E-STOP", fontSize = if (compact) 10.sp else 12.sp) }
                    Text(
                        safetyResponse?.let { if (it.physical_execution_confirmed == true) "Backend reports execution confirmed" else "Physical execution unconfirmed" }
                            ?: "These actions update backend state only",
                        color = Muted, fontSize = if (compact) 9.sp else 10.sp
                    )
                }
            }
        }

        // Game-like directional and pedal silhouettes communicate unavailable controls.
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = edge, bottom = edge + 30.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)
        ) {
            DisabledDriveKey("◀", compact, "motion_disabled_left")
            DisabledDriveKey("▶", compact, "motion_disabled_right")
        }
        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = edge, bottom = edge + 30.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            DisabledPedal("BRAKE", compact, "motion_disabled_brake")
            DisabledPedal("THROTTLE", compact, "motion_disabled_throttle")
        }
        Text(
            "DRIVE COMMANDS UNAVAILABLE · No motor API is configured",
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = edge, vertical = 8.dp)
                .background(Glass, RoundedCornerShape(4.dp)).border(1.dp, Cyan.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            color = Color.White, fontSize = if (compact) 9.sp else 11.sp,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
        )
    }

    if (confirmFinish) AlertDialog(
        onDismissRequest = { confirmFinish = false },
        title = { Text("Finish this mapping session?") },
        text = { Text("This ends the current session. Use the mapping detail screen if you need to enter a map reference. Finishing does not verify a physical map.") },
        confirmButton = { TextButton(enabled = !working && !busy && session?.status?.uppercase() in setOf("MAPPING", "PAUSED"), onClick = {
            confirmFinish = false
            updateSession { apiClient.getService().finishMappingSession(it, FinishSessionRequest()) }
        }) { Text("Finish session") } },
        dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep session") } }
    )

    if (showRobotConnectionDialog) {
        AlertDialog(
            onDismissRequest = { if (!connectionWorking) showRobotConnectionDialog = false },
            title = { Text("ROBOT WI-FI CONNECTION") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The backend will open a ROSBridge WebSocket to this robot on its local Wi-Fi.")
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
                        label = { Text("ROSBridge port") },
                        singleLine = true,
                        enabled = !connectionWorking,
                        modifier = Modifier.testTag("robot_wifi_port")
                    )
                    Text("Backend status: $rosBridgeState", style = MaterialTheme.typography.labelMedium)
                    Text(connectionMessage, style = MaterialTheme.typography.bodySmall)
                    if (rosBridgeState == "CONNECTED") {
                        Text("A bridge is already connected. Restart the local backend before changing its target.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (!rosBridgeEnabled) {
                        Text(
                            "For local development only: enable ROSBRIDGE_ENABLED=true in the backend and restart it. Cloud ROS remains disabled.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = ::requestRobotConnection,
                    enabled = rosBridgeEnabled && rosBridgeState != "CONNECTED" && !connectionWorking,
                    modifier = Modifier.testTag("robot_wifi_connect")
                ) { Text(if (connectionWorking) "CONNECTING…" else "CONNECT") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = ::refreshRobotConnection, enabled = !connectionWorking) { Text("REFRESH") }
                    TextButton(onClick = { showRobotConnectionDialog = false }, enabled = !connectionWorking) { Text("CLOSE") }
                }
            }
        )
    }
}

@Composable
private fun GaugeDial(title: String, value: String, detail: String, dialSize: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(dialSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Glass, style = Stroke(width = 3.dp.toPx()))
            drawArc(Cyan.copy(alpha = 0.9f), 135f, 270f, false, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            drawLine(Amber, center, androidx.compose.ui.geometry.Offset(center.x, this.size.minDimension * 0.18f),
                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(title, color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(value, color = Color.White, fontSize = if (dialSize < 90.dp) 10.sp else 15.sp, fontWeight = FontWeight.Black)
            Text(detail, color = Muted, fontSize = if (dialSize < 90.dp) 6.sp else 8.sp, maxLines = 1)
        }
    }
}

@Composable
private fun HudCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Glass, RoundedCornerShape(3.dp)).border(1.dp, Cyan, RoundedCornerShape(3.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

@Composable
private fun HudAction(label: String, enabled: Boolean, modifier: Modifier, tag: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).testTag(tag),
        shape = RoundedCornerShape(3.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (enabled) Cyan else Muted.copy(alpha = 0.45f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Glass,
            contentColor = Color.White,
            disabledContainerColor = Glass.copy(alpha = 0.7f),
            disabledContentColor = Muted.copy(alpha = 0.7f)
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) }
}

@Composable
private fun GlassPanel(modifier: Modifier, compact: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.background(Glass, RoundedCornerShape(5.dp)).border(1.dp, Cyan.copy(alpha = 0.8f), RoundedCornerShape(5.dp))
            .padding(if (compact) 8.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp),
        content = content
    )
}

@Composable
private fun DisabledDriveKey(label: String, compact: Boolean, tag: String) {
    OutlinedButton(
        onClick = {}, enabled = false,
        modifier = Modifier.size(if (compact) 48.dp else 82.dp).testTag(tag),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Cyan.copy(alpha = 0.7f)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Glass, disabledContainerColor = Glass,
            disabledContentColor = Color.White.copy(alpha = 0.85f)),
        contentPadding = PaddingValues(0.dp)
    ) { Text(label, fontSize = if (compact) 24.sp else 42.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun DisabledPedal(label: String, compact: Boolean, tag: String) {
    Column(
        modifier = Modifier.width(if (compact) 46.dp else 76.dp).height(if (compact) 58.dp else 100.dp)
            .background(Glass, RoundedCornerShape(16.dp)).border(1.dp, Cyan.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("⋮", color = Muted, fontSize = if (compact) 20.sp else 30.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White, fontSize = if (compact) 6.sp else 8.sp, fontWeight = FontWeight.Bold)
        Text("LOCKED", color = Amber, fontSize = if (compact) 6.sp else 8.sp)
    }
}

private fun formatElapsed(milliseconds: Long?): String {
    if (milliseconds == null || milliseconds <= 0L) return "--:--"
    val totalSeconds = milliseconds / 1000L
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
