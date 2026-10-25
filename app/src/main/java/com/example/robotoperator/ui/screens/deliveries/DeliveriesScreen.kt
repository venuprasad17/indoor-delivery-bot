package com.example.robotoperator.ui.screens.deliveries

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.robotoperator.data.model.isDeliveryLifecycleComplete
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
import com.example.robotoperator.data.model.CreateDeliveryRequest
import com.example.robotoperator.data.model.Delivery
import com.example.robotoperator.data.model.Destination
import com.example.robotoperator.data.model.isAdministratorRole
import com.example.robotoperator.data.model.isValidDeliveryRoute
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.robotoperator.state.AuthManager
import com.example.robotoperator.state.RobotStateHolder
import com.example.robotoperator.ui.components.StatusBadge
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun DeliveriesScreen(
    apiClient: ApiClient,
    stateHolder: RobotStateHolder,
    settingsStore: ServerSettingsStore,
    authManager: AuthManager,
    onSelectDelivery: (String) -> Unit
) {
    var deliveries by remember { mutableStateOf<List<Delivery>>(emptyList()) }
    var destinations by remember { mutableStateOf<List<Destination>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isCreating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var selectedDestinationId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSourceId by rememberSaveable { mutableStateOf<String?>(null) }

    var search by rememberSaveable { mutableStateOf("") }
    var destinationSearch by rememberSaveable { mutableStateOf("") }
    var createError by remember { mutableStateOf<String?>(null) }
    val filteredDeliveries = deliveries.filter { search.isBlank() ||
        it.id.contains(search, true) || it.destination_name.orEmpty().contains(search, true) || it.source_name.orEmpty().contains(search, true) ||
        (if (isDeliveryLifecycleComplete(it)) "Completed" else it.status).contains(search, true) }
    val robotStatus by stateHolder.robotStatus.collectAsState()
    val currentUser by authManager.currentUser.collectAsState()
    val isAdmin = isAdministratorRole(currentUser?.role)
    val scope = rememberCoroutineScope()

    fun loadData() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val service = apiClient.getService()
                // Role-based list: ADMIN gets full deliveries list; USER gets only their own deliveries
                deliveries = if (isAdmin) {
                    service.getDeliveries()
                } else {
                    service.getMyDeliveries()
                }
                destinations = service.getDestinations()
            } catch (e: CancellationException) { throw e
            } catch (t: Exception) {
                errorMessage = ApiErrorParser.parse(t).message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(currentUser?.role) {
        loadData()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedDestinationId = null
                    selectedSourceId = null
                    destinationSearch = ""
                    createError = null
                    showCreateDialog = true
                },
                containerColor = ActionBlue,
                contentColor = PureWhite,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.testTag("create_delivery_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Delivery",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New delivery", fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
                    text = if (isAdmin) "Deliveries" else "My deliveries",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Slate500
                )
                IconButton(
                    onClick = { loadData() },
                    enabled = !isLoading,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Slate600,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (!errorMessage.isNullOrBlank()) {
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

            OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Search pickup, destination, status or ID") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("delivery_search"))
            if (!isAdmin) Text("Use New delivery to choose pickup and destination. An operator dispatches the robot.", style = MaterialTheme.typography.bodyMedium, color = Slate600)
            if (errorMessage != null) TextButton(onClick = { loadData() }) { Text("Retry") }
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Loading deliveries...",
                            fontSize = 14.sp,
                            color = Slate500
                        )
                    }
                }
                filteredDeliveries.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (errorMessage != null) "Deliveries could not be loaded. Tap Retry to try again." else if (search.isNotBlank()) "No matching deliveries. Try a different search." else if (isAdmin) "No deliveries yet. Create a delivery to choose its destination." else "No deliveries assigned to your account yet.",
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
                        items(filteredDeliveries, key = { it.id }) { delivery ->
                            Card(
                                shape = RoundedCornerShape(6.dp),
                                colors = CardDefaults.cardColors(containerColor = PureWhite),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
                                    .clickable { onSelectDelivery(delivery.id) }
                                    .testTag("delivery_item_${delivery.id}")
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
                                            text = listOfNotNull(delivery.source_name, delivery.destination_name).joinToString(" → ").ifBlank { "Delivery ${delivery.id.take(8)}" },
                                            modifier = Modifier.weight(1f),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Slate900
                                        )
                                        StatusBadge(status = if (isDeliveryLifecycleComplete(delivery)) "COMPLETED" else delivery.status)
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = "ID: ${delivery.id.take(8)} • ${delivery.history.size} history steps",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Slate500
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { if (!isCreating) showCreateDialog = false },
                title = { Text("Create delivery") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Choose pickup and destination on the same mapped floor. This creates a request; dispatch is a separate action.", style = MaterialTheme.typography.bodyMedium)
                        createError?.let { Text(it, color = StatusRed, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                        if (destinations.isEmpty()) {
                            Text(errorMessage ?: "No destinations found. Create one in More > Destinations first.", fontSize = 12.sp, color = StatusRed)
                            TextButton(enabled = !isLoading, onClick = { loadData() }) { Text("Reload destinations") }
                        } else {
                            DeliveryLocationSelection(
                                locations = destinations,
                                sourceId = selectedSourceId,
                                destinationId = selectedDestinationId,
                                enabled = !isCreating,
                                onSourceSelected = { selectedSourceId = it; selectedDestinationId = null },
                                onDestinationSelected = { selectedDestinationId = it }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val destId = selectedDestinationId ?: return@Button
                            val source = destinations.firstOrNull { it.id == selectedSourceId }
                            val destination = destinations.firstOrNull { it.id == destId }
                            if (!isValidDeliveryRoute(source, destination)) {
                                createError = "Choose distinct pickup and destination locations on the same robot map."
                                return@Button
                            }
                            val robotId = source!!.robot_id!!

                            if (isCreating) return@Button
                            isCreating = true
                            createError = null
                            scope.launch {
                                try {
                                    val newDel = apiClient.getService().createDelivery(
                                        CreateDeliveryRequest(
                                            destination_id = destId,
                                            source_id = source.id,
                                            robot_id = robotId
                                        )
                                    )
                                    showCreateDialog = false
                                    loadData()
                                    onSelectDelivery(newDel.id)
                                } catch (e: CancellationException) { throw e
                                } catch (t: Exception) {
                                    val parsed = ApiErrorParser.parse(t)
                                    createError = if (parsed.statusCode == 403) {
                                        "You don't have access to this robot. Ask an admin to grant access."
                                    } else {
                                        parsed.message
                                    }
                                } finally {
                                    isCreating = false
                                }
                            }
                        },
                        enabled = isValidDeliveryRoute(destinations.firstOrNull { it.id == selectedSourceId }, destinations.firstOrNull { it.id == selectedDestinationId }) && !isCreating,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
                    ) {
                        Text(if (isCreating) "Creating…" else "Create delivery")
                    }
                },
                dismissButton = {
                    TextButton(enabled = !isCreating, onClick = { showCreateDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
