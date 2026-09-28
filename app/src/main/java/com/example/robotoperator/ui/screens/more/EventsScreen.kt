package com.example.robotoperator.ui.screens.more

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import com.example.robotoperator.data.model.EventItem
import com.example.robotoperator.ui.components.BadgeColorType
import com.example.robotoperator.ui.components.StatusBadge
import com.example.robotoperator.ui.components.getStatusBadgeType
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    apiClient: ApiClient,
    onBack: () -> Unit
) {
    var events by remember { mutableStateOf<List<EventItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedSeverity by remember { mutableStateOf<String?>(null) } // null = ALL
    var currentPage by remember { mutableIntStateOf(1) }
    var totalCount by remember { mutableIntStateOf(0) }
    val expandedEventIds = remember { mutableStateMapOf<String, Boolean>() }

    val scope = rememberCoroutineScope()

    val fetchEvents: suspend () -> Unit = {
        try {
            val service = apiClient.getService()
            val res = withContext(Dispatchers.IO) {
                service.getEvents(
                    severity = selectedSeverity,
                    page = currentPage,
                    pageSize = 50
                )
            }
            events = res.items
            totalCount = res.total
            errorMessage = null
        } catch (e: Exception) {
            val err = ApiErrorParser.parse(e)
            errorMessage = err.message
        } finally {
            isLoading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(selectedSeverity, currentPage) {
        fetchEvents()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Events", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Slate900) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("events_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Slate700)
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
                .testTag("events_screen")
        ) {
            // Severity Filter Chips
            Surface(color = PureWhite, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val severities = listOf("ALL", "INFO", "WARN", "ERROR", "CRITICAL")
                    severities.forEach { sev ->
                        val isSelected = (sev == "ALL" && selectedSeverity == null) || (selectedSeverity == sev)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedSeverity = if (sev == "ALL") null else sev
                                currentPage = 1
                            },
                            label = { Text(sev, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                            shape = RoundedCornerShape(4.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryBlueLight,
                                selectedLabelColor = PrimaryBlueDark
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) PrimaryBlue else Slate300
                            ),
                            modifier = Modifier.testTag("chip_severity_${sev.lowercase()}")
                        )
                    }
                }
            }

            HorizontalDivider(color = Slate200, thickness = 1.dp)

            if (!errorMessage.isNullOrEmpty()) {
                Text(
                    text = errorMessage!!,
                    color = StatusRedText,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StatusRedBg)
                        .padding(12.dp)
                )
                HorizontalDivider(color = StatusRedBorder, thickness = 1.dp)
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    scope.launch { fetchEvents() }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                if (isLoading && events.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = PrimaryBlue)
                    }
                } else if (events.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(text = "No events logged for current filter", color = Slate500, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("events_list"),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(events, key = { it.id }) { event ->
                            val isExpanded = expandedEventIds[event.id] == true

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedEventIds[event.id] = !isExpanded }
                                    .testTag("event_row_${event.id}"),
                                colors = CardDefaults.cardColors(containerColor = PureWhite),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            StatusBadge(
                                                text = event.severity,
                                                type = when (event.severity.uppercase()) {
                                                    "CRITICAL", "ERROR" -> BadgeColorType.RED
                                                    "WARN" -> BadgeColorType.AMBER
                                                    "INFO" -> BadgeColorType.GREEN
                                                    else -> BadgeColorType.GREY
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = event.type,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                fontFamily = FontFamily.Monospace,
                                                color = Slate900
                                            )
                                        }

                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Expand",
                                            tint = Slate500,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = event.created_at ?: "",
                                        fontSize = 11.sp,
                                        color = Slate400
                                    )

                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 10.dp)
                                                .background(Slate100, shape = RoundedCornerShape(4.dp))
                                                .padding(10.dp)
                                        ) {
                                            Text(
                                                text = "Payload:",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Slate700
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = event.payload_json?.toString() ?: "{}",
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = Slate800
                                            )
                                            if (!event.robot_id.isNullOrEmpty()) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "Robot ID: ${event.robot_id}",
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = Slate600
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
    }
}
