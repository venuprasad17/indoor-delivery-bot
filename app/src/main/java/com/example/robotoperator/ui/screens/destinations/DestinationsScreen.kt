package com.example.robotoperator.ui.screens.destinations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.api.ApiErrorParser
import com.example.robotoperator.data.model.CreateDestinationRequest
import com.example.robotoperator.data.model.Destination
import com.example.robotoperator.state.RobotStateHolder
import com.example.robotoperator.ui.components.StatusBadge
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun DestinationsScreen(
    apiClient: ApiClient,
    stateHolder: RobotStateHolder
) {
    var destinations by remember { mutableStateOf<List<Destination>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var showOnlyHome by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var addName by remember { mutableStateOf("") }
    var addMapId by remember { mutableStateOf("") }
    var addX by remember { mutableStateOf("") }
    var addY by remember { mutableStateOf("") }
    var addTheta by remember { mutableStateOf("0.0") }
    var addIsHome by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun loadDestinations() {
        scope.launch {
            isLoading = true
            actionError = null
            try {
                destinations = apiClient.getService().getDestinations()
            } catch (t: Throwable) {
                actionError = ApiErrorParser.parse(t).message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadDestinations()
    }

    val filteredDestinations = remember(destinations, showOnlyHome) {
        if (showOnlyHome) destinations.filter { it.is_home } else destinations
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    addName = ""
                    // Default map_id from existing destinations if any exist
                    addMapId = destinations.firstOrNull { !it.map_id.isNullOrBlank() }?.map_id ?: "default_map"
                    addX = ""
                    addY = ""
                    addTheta = "0.0"
                    addIsHome = false
                    showAddDialog = true
                },
                containerColor = ActionBlue,
                contentColor = PureWhite,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.testTag("add_destination_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Destination",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add destination", fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
                    text = "WAYPOINTS & DESTINATIONS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Slate500
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = showOnlyHome,
                        onClick = { showOnlyHome = !showOnlyHome },
                        label = { Text("Home only", fontSize = 12.sp) },
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { loadDestinations() },
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
            }

            if (!actionError.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(StatusRedBg)
                        .border(1.dp, StatusRedBorder, RoundedCornerShape(4.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = actionError!!,
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
                            text = "Loading destinations...",
                            fontSize = 14.sp,
                            color = Slate500
                        )
                    }
                }
                filteredDestinations.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (showOnlyHome) "No home destinations found" else "No destinations registered",
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
                        items(filteredDestinations, key = { it.id }) { dest ->
                            Card(
                                shape = RoundedCornerShape(6.dp),
                                colors = CardDefaults.cardColors(containerColor = PureWhite),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
                                    .testTag("destination_card_${dest.id}")
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
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = dest.name,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Slate900
                                                )
                                                if (dest.is_home) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    StatusBadge(status = "HOME")
                                                }
                                            }
                                            Text(
                                                text = "Map: ${dest.map_id ?: "default"}",
                                                fontSize = 12.sp,
                                                color = Slate500,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    actionError = null
                                                    try {
                                                        apiClient.getService().deleteDestination(dest.id)
                                                        loadDestinations()
                                                    } catch (t: Throwable) {
                                                        actionError = ApiErrorParser.parse(t).message
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = StatusRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(SurfaceCanvas, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "x: ${"%.2f".format(dest.x)}m",
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Slate700
                                        )
                                        Text(
                                            text = "y: ${"%.2f".format(dest.y)}m",
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Slate700
                                        )
                                        Text(
                                            text = "θ: ${"%.2f".format(dest.theta)}rad",
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

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("New Destination") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = addName,
                            onValueChange = { addName = it },
                            label = { Text("Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = addMapId,
                            onValueChange = { addMapId = it },
                            label = { Text("Map ID") },
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = addX,
                                onValueChange = { addX = it },
                                label = { Text("X (m)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = addY,
                                onValueChange = { addY = it },
                                label = { Text("Y (m)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = addTheta,
                            onValueChange = { addTheta = it },
                            label = { Text("Theta (rad)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { addIsHome = !addIsHome }
                        ) {
                            Checkbox(
                                checked = addIsHome,
                                onCheckedChange = { addIsHome = it },
                                colors = CheckboxDefaults.colors(checkedColor = ActionBlue)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mark as Home Base", fontSize = 13.sp, color = Slate800)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val x = addX.toDoubleOrNull() ?: 0.0
                            val y = addY.toDoubleOrNull() ?: 0.0
                            val theta = addTheta.toDoubleOrNull() ?: 0.0
                            val mapId = addMapId.trim().ifBlank { "default_map" }
                            val name = addName.trim().ifBlank { "Waypoint" }

                            showAddDialog = false
                            scope.launch {
                                actionError = null
                                try {
                                    apiClient.getService().createDestination(
                                        CreateDestinationRequest(
                                            name = name,
                                            map_id = mapId,
                                            x = x,
                                            y = y,
                                            theta = theta,
                                            is_home = addIsHome
                                        )
                                    )
                                    loadDestinations()
                                } catch (t: Throwable) {
                                    actionError = ApiErrorParser.parse(t).message
                                }
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
