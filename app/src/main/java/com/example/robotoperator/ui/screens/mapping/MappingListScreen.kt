package com.example.robotoperator.ui.screens.mapping

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.robotoperator.data.model.CreateSessionRequest
import com.example.robotoperator.data.model.MappingSession
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.robotoperator.ui.components.StatusBadge
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MappingListScreen(
    apiClient: ApiClient,
    settingsStore: ServerSettingsStore,
    onSelectSession: (String) -> Unit
) {
    var sessions by remember { mutableStateOf<List<MappingSession>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isCreating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var activeConflictSessionId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadSessions() {
        scope.launch {
            isLoading = true
            errorMessage = null
            activeConflictSessionId = null
            try {
                val list = apiClient.getService().getMappingSessions()
                sessions = list
            } catch (t: Throwable) {
                val parsed = ApiErrorParser.parse(t)
                errorMessage = parsed.message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadSessions()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val robotId = settingsStore.cachedRobotId
                    if (robotId.isNullOrBlank()) {
                        errorMessage = "Cannot create session: No robot registered or cached yet."
                        return@FloatingActionButton
                    }
                    scope.launch {
                        isCreating = true
                        errorMessage = null
                        activeConflictSessionId = null
                        try {
                            val newSession = apiClient.getService().createMappingSession(
                                CreateSessionRequest(robot_id = robotId)
                            )
                            settingsStore.activeSessionId = newSession.id
                            onSelectSession(newSession.id)
                        } catch (t: Throwable) {
                            val parsed = ApiErrorParser.parse(t)
                            if (parsed.statusCode == 409 && !parsed.activeSessionId.isNullOrBlank()) {
                                activeConflictSessionId = parsed.activeSessionId
                            }
                            errorMessage = parsed.message
                        } finally {
                            isCreating = false
                        }
                    }
                },
                containerColor = ActionBlue,
                contentColor = PureWhite,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.testTag("create_session_fab")
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = PureWhite,
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Session",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New session", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        containerColor = SurfaceCanvas
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MAPPING SESSIONS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Slate500
                )
                IconButton(
                    onClick = { loadSessions() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Slate600,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (activeConflictSessionId != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(StatusAmberBg)
                        .border(1.dp, StatusAmberBorder, RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Session already active: $activeConflictSessionId",
                            color = StatusAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { onSelectSession(activeConflictSessionId!!) },
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                            modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                        ) {
                            Text("Jump to active session", fontSize = 12.sp)
                        }
                    }
                }
            } else if (!errorMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(StatusRedBg)
                        .border(1.dp, StatusRedBorder, RoundedCornerShape(4.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = errorMessage!!,
                        color = StatusRed,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Loading mapping sessions...",
                            fontSize = 14.sp,
                            color = Slate500
                        )
                    }
                }
                sessions.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No mapping sessions yet",
                            fontSize = 14.sp,
                            color = Slate500
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sessions, key = { it.id }) { session ->
                            Card(
                                shape = RoundedCornerShape(6.dp),
                                colors = CardDefaults.cardColors(containerColor = PureWhite),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
                                    .clickable { onSelectSession(session.id) }
                                    .testTag("session_item_${session.id}")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = session.map_data_ref ?: "Session ${session.id.take(8)}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Slate900
                                        )
                                        StatusBadge(status = session.status)
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = "ID: ${session.id}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Slate500
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Elapsed: ${session.elapsed_ms / 1000}s",
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Slate700
                                        )
                                        Text(
                                            text = "Updates: ${session.pose_update_count}",
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Slate700
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
