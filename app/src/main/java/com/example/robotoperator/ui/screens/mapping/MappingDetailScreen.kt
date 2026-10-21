package com.example.robotoperator.ui.screens.mapping

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.api.ApiErrorParser
import com.example.robotoperator.data.model.FinishSessionRequest
import com.example.robotoperator.data.model.MappingSession
import com.example.robotoperator.state.RobotStateHolder
import com.example.robotoperator.ui.components.KeyValRow
import com.example.robotoperator.ui.components.PosePlotCanvas
import com.example.robotoperator.ui.components.StatusBadge
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MappingDetailScreen(
    sessionId: String,
    apiClient: ApiClient,
    stateHolder: RobotStateHolder,
    onNavigateBack: () -> Unit
) {
    var session by remember { mutableStateOf<MappingSession?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var actionVersion by remember(sessionId) { mutableStateOf(0) }
    var fetchError by remember(sessionId) { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var isExecutingAction by remember { mutableStateOf(false) }

    var showFinishDialog by rememberSaveable { mutableStateOf(false) }
    var mapDataRefInput by rememberSaveable { mutableStateOf("") }

    val currentPose by stateHolder.currentPose.collectAsState()
    val poseTrail by stateHolder.poseTrail.collectAsState()
    val scope = rememberCoroutineScope()

    suspend fun fetchSession() {
        val version = actionVersion
        try {
            val s = apiClient.getService().getMappingSession(sessionId)
            if (version == actionVersion && !isExecutingAction) {
                session = s
                fetchError = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Exception) {
            val parsed = ApiErrorParser.parse(t)
            if (version == actionVersion) fetchError = parsed.message
        } finally {
            isLoading = false
        }
    }

    // Polling every 2s while mapping session is active (MAPPING or PAUSED), or single initial fetch
    LaunchedEffect(sessionId) {
        while (isActive) {
            if (!isExecutingAction) fetchSession()
            val currentStatus = session?.status?.uppercase()
            if (currentStatus == "MAPPING" || currentStatus == "PAUSED" || session == null) {
                delay(2000)
            } else {
                delay(5000)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Session ${sessionId.take(8)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate900
                    )
                },
                navigationIcon = {
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PureWhite,
                    titleContentColor = Slate900
                )
            )
        },
        containerColor = SurfaceCanvas
    ) { innerPadding ->
        if (isLoading && session == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading session details...",
                    fontSize = 14.sp,
                    color = Slate500
                )
            }
        } else if (session == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = fetchError ?: "Session not found",
                        fontSize = 14.sp,
                        color = StatusRed
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { scope.launch { fetchSession() } }) {
                        Text("Retry")
                    }
                }
            }
        } else {
            val currentSession = session!!
            val status = currentSession.status.uppercase()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                fetchError?.let {
                    Text("Updates unavailable: $it", color = StatusRed, style = MaterialTheme.typography.bodyMedium)
                    TextButton(enabled = !isExecutingAction, onClick = { scope.launch { fetchSession() } }) { Text("Retry refresh") }
                }
                if (!actionError.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(StatusRedBg)
                            .border(1.dp, StatusRedBorder, RoundedCornerShape(4.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = actionError!!,
                            color = StatusRed,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                if (status == "MAPPING" || status == "PAUSED") {
                    PosePlotCanvas(
                        currentPose = currentPose,
                        poseTrail = poseTrail,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (status == "IDLE" || status == "MAPPING" || status == "PAUSED") {
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
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "SESSION CONTROLS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Slate500
                            )

                            when (status) {
                                "IDLE" -> {
                                    Button(
                                        onClick = {
                                            if (isExecutingAction || fetchError != null) return@Button
                                            isExecutingAction = true
                                            actionVersion++
                                            scope.launch {
                                                actionError = null
                                                try {
                                                    session = apiClient.getService().startMappingSession(sessionId)
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (t: Exception) {
                                                    actionError = ApiErrorParser.parse(t).message
                                                } finally {
                                                    isExecutingAction = false
                                                }
                                            }
                                        },
                                        enabled = !isExecutingAction && fetchError == null,
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("start_session_button")
                                    ) {
                                        Text("Start Mapping")
                                    }
                                }

                                "MAPPING" -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                if (isExecutingAction || fetchError != null) return@Button
                                                isExecutingAction = true
                                                actionVersion++
                                                scope.launch {
                                                    actionError = null
                                                    try {
                                                        session = apiClient.getService().pauseMappingSession(sessionId)
                                                    } catch (e: CancellationException) {
                                                        throw e
                                                    } catch (t: Exception) {
                                                        actionError = ApiErrorParser.parse(t).message
                                                    } finally {
                                                        isExecutingAction = false
                                                    }
                                                }
                                            },
                                            enabled = !isExecutingAction && fetchError == null,
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = StatusAmber),
                                            modifier = Modifier
                                                .weight(1f)
                                                .defaultMinSize(minHeight = 48.dp)
                                                .testTag("pause_session_button")
                                        ) {
                                            Text("Pause")
                                        }

                                        Button(
                                            onClick = { actionError = null; showFinishDialog = true },
                                            enabled = !isExecutingAction && fetchError == null,
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                                            modifier = Modifier
                                                .weight(1f)
                                                .defaultMinSize(minHeight = 48.dp)
                                                .testTag("finish_session_button")
                                        ) {
                                            Text("Finish")
                                        }
                                    }
                                }

                                "PAUSED" -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                if (isExecutingAction || fetchError != null) return@Button
                                                isExecutingAction = true
                                                actionVersion++
                                                scope.launch {
                                                    actionError = null
                                                    try {
                                                        session = apiClient.getService().resumeMappingSession(sessionId)
                                                    } catch (e: CancellationException) {
                                                        throw e
                                                    } catch (t: Exception) {
                                                        actionError = ApiErrorParser.parse(t).message
                                                    } finally {
                                                        isExecutingAction = false
                                                    }
                                                }
                                            },
                                            enabled = !isExecutingAction && fetchError == null,
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                                            modifier = Modifier
                                                .weight(1f)
                                                .defaultMinSize(minHeight = 48.dp)
                                                .testTag("resume_session_button")
                                        ) {
                                            Text("Resume")
                                        }

                                        Button(
                                            onClick = { actionError = null; showFinishDialog = true },
                                            enabled = !isExecutingAction && fetchError == null,
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                                            modifier = Modifier
                                                .weight(1f)
                                                .defaultMinSize(minHeight = 48.dp)
                                                .testTag("finish_session_button")
                                        ) {
                                            Text("Finish")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SESSION METADATA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Slate500
                            )
                            StatusBadge(status = currentSession.status)
                        }

                        HorizontalDivider(thickness = 1.dp, color = BorderHairline)

                        KeyValRow(label = "Session ID", value = currentSession.id, isMonospace = true)
                        KeyValRow(label = "Robot ID", value = currentSession.robot_id, isMonospace = true)
                        KeyValRow(label = "SLAM Status", value = currentSession.slam_status ?: "None", isMonospace = true)
                        KeyValRow(label = "Elapsed", value = "${currentSession.elapsed_ms / 1000}s", isMonospace = true)
                        KeyValRow(
                            label = "Pose Updates",
                            value = "${currentSession.pose_update_count}",
                            isMonospace = true
                        )
                        KeyValRow(label = "Map Reference", value = currentSession.map_data_ref ?: "None")
                        KeyValRow(label = "Started At", value = currentSession.started_at ?: "Not started", isMonospace = true)
                        KeyValRow(label = "Paused At", value = currentSession.paused_at ?: "None", isMonospace = true)
                        KeyValRow(
                            label = "Finished At",
                            value = currentSession.finished_at ?: "Ongoing",
                            isMonospace = true,
                            showDivider = false
                        )
                    }
                }
            }
        }

        if (showFinishDialog) {
            AlertDialog(
                onDismissRequest = { if (!isExecutingAction) showFinishDialog = false },
                title = { Text("Finish Mapping Session") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        fetchError?.let { Text("Refresh failed: $it. Close this dialog and retry refresh.", color = StatusRed) }
                        actionError?.let { Text(it, color = StatusRed) }
                        Text("Optionally enter a map data reference identifier:", fontSize = 13.sp, color = Slate600)
                        OutlinedTextField(
                            value = mapDataRefInput,
                            enabled = !isExecutingAction && fetchError == null,
                            onValueChange = { mapDataRefInput = it },
                            placeholder = { Text("map_ref_floor_1") },
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (isExecutingAction || fetchError != null) return@Button
                            actionVersion++
                            val ref = mapDataRefInput.trim().ifBlank { null }
                            isExecutingAction = true
                            scope.launch {
                                actionError = null
                                try {
                                    session = apiClient.getService().finishMappingSession(
                                        sessionId,
                                        FinishSessionRequest(map_data_ref = ref)
                                    )
                                    showFinishDialog = false
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (t: Exception) {
                                    actionError = ApiErrorParser.parse(t).message
                                } finally {
                                    isExecutingAction = false
                                }
                            }
                        },
                        enabled = !isExecutingAction && fetchError == null,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
                    ) {
                        Text(if (isExecutingAction) "Finishing…" else "Finish session")
                    }
                },
                dismissButton = {
                    TextButton(enabled = !isExecutingAction, onClick = { showFinishDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
