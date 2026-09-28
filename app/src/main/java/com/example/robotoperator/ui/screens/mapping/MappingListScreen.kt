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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.example.robotoperator.data.model.CreateSessionRequest
import com.example.robotoperator.data.model.MappingSession
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.robotoperator.state.RobotStateHolder
import com.example.robotoperator.ui.components.BadgeColorType
import com.example.robotoperator.ui.components.StatusBadge
import com.example.robotoperator.ui.components.getStatusBadgeType
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SessionFilter {
    ALL,
    ACTIVE,
    FINISHED
}

fun formatElapsed(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val hrs = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return String.format(java.util.Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MappingListScreen(
    apiClient: ApiClient,
    robotStore: RobotStateHolder,
    settingsStore: ServerSettingsStore,
    onNavigateDetail: (String) -> Unit
) {
    var sessions by remember { mutableStateOf<List<MappingSession>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var activeConflictId by remember { mutableStateOf<String?>(null) }
    var currentFilter by remember { mutableStateOf(SessionFilter.ALL) }

    val robotStatus by robotStore.robotStatus.collectAsState()
    val scope = rememberCoroutineScope()

    val fetchSessions: suspend () -> Unit = {
        try {
            val service = apiClient.getService()
            val list = withContext(Dispatchers.IO) {
                service.getMappingSessions(skip = 0, limit = 100)
            }
            sessions = list
            // Update active session in store
            val active = list.firstOrNull { it.status == "MAPPING" || it.status == "PAUSED" }
            robotStore.setActiveSessionId(active?.id)
            settingsStore.activeSessionId = active?.id
            errorMessage = null
        } catch (e: Exception) {
            val err = ApiErrorParser.parse(e)
            errorMessage = err.message
        } finally {
            isLoading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        fetchSessions()
    }

    val filteredSessions = remember(sessions, currentFilter) {
        when (currentFilter) {
            SessionFilter.ALL -> sessions
            SessionFilter.ACTIVE -> sessions.filter { it.status == "MAPPING" || it.status == "PAUSED" }
            SessionFilter.FINISHED -> sessions.filter { it.status == "FINISHED" || it.status == "IDLE" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .testTag("mapping_list_screen")
    ) {
        // Controls Header
        Surface(
            color = PureWhite,
            border = null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mapping Sessions",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate900
                    )

                    Button(
                        onClick = {
                            val rId = robotStatus.id.ifEmpty { settingsStore.cachedRobotId ?: "" }
                            if (rId.isEmpty()) {
                                errorMessage = "No robot registered to create session"
                                return@Button
                            }
                            isCreating = true
                            errorMessage = null
                            activeConflictId = null
                            scope.launch {
                                try {
                                    val service = apiClient.getService()
                                    val created = withContext(Dispatchers.IO) {
                                        service.createMappingSession(CreateSessionRequest(robot_id = rId))
                                    }
                                    robotStore.setActiveSessionId(created.id)
                                    onNavigateDetail(created.id)
                                } catch (e: Exception) {
                                    val err = ApiErrorParser.parse(e)
                                    errorMessage = err.message
                                    activeConflictId = err.activeSessionId
                                } finally {
                                    isCreating = false
                                }
                            }
                        },
                        enabled = !isCreating,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("new_session_button")
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = PureWhite
                            )
                        } else {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New session", fontSize = 13.sp)
                        }
                    }
                }

                // Segmented Filter: All / Active / Finished
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Slate100, shape = RoundedCornerShape(4.dp))
                        .padding(2.dp)
                ) {
                    SessionFilter.entries.forEach { filter ->
                        val isSelected = currentFilter == filter
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isSelected) PureWhite else Slate100,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable { currentFilter = filter }
                                .padding(vertical = 6.dp)
                                .testTag("filter_${filter.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filter.name.lowercase().replaceFirstChar { it.uppercase() },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) Slate900 else Slate600
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Slate200, thickness = 1.dp)

        // Error message banner (if active session exists or other error)
        if (!errorMessage.isNullOrEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StatusRedBg)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = errorMessage!!,
                    color = StatusRedText,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                if (activeConflictId != null) {
                    TextButton(
                        onClick = { onNavigateDetail(activeConflictId!!) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Open active session", color = PrimaryBlue, fontSize = 12.sp)
                    }
                }
            }
            HorizontalDivider(color = StatusRedBorder, thickness = 1.dp)
        }

        // List Content with PullToRefreshBox
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch { fetchSessions() }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading && sessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryBlue
                    )
                }
            } else if (filteredSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No mapping sessions yet",
                        color = Slate500,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("sessions_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSessions, key = { it.id }) { session ->
                        MappingSessionRow(
                            session = session,
                            onClick = { onNavigateDetail(session.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MappingSessionRow(
    session: MappingSession,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("session_row_${session.id}"),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = session.shortId(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Slate900
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusBadge(
                        text = session.status,
                        type = getStatusBadgeType(session.status)
                    )
                }

                if (!session.slam_status.isNullOrEmpty()) {
                    StatusBadge(
                        text = session.slam_status,
                        type = getStatusBadgeType(session.slam_status)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Elapsed: ${formatElapsed(session.elapsed_ms)}",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Slate700
                )
                Text(
                    text = "${session.pose_update_count} poses",
                    fontSize = 13.sp,
                    color = Slate500
                )
            }

            if (!session.started_at.isNullOrEmpty()) {
                Text(
                    text = "Started: ${session.started_at}",
                    fontSize = 12.sp,
                    color = Slate400
                )
            }
        }
    }
}
