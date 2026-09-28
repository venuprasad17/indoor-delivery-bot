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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.api.ApiErrorParser
import com.example.robotoperator.data.model.FinishSessionRequest
import com.example.robotoperator.data.model.MappingSession
import com.example.robotoperator.data.model.Pose
import com.example.robotoperator.state.RobotStateHolder
import com.example.robotoperator.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MappingDetailScreen(
    sessionId: String,
    apiClient: ApiClient,
    robotStore: RobotStateHolder,
    onBack: () -> Unit
) {
    var session by remember { mutableStateOf<MappingSession?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isActionRunning by remember { mutableStateOf(false) }
    var inlineError by remember { mutableStateOf<String?>(null) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var mapDataRefInput by remember { mutableStateOf("") }
    var secondsSinceUpdate by remember { mutableIntStateOf(0) }
    var localElapsedMs by remember { mutableLongStateOf(0L) }

    // Pose trail accumulated locally while this screen is open
    var localPoseTrail by remember { mutableStateOf<List<Pose>>(emptyList()) }

    val scope = rememberCoroutineScope()

    // Load initial session data
    val fetchSession: suspend () -> Unit = {
        try {
            val service = apiClient.getService()
            val data = withContext(Dispatchers.IO) {
                service.getMappingSession(sessionId)
            }
            session = data
            localElapsedMs = data.elapsed_ms
            secondsSinceUpdate = 0
            inlineError = null

            // Accumulate pose trail
            data.last_pose_json?.let { p ->
                if (localPoseTrail.isEmpty() || localPoseTrail.last() != p) {
                    localPoseTrail = localPoseTrail + p
                }
            }
        } catch (e: Exception) {
            val err = ApiErrorParser.parse(e)
            inlineError = err.message
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(sessionId) {
        fetchSession()
    }

    // 1-second ticker for local elapsed time and "Updated Ns ago"
    LaunchedEffect(session?.status) {
        while (isActive) {
            delay(1000L)
            secondsSinceUpdate += 1
            if (session?.status == "MAPPING") {
                localElapsedMs += 1000L
            }
        }
    }

    // 2-second poller while MAPPING or PAUSED; stops when FINISHED
    LaunchedEffect(session?.status) {
        val st = session?.status
        if (st == "MAPPING" || st == "PAUSED") {
            while (isActive) {
                delay(2000L)
                fetchSession()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Session ${session?.shortId() ?: sessionId.take(8)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = Slate900
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        session?.let {
                            StatusBadge(
                                text = it.status,
                                type = getStatusBadgeType(it.status),
                                testTag = "detail_status_badge"
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Slate700
                        )
                    }
                },
                actions = {
                    Text(
                        text = "Updated ${secondsSinceUpdate}s ago",
                        fontSize = 11.sp,
                        color = Slate500,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureWhite)
            )
        },
        containerColor = Slate50
    ) { innerPadding ->
        if (isLoading && session == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = PrimaryBlue
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
                Text(
                    text = inlineError ?: "Session not found",
                    color = StatusRedText,
                    fontSize = 14.sp
                )
            }
        } else {
            val current = session!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Inline Error Display
                if (!inlineError.isNullOrEmpty()) {
                    Text(
                        text = inlineError!!,
                        color = StatusRedText,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StatusRedBg, shape = RoundedCornerShape(4.dp))
                            .border(1.dp, StatusRedBorder, shape = RoundedCornerShape(4.dp))
                            .padding(10.dp)
                            .testTag("session_error_banner")
                    )
                }

                // Action Buttons Bar (strictly enabled by status)
                Card(
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Session Controls",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate700
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // IDLE -> Start button
                            if (current.status == "IDLE") {
                                Button(
                                    onClick = {
                                        isActionRunning = true
                                        inlineError = null
                                        scope.launch {
                                            try {
                                                val s = apiClient.getService()
                                                val res = withContext(Dispatchers.IO) {
                                                    s.startMappingSession(sessionId)
                                                }
                                                session = res
                                            } catch (e: Exception) {
                                                inlineError = ApiErrorParser.parse(e).message
                                            } finally {
                                                isActionRunning = false
                                            }
                                        }
                                    },
                                    enabled = !isActionRunning,
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("action_start_session")
                                ) {
                                    Text("Start mapping")
                                }
                            }

                            // MAPPING -> Pause & Finish
                            if (current.status == "MAPPING") {
                                Button(
                                    onClick = {
                                        isActionRunning = true
                                        inlineError = null
                                        scope.launch {
                                            try {
                                                val s = apiClient.getService()
                                                val res = withContext(Dispatchers.IO) {
                                                    s.pauseMappingSession(sessionId)
                                                }
                                                session = res
                                            } catch (e: Exception) {
                                                inlineError = ApiErrorParser.parse(e).message
                                            } finally {
                                                isActionRunning = false
                                            }
                                        }
                                    },
                                    enabled = !isActionRunning,
                                    colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("action_pause_session")
                                ) {
                                    Text("Pause")
                                }

                                Button(
                                    onClick = { showFinishDialog = true },
                                    enabled = !isActionRunning,
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("action_finish_session")
                                ) {
                                    Text("Finish")
                                }
                            }

                            // PAUSED -> Resume & Finish
                            if (current.status == "PAUSED") {
                                Button(
                                    onClick = {
                                        isActionRunning = true
                                        inlineError = null
                                        scope.launch {
                                            try {
                                                val s = apiClient.getService()
                                                val res = withContext(Dispatchers.IO) {
                                                    s.resumeMappingSession(sessionId)
                                                }
                                                session = res
                                            } catch (e: Exception) {
                                                inlineError = ApiErrorParser.parse(e).message
                                            } finally {
                                                isActionRunning = false
                                            }
                                        }
                                    },
                                    enabled = !isActionRunning,
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("action_resume_session")
                                ) {
                                    Text("Resume")
                                }

                                Button(
                                    onClick = { showFinishDialog = true },
                                    enabled = !isActionRunning,
                                    colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("action_finish_session")
                                ) {
                                    Text("Finish")
                                }
                            }

                            // FINISHED -> no actions
                            if (current.status == "FINISHED") {
                                Text(
                                    text = "This mapping session is finished and saved.",
                                    fontSize = 13.sp,
                                    color = Slate500,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Metrics List
                Card(
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Telemetry & Metrics",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate900,
                            modifier = Modifier.padding(16.dp, 12.dp)
                        )
                        HorizontalDivider(color = Slate200, thickness = 1.dp)

                        KeyValRow(
                            label = "Session ID",
                            value = current.id,
                            isMonospace = true,
                            testTag = "detail_session_id"
                        )
                        KeyValRow(
                            label = "Elapsed",
                            value = formatElapsed(localElapsedMs),
                            isMonospace = true,
                            testTag = "detail_elapsed"
                        )
                        KeyValRow(
                            label = "Pose updates",
                            value = "${current.pose_update_count}",
                            testTag = "detail_pose_count"
                        )
                        KeyValRow(
                            label = "Last known pose",
                            value = current.last_pose_json?.let {
                                "(${it.formattedX()}, ${it.formattedY()}, ${it.formattedTheta()} rad)"
                            } ?: "None",
                            isMonospace = true,
                            testTag = "detail_last_pose"
                        )
                        KeyValRow(
                            label = "SLAM status",
                            value = current.slam_status ?: "None",
                            trailingBadge = current.slam_status?.let {
                                { StatusBadge(text = it, type = getStatusBadgeType(it)) }
                            }
                        )
                        KeyValRow(label = "Started at", value = current.started_at ?: "Not started")
                        KeyValRow(label = "Paused at", value = current.paused_at ?: "None")
                        KeyValRow(label = "Finished at", value = current.finished_at ?: "None")
                        KeyValRow(label = "Map reference", value = current.map_data_ref ?: "None")
                    }
                }

                // Pose Trail Canvas
                val activePose = current.last_pose_json ?: Pose()
                PosePlotCanvas(
                    currentPose = activePose,
                    poseTrail = localPoseTrail,
                    heightDp = 260
                )
            }
        }
    }

    // Finish Confirmation Dialog with optional map_data_ref
    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = {
                Text(
                    text = "Finish mapping session?",
                    style = MaterialTheme.typography.titleMedium,
                    color = Slate900
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Completing this session will freeze SLAM landmarks and finalize the map data.",
                        fontSize = 13.sp,
                        color = Slate600
                    )
                    OutlinedTextField(
                        value = mapDataRefInput,
                        onValueChange = { mapDataRefInput = it },
                        label = { Text("Map reference (optional)") },
                        placeholder = { Text("e.g. floor_2_grid") },
                        singleLine = true,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("map_data_ref_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinishDialog = false
                        isActionRunning = true
                        inlineError = null
                        scope.launch {
                            try {
                                val s = apiClient.getService()
                                val ref = mapDataRefInput.trim().ifEmpty { null }
                                val res = withContext(Dispatchers.IO) {
                                    s.finishMappingSession(sessionId, FinishSessionRequest(map_data_ref = ref))
                                }
                                session = res
                                robotStore.setActiveSessionId(null)
                            } catch (e: Exception) {
                                inlineError = ApiErrorParser.parse(e).message
                            } finally {
                                isActionRunning = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier.testTag("confirm_finish_button")
                ) {
                    Text("Confirm finish")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFinishDialog = false },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("Cancel", color = Slate600)
                }
            },
            containerColor = PureWhite,
            shape = RoundedCornerShape(6.dp)
        )
    }
}
