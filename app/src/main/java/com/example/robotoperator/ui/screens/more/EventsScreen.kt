package com.example.robotoperator.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import com.example.robotoperator.data.model.SystemEvent
import com.example.robotoperator.ui.components.StatusBadge
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    apiClient: ApiClient,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var events by remember { mutableStateOf<List<SystemEvent>>(emptyList()) }
    var totalCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedSeverityFilter by remember { mutableStateOf("ALL") }

    fun loadEvents() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val service = apiClient.getService()
                val resp = service.getEvents(page = 1, pageSize = 50)
                events = resp.items
                totalCount = resp.total
            } catch (e: Exception) {
                errorMessage = ApiErrorParser.parseErrorMessage(e)
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadEvents()
    }

    val filteredEvents = remember(events, selectedSeverityFilter) {
        if (selectedSeverityFilter == "ALL") {
            events
        } else {
            events.filter { it.severity.equals(selectedSeverityFilter, ignoreCase = true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "System Events",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("events_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { loadEvents() },
                        modifier = Modifier.testTag("events_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh events"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PureWhite
                )
            )
        },
        containerColor = SurfaceCanvas
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Severity filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PureWhite)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL", "INFO", "WARNING", "ERROR", "CRITICAL").forEach { severity ->
                    FilterChip(
                        selected = selectedSeverityFilter == severity,
                        onClick = { selectedSeverityFilter = severity },
                        label = { Text(severity, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("severity_filter_$severity")
                    )
                }
            }

            HorizontalDivider(color = BorderHairline, thickness = 1.dp)

            if (isLoading && events.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ActionBlue)
                }
            } else if (errorMessage != null && events.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "Failed to load events",
                            color = StatusRed,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = { loadEvents() },
                            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
                        ) {
                            Text("Retry")
                        }
                    }
                }
            } else if (filteredEvents.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (events.isEmpty()) "No system events logged" else "No events matching '$selectedSeverityFilter'",
                        color = Slate500,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("events_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredEvents, key = { it.id }) { item ->
                        EventItemCard(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventItemCard(event: SystemEvent) {
    val borderColor = when (event.severity.uppercase()) {
        "CRITICAL", "ERROR" -> StatusRedBorder
        "WARNING" -> StatusAmberBorder
        else -> BorderHairline
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PureWhite, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(status = event.severity)
                Text(
                    text = event.created_at?.replace("T", " ")?.take(19) ?: "Unknown time",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Slate500
                )
            }

            Text(
                text = event.type.ifBlank { "System Event" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Slate900
            )

            if (!event.payload_json.isNullOrBlank()) {
                Text(
                    text = event.payload_json,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = Slate700
                )
            }

            if (!event.robot_id.isNullOrBlank()) {
                Text(
                    text = "Robot: ${event.robot_id}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Slate400
                )
            }
        }
    }
}
