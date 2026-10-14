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

    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedDestinationId by remember { mutableStateOf<String?>(null) }

    val robotStatus by stateHolder.robotStatus.collectAsState()
    val currentUser by authManager.currentUser.collectAsState()
    val scope = rememberCoroutineScope()

    fun loadData() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val service = apiClient.getService()
                // Role-based list: ADMIN gets full deliveries list; USER gets only their own deliveries
                deliveries = if (currentUser?.role == "ADMIN") {
                    service.getDeliveries()
                } else {
                    service.getMyDeliveries()
                }
                destinations = service.getDestinations()
                if (destinations.isNotEmpty() && selectedDestinationId == null) {
                    selectedDestinationId = destinations.first().id
                }
            } catch (t: Throwable) {
                errorMessage = ApiErrorParser.parse(t).message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (destinations.isEmpty()) {
                        scope.launch {
                            try {
                                destinations = apiClient.getService().getDestinations()
                                if (destinations.isNotEmpty()) selectedDestinationId = destinations.first().id
                            } catch (_: Exception) {}
                            showCreateDialog = true
                        }
                    } else {
                        showCreateDialog = true
                    }
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
                    text = "DELIVERY DISPATCH",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Slate500
                )
                IconButton(
                    onClick = { loadData() },
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
                deliveries.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No deliveries registered",
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
                        items(deliveries, key = { it.id }) { delivery ->
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
                                            text = delivery.destination_name ?: "Dest: ${delivery.destination_id?.take(8) ?: "Unknown"}",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Slate900
                                        )
                                        StatusBadge(status = delivery.status)
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
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Dispatch New Delivery") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Select Destination:", fontSize = 13.sp, color = Slate700)
                        if (destinations.isEmpty()) {
                            Text("No destinations found. Create a destination first.", fontSize = 12.sp, color = StatusRed)
                        } else {
                            destinations.forEach { dest ->
                                val isSelected = selectedDestinationId == dest.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(
                                            1.dp,
                                            if (isSelected) ActionBlue else BorderHairline,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .background(if (isSelected) ActionBlueSubtle else PureWhite)
                                        .clickable { selectedDestinationId = dest.id }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedDestinationId = dest.id },
                                        colors = RadioButtonDefaults.colors(selectedColor = ActionBlue)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(dest.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val destId = selectedDestinationId ?: return@Button
                            val robotId = robotStatus.id.ifBlank { settingsStore.cachedRobotId ?: "" }
                            if (robotId.isBlank()) {
                                errorMessage = "Cannot dispatch: No robot registered yet."
                                showCreateDialog = false
                                return@Button
                            }

                            showCreateDialog = false
                            scope.launch {
                                isCreating = true
                                errorMessage = null
                                try {
                                    val newDel = apiClient.getService().createDelivery(
                                        CreateDeliveryRequest(
                                            destination_id = destId,
                                            robot_id = robotId
                                        )
                                    )
                                    loadData()
                                    onSelectDelivery(newDel.id)
                                } catch (t: Throwable) {
                                    val parsed = ApiErrorParser.parse(t)
                                    errorMessage = if (parsed.statusCode == 403) {
                                        "You don't have access to this robot. Ask an admin to grant access."
                                    } else {
                                        parsed.message
                                    }
                                } finally {
                                    isCreating = false
                                }
                            }
                        },
                        enabled = selectedDestinationId != null && !isCreating,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
                    ) {
                        Text("Dispatch")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
