package com.example.robotoperator.ui.screens.deliveries

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.window.Dialog
import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.api.ApiErrorParser
import com.example.robotoperator.data.model.CreateDeliveryRequest
import com.example.robotoperator.data.model.Delivery
import com.example.robotoperator.data.model.Destination
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.robotoperator.state.RobotStateHolder
import com.example.robotoperator.ui.components.BadgeColorType
import com.example.robotoperator.ui.components.StatusBadge
import com.example.robotoperator.ui.components.getStatusBadgeType
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveriesScreen(
    apiClient: ApiClient,
    robotStore: RobotStateHolder,
    settingsStore: ServerSettingsStore,
    onNavigateDetail: (String) -> Unit
) {
    var deliveries by remember { mutableStateOf<List<Delivery>>(emptyList()) }
    var destinations by remember { mutableStateOf<List<Destination>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showNewDeliveryDialog by remember { mutableStateOf(false) }

    val robotStatus by robotStore.robotStatus.collectAsState()
    val scope = rememberCoroutineScope()

    // Can create delivery only if robot is ONLINE and AUTONOMOUS
    val canCreateDelivery = robotStatus.status == "ONLINE" && robotStatus.control_mode == "AUTONOMOUS"
    val disabledReason = when {
        robotStatus.status != "ONLINE" -> "Robot is offline"
        robotStatus.control_mode != "AUTONOMOUS" -> "Robot is not in autonomous mode"
        else -> ""
    }

    val fetchDeliveries: suspend () -> Unit = {
        try {
            val service = apiClient.getService()
            val list = withContext(Dispatchers.IO) { service.getDeliveries() }
            deliveries = list

            val active = list.firstOrNull { it.status != "HOME" && it.status != "DELIVERED" && it.status != "EMERGENCY_STOP" }
            robotStore.setActiveDeliveryId(active?.id)

            // Also load destinations for selection
            val dests = withContext(Dispatchers.IO) { service.getDestinations() }
            destinations = dests
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
        fetchDeliveries()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .testTag("deliveries_screen")
    ) {
        Surface(color = PureWhite, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Deliveries",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate900
                    )

                    Button(
                        onClick = { showNewDeliveryDialog = true },
                        enabled = canCreateDelivery,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("new_delivery_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New delivery", fontSize = 13.sp)
                    }
                }

                if (!canCreateDelivery) {
                    Text(
                        text = "New delivery unavailable: $disabledReason",
                        color = StatusAmberText,
                        fontSize = 12.sp,
                        modifier = Modifier.testTag("delivery_disabled_reason")
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
                    .testTag("deliveries_error_banner")
            )
            HorizontalDivider(color = StatusRedBorder, thickness = 1.dp)
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch { fetchDeliveries() }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading && deliveries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryBlue
                    )
                }
            } else if (deliveries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No deliveries scheduled",
                        color = Slate500,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("deliveries_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(deliveries, key = { it.id }) { delivery ->
                        DeliveryItemRow(
                            delivery = delivery,
                            destinations = destinations,
                            onClick = { onNavigateDetail(delivery.id) }
                        )
                    }
                }
            }
        }
    }

    if (showNewDeliveryDialog) {
        NewDeliveryDialog(
            destinations = destinations,
            onDismiss = { showNewDeliveryDialog = false },
            onConfirm = { selectedDestId ->
                val rId = robotStatus.id.ifEmpty { settingsStore.cachedRobotId ?: "" }
                scope.launch {
                    try {
                        val s = apiClient.getService()
                        val created = withContext(Dispatchers.IO) {
                            s.createDelivery(CreateDeliveryRequest(destination_id = selectedDestId, robot_id = rId))
                        }
                        showNewDeliveryDialog = false
                        onNavigateDetail(created.id)
                    } catch (e: Exception) {
                        val err = ApiErrorParser.parse(e)
                        errorMessage = err.message
                    }
                }
            }
        )
    }
}

@Composable
private fun DeliveryItemRow(
    delivery: Delivery,
    destinations: List<Destination>,
    onClick: () -> Unit
) {
    val destName = destinations.firstOrNull { it.id == delivery.destination_id }?.name
        ?: delivery.destination_name
        ?: delivery.destination_id?.take(8)
        ?: "Unknown"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("delivery_row_${delivery.id}"),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        shape = RoundedCornerShape(6.dp),
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
                Text(
                    text = "Delivery #${delivery.id.take(8)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Slate900
                )
                StatusBadge(
                    text = delivery.status,
                    type = getStatusBadgeType(delivery.status)
                )
            }

            Text(
                text = "Destination: $destName",
                fontSize = 13.sp,
                color = Slate700
            )

            if (!delivery.created_at.isNullOrEmpty()) {
                Text(
                    text = "Created: ${delivery.created_at}",
                    fontSize = 12.sp,
                    color = Slate400
                )
            }
        }
    }
}

@Composable
private fun NewDeliveryDialog(
    destinations: List<Destination>,
    onDismiss: () -> Unit,
    onConfirm: (destinationId: String) -> Unit
) {
    var selectedDestId by remember {
        mutableStateOf(destinations.firstOrNull { !it.is_home }?.id ?: destinations.firstOrNull()?.id ?: "")
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = PureWhite,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate300),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("new_delivery_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Create new delivery",
                    style = MaterialTheme.typography.titleMedium,
                    color = Slate900,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Select target destination point for autonomous transit:",
                    fontSize = 13.sp,
                    color = Slate600
                )

                if (destinations.isEmpty()) {
                    Text(
                        text = "No destinations registered yet. Create a destination in the Destinations tab first.",
                        color = StatusAmberText,
                        fontSize = 13.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        destinations.forEach { dest ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedDestId = dest.id }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedDestId == dest.id,
                                    onClick = { selectedDestId = dest.id }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = dest.name + if (dest.is_home) " (Home)" else "",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Slate900
                                    )
                                    Text(
                                        text = "Map: ${dest.map_id} | (${String.format(java.util.Locale.US, "%.2f", dest.x)}, ${String.format(java.util.Locale.US, "%.2f", dest.y)})",
                                        fontSize = 11.sp,
                                        color = Slate500
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(4.dp)) {
                        Text("Cancel", color = Slate600)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(selectedDestId) },
                        enabled = selectedDestId.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.testTag("confirm_create_delivery")
                    ) {
                        Text("Dispatch mission")
                    }
                }
            }
        }
    }
}
