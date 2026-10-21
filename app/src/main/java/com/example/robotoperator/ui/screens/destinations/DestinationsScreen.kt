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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.robotoperator.data.model.MappingSession
import com.example.robotoperator.data.model.finiteCoordinate
import kotlinx.coroutines.CancellationException
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

    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var addName by rememberSaveable { mutableStateOf("") }
    var addMapId by rememberSaveable { mutableStateOf("") }
    var addX by rememberSaveable { mutableStateOf("") }
    var addY by rememberSaveable { mutableStateOf("") }
    var addTheta by rememberSaveable { mutableStateOf("0.0") }
    var addIsHome by rememberSaveable { mutableStateOf(false) }

    var maps by remember { mutableStateOf<List<MappingSession>>(emptyList()) }
    var mapsLoading by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Destination?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadMaps() {
        scope.launch {
            mapsLoading = true
            mapError = null
            try {
                maps = apiClient.getService().getMappingSessions().filter { it.status == "FINISHED" }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { mapError = ApiErrorParser.parse(e).message
            } finally { mapsLoading = false }
        }
    }
    LaunchedEffect(showAddDialog) { if (showAddDialog) loadMaps() }
    val validCoordinates = finiteCoordinate(addX) != null && finiteCoordinate(addY) != null && finiteCoordinate(addTheta) != null
    val validForm = addName.trim().isNotEmpty() && addName.trim().length <= 100 &&
        maps.any { it.id == addMapId } && validCoordinates && !mapsLoading && mapError == null

    fun loadDestinations() {
        scope.launch {
            isLoading = true
            actionError = null
            try {
                destinations = apiClient.getService().getDestinations()
            } catch (e: CancellationException) { throw e
            } catch (t: Exception) {
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
                    addMapId = ""
                    formError = null
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
                    text = "Destinations",
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
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { loadDestinations() },
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
                            text = if (showOnlyHome) "No home destinations found. Clear the filter to see all destinations." else "No destinations yet. Finish a mapping session, then add a named destination.",
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
                                        Column(modifier = Modifier.weight(1f)) {
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
                                                deleteError = null
                                                pendingDelete = dest
                                            },
                                            enabled = !dest.is_home,
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = if (dest.is_home) "Home destination cannot be deleted" else "Delete ${dest.name}",
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

        pendingDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { if (!deleting) pendingDelete = null },
                title = { Text("Delete ${target.name}?") },
                text = { Column {
                    Text("This removes the saved destination. Deliveries may still refer to it; the server will check before deleting.")
                    deleteError?.let { Text(it, color = StatusRed, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                } },
                confirmButton = { TextButton(enabled = !deleting, onClick = {
                    deleting = true
                    deleteError = null
                    scope.launch {
                        try {
                            val response = apiClient.getService().deleteDestination(target.id)
                            if (!response.isSuccessful) throw retrofit2.HttpException(response)
                            pendingDelete = null
                            loadDestinations()
                        } catch (e: CancellationException) { throw e
                        } catch (e: Exception) { deleteError = ApiErrorParser.parse(e).message
                        } finally { deleting = false }
                    }
                }) { Text(if (deleting) "Deleting…" else "Delete", color = StatusRed) } },
                dismissButton = { TextButton(enabled = !deleting, onClick = { pendingDelete = null }) { Text("Keep destination") } }
            )
        }
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { if (!saving) showAddDialog = false },
                title = { Text("New Destination") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Choose a finished map and enter its coordinates. Positions are in metres; heading is in radians.", style = MaterialTheme.typography.bodyMedium)
                        formError?.let { Text(it, color = StatusRed, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                        OutlinedTextField(
                            value = addName,
                            onValueChange = { addName = it },
                            label = { Text("Destination name") },
                            enabled = !saving,
                            isError = addName.length > 100,
                            supportingText = { Text("Required · up to 100 characters") },
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Finished map", style = MaterialTheme.typography.titleSmall)
                        if (mapsLoading) Text("Loading maps…")
                        mapError?.let { Text(it, color = StatusRed); TextButton(onClick = { loadMaps() }) { Text("Retry loading maps") } }
                        if (!mapsLoading && mapError == null && maps.isEmpty()) Text("No finished maps. Finish a mapping session in More → Mapping before creating a destination.")
                        Column {
                            maps.forEach { map ->
                                Row(Modifier.fillMaxWidth().selectable(selected = addMapId == map.id, enabled = !saving, role = Role.RadioButton, onClick = { addMapId = map.id }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = addMapId == map.id, onClick = null)
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text("Map ${map.id.take(8)}")
                                        Text(map.finished_at ?: "Finished session", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = addX,
                                onValueChange = { addX = it },
                                label = { Text("X (m)") },
                                enabled = !saving,
                                isError = addX.isNotBlank() && finiteCoordinate(addX) == null,
                                supportingText = { Text("Required finite number") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = addY,
                                onValueChange = { addY = it },
                                label = { Text("Y (m)") },
                                enabled = !saving,
                                isError = addY.isNotBlank() && finiteCoordinate(addY) == null,
                                supportingText = { Text("Required finite number") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = addTheta,
                            onValueChange = { addTheta = it },
                            label = { Text("Heading (radians)") },
                            enabled = !saving,
                            isError = finiteCoordinate(addTheta) == null,
                            supportingText = { Text("0 points along the map’s positive X axis") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !saving) { addIsHome = !addIsHome }
                        ) {
                            Checkbox(
                                checked = addIsHome,
                                onCheckedChange = { addIsHome = it },
                                enabled = !saving,
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
                            if (!validForm || saving) return@Button
                            val x = finiteCoordinate(addX) ?: return@Button
                            val y = finiteCoordinate(addY) ?: return@Button
                            val theta = finiteCoordinate(addTheta) ?: return@Button
                            val mapId = addMapId
                            val name = addName.trim()
                            saving = true
                            formError = null
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
                                    showAddDialog = false
                                    loadDestinations()
                                } catch (e: CancellationException) { throw e
                                } catch (e: Exception) {
                                    formError = ApiErrorParser.parse(e).message
                                } finally { saving = false }
                            }
                        },
                        enabled = validForm && !saving,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
                    ) {
                        Text(if (saving) "Creating…" else "Create destination")
                    }
                },
                dismissButton = {
                    TextButton(enabled = !saving, onClick = { showAddDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
