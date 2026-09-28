package com.example.robotoperator.ui.screens.destinations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.api.ApiErrorParser
import com.example.robotoperator.data.model.CreateDestinationRequest
import com.example.robotoperator.data.model.Destination
import com.example.robotoperator.data.model.UpdateDestinationRequest
import com.example.robotoperator.state.RobotStateHolder
import com.example.robotoperator.ui.components.BadgeColorType
import com.example.robotoperator.ui.components.StatusBadge
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationsScreen(
    apiClient: ApiClient,
    robotStore: RobotStateHolder
) {
    var destinations by remember { mutableStateOf<List<Destination>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var mapIdFilter by remember { mutableStateOf("") }

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingDestination by remember { mutableStateOf<Destination?>(null) }
    var destinationToDelete by remember { mutableStateOf<Destination?>(null) }

    val currentPose by robotStore.currentPose.collectAsState()
    val scope = rememberCoroutineScope()

    val fetchDestinations: suspend () -> Unit = {
        try {
            val service = apiClient.getService()
            val list = withContext(Dispatchers.IO) {
                service.getDestinations(mapId = mapIdFilter.ifBlank { null })
            }
            destinations = list
            errorMessage = null
        } catch (e: Exception) {
            val err = ApiErrorParser.parse(e)
            errorMessage = err.message
        } finally {
            isLoading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(mapIdFilter) {
        fetchDestinations()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .testTag("destinations_screen")
    ) {
        // Top action bar & map filter
        Surface(
            color = PureWhite,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Destinations",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate900
                    )

                    Button(
                        onClick = {
                            editingDestination = null
                            showAddEditDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("add_destination_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add destination", fontSize = 13.sp)
                    }
                }

                // Map ID filter input
                OutlinedTextField(
                    value = mapIdFilter,
                    onValueChange = { mapIdFilter = it },
                    placeholder = { Text("Filter by map ID (e.g. floor_1)") },
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("map_id_filter_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Slate300
                    )
                )
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
                    .testTag("destinations_error_banner")
            )
            HorizontalDivider(color = StatusRedBorder, thickness = 1.dp)
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch { fetchDestinations() }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading && destinations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryBlue
                    )
                }
            } else if (destinations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No destinations registered",
                        color = Slate500,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("destinations_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(destinations, key = { it.id }) { dest ->
                        DestinationItemCard(
                            destination = dest,
                            onEdit = {
                                editingDestination = dest
                                showAddEditDialog = true
                            },
                            onDelete = {
                                destinationToDelete = dest
                            }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Destination Dialog
    if (showAddEditDialog) {
        AddEditDestinationDialog(
            existing = editingDestination,
            prefillMapId = mapIdFilter.ifBlank {
                destinations.firstOrNull()?.map_id ?: "default_map"
            },
            currentPose = currentPose,
            onDismiss = { showAddEditDialog = false },
            onSave = { name, mapId, x, y, theta, isHome ->
                scope.launch {
                    try {
                        val s = apiClient.getService()
                        if (editingDestination == null) {
                            withContext(Dispatchers.IO) {
                                s.createDestination(
                                    CreateDestinationRequest(
                                        name = name,
                                        map_id = mapId,
                                        x = x,
                                        y = y,
                                        theta = theta,
                                        is_home = isHome
                                    )
                                )
                            }
                        } else {
                            withContext(Dispatchers.IO) {
                                s.updateDestination(
                                    editingDestination!!.id,
                                    UpdateDestinationRequest(
                                        name = name,
                                        x = x,
                                        y = y,
                                        theta = theta
                                    )
                                )
                            }
                        }
                        showAddEditDialog = false
                        fetchDestinations()
                    } catch (e: Exception) {
                        val err = ApiErrorParser.parse(e)
                        errorMessage = err.message
                    }
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (destinationToDelete != null) {
        val toDelete = destinationToDelete!!
        AlertDialog(
            onDismissRequest = { destinationToDelete = null },
            title = { Text("Delete destination?", color = Slate900) },
            text = {
                Text(
                    text = "Are you sure you want to delete '${toDelete.name}'? This action cannot be undone.",
                    color = Slate700,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = toDelete.id
                        destinationToDelete = null
                        scope.launch {
                            try {
                                val s = apiClient.getService()
                                withContext(Dispatchers.IO) { s.deleteDestination(id) }
                                fetchDestinations()
                            } catch (e: Exception) {
                                errorMessage = ApiErrorParser.parse(e).message
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EStopRed),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.testTag("confirm_delete_destination")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { destinationToDelete = null },
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

@Composable
private fun DestinationItemCard(
    destination: Destination,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dest_item_${destination.id}"),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = destination.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate900
                    )
                    if (destination.is_home) {
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge(
                            text = "HOME",
                            type = BadgeColorType.GREEN,
                            testTag = "home_badge_${destination.id}"
                        )
                    }
                }

                Text(
                    text = "Map: ${destination.map_id}",
                    fontSize = 12.sp,
                    color = Slate500
                )

                Text(
                    text = "X: ${String.format(java.util.Locale.US, "%.3f", destination.x)}  Y: ${String.format(java.util.Locale.US, "%.3f", destination.y)}  θ: ${String.format(java.util.Locale.US, "%.3f", destination.theta)} rad",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Slate700
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_dest_${destination.id}")) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit destination", tint = Slate600)
                }
                IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_dest_${destination.id}")) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete destination", tint = EStopRed)
                }
            }
        }
    }
}

@Composable
private fun AddEditDestinationDialog(
    existing: Destination?,
    prefillMapId: String,
    currentPose: com.example.robotoperator.data.model.Pose,
    onDismiss: () -> Unit,
    onSave: (name: String, mapId: String, x: Double, y: Double, theta: Double, isHome: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var mapId by remember { mutableStateOf(existing?.map_id ?: prefillMapId) }
    var xStr by remember { mutableStateOf(existing?.x?.toString() ?: "0.0") }
    var yStr by remember { mutableStateOf(existing?.y?.toString() ?: "0.0") }
    var thetaStr by remember { mutableStateOf(existing?.theta?.toString() ?: "0.0") }
    var isHome by remember { mutableStateOf(existing?.is_home ?: false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = PureWhite,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate300),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("add_edit_destination_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (existing == null) "New destination" else "Edit destination",
                    style = MaterialTheme.typography.titleMedium,
                    color = Slate900,
                    fontWeight = FontWeight.SemiBold
                )

                // "Use current robot pose" button
                OutlinedButton(
                    onClick = {
                        xStr = currentPose.formattedX()
                        yStr = currentPose.formattedY()
                        thetaStr = currentPose.formattedTheta()
                    },
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("use_robot_pose_button")
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Use current robot pose", fontSize = 12.sp)
                }

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Destination name") },
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dest_name_input")
                )

                // Map ID (disabled on edit per API schema)
                OutlinedTextField(
                    value = mapId,
                    onValueChange = { mapId = it },
                    label = { Text("Map ID") },
                    enabled = existing == null,
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dest_map_id_input")
                )

                // Coordinates Row (X & Y)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = xStr,
                        onValueChange = { xStr = it },
                        label = { Text("X (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dest_x_input")
                    )

                    OutlinedTextField(
                        value = yStr,
                        onValueChange = { yStr = it },
                        label = { Text("Y (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dest_y_input")
                    )
                }

                // Theta (radians)
                OutlinedTextField(
                    value = thetaStr,
                    onValueChange = { thetaStr = it },
                    label = { Text("θ Heading (radians)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dest_theta_input")
                )

                // Is Home Checkbox (only on create)
                if (existing == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isHome = !isHome }
                    ) {
                        Checkbox(
                            checked = isHome,
                            onCheckedChange = { isHome = it },
                            modifier = Modifier.testTag("is_home_checkbox")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Set as Home docking station", fontSize = 13.sp, color = Slate800)
                    }
                }

                if (!validationError.isNullOrEmpty()) {
                    Text(
                        text = validationError!!,
                        color = StatusRedText,
                        fontSize = 12.sp
                    )
                }

                // Dialog Buttons
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
                        onClick = {
                            val cleanName = name.trim()
                            if (cleanName.isEmpty()) {
                                validationError = "Name cannot be empty"
                                return@Button
                            }
                            val xVal = xStr.toDoubleOrNull()
                            val yVal = yStr.toDoubleOrNull()
                            val thetaVal = thetaStr.toDoubleOrNull()
                            if (xVal == null || yVal == null || thetaVal == null) {
                                validationError = "Coordinates and theta must be valid numbers"
                                return@Button
                            }
                            onSave(cleanName, mapId.trim(), xVal, yVal, thetaVal, isHome)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.testTag("save_destination_button")
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
