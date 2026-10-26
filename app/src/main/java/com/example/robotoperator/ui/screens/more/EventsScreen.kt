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
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var selectedSeverityFilter by rememberSaveable { mutableStateOf("ALL") }

    var page by remember { mutableStateOf(1) }
    var requestVersion by remember { mutableStateOf(0) }
    var loadJob by remember { mutableStateOf<Job?>(null) }

    fun loadEvents(append: Boolean = false) {
        if (append && isLoading) return
        val version = ++requestVersion
        loadJob?.cancel()
        val requestedPage = if (append) page + 1 else 1
        val severity = selectedSeverityFilter.takeUnless { it == "ALL" }
        isLoading = true
        errorMessage = null
        loadJob = coroutineScope.launch {
            try {
                val resp = apiClient.getService().getEvents(severity = severity, page = requestedPage, pageSize = 50)
                if (version != requestVersion) return@launch
                events = if (append) (events + resp.items).distinctBy { it.id } else resp.items
                totalCount = resp.total
                page = requestedPage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = ApiErrorParser.parseErrorMessage(e)
            } finally { if (version == requestVersion) isLoading = false }
        }
    }

    LaunchedEffect(selectedSeverityFilter) {
        events = emptyList()
        totalCount = 0
        page = 1
        loadEvents()
    }

    val filteredEvents = events

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
                        enabled = !isLoading,
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
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PureWhite)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL", "INFO", "WARN", "ERROR", "CRITICAL").forEach { severity ->
                    FilterChip(
                        selected = selectedSeverityFilter == severity,
                        onClick = { selectedSeverityFilter = severity },
                        label = { Text(if (severity == "WARN") "Warning" else severity.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("severity_filter_$severity")
                    )
                }
            }

            HorizontalDivider(color = BorderHairline, thickness = 1.dp)
            if (isLoading && events.isNotEmpty()) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (events.isNotEmpty()) Text("${events.size} of $totalCount events", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            if (errorMessage != null && events.isNotEmpty()) {
                Text(errorMessage!!, color = StatusRed, modifier = Modifier.padding(horizontal = 16.dp))
                TextButton(enabled = !isLoading, onClick = { loadEvents() }) { Text("Retry refresh") }
            }

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
                    if (events.size < totalCount) item {
                        OutlinedButton(onClick = { loadEvents(append = true) }, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
                            Text(if (isLoading) "Loading…" else "Load more events")
                        }
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
        "WARN", "WARNING" -> StatusAmberBorder
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

            if (event.payload_json.isNotEmpty()) {
                Text(
                    text = JSONObject(event.payload_json).toString(2),
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
                    color = Slate600
                )
            }
        }
    }
}
