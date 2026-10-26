package com.example.robotoperator.ui.screens.deliveries

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.robotoperator.data.model.Destination

@Composable
internal fun DeliveryLocationSelection(
    locations: List<Destination>,
    sourceId: String?,
    destinationId: String?,
    enabled: Boolean,
    onSourceSelected: (String) -> Unit,
    onDestinationSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val source = locations.firstOrNull { it.id == sourceId }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pickup / Source")
        LazyColumn(Modifier.heightIn(max = 140.dp).testTag("source_locations")) {
            items(locations, key = { it.id }) { location ->
                TextButton(
                    enabled = enabled,
                    onClick = { onSourceSelected(location.id) },
                    modifier = Modifier.fillMaxWidth().testTag("source_${location.id}")
                ) { Text((if (location.id == sourceId) "Selected: " else "") + location.name) }
            }
        }
        Text("Destination")
        if (source == null) Text("Choose a pickup location first.")
        LazyColumn(Modifier.heightIn(max = 140.dp).testTag("destination_locations")) {
            items(locations.filter { source != null && it.id != source.id && it.map_id == source.map_id }, key = { it.id }) { location ->
                TextButton(
                    enabled = enabled,
                    onClick = { onDestinationSelected(location.id) },
                    modifier = Modifier.fillMaxWidth().testTag("destination_${location.id}")
                ) { Text((if (location.id == destinationId) "Selected: " else "") + location.name) }
            }
        }
    }
}
