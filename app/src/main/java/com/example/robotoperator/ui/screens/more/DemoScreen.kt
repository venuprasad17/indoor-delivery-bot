package com.example.robotoperator.ui.screens.more

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.api.ApiErrorParser
import com.example.robotoperator.data.model.Delivery
import com.example.robotoperator.data.model.Destination
import com.example.robotoperator.data.model.RobotStatus
import com.example.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoScreen(apiClient: ApiClient, onNavigateBack: () -> Unit) {
    var robot by remember { mutableStateOf<RobotStatus?>(null) }
    var destinations by remember { mutableStateOf<List<Destination>>(emptyList()) }
    var deliveries by remember { mutableStateOf<List<Delivery>>(emptyList()) }
    var message by remember { mutableStateOf("Loading live demo data…") }
    val scope = rememberCoroutineScope()

    fun refresh() = scope.launch {
        try {
            val service = apiClient.getService()
            robot = runCatching { service.getRobotStatus() }.getOrNull()
            destinations = service.getDestinations()
            deliveries = service.getDeliveries()
            message = "Live data loaded from the configured backend."
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { message = ApiErrorParser.parse(e).message }
    }
    LaunchedEffect(Unit) { refresh() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("VIVA DEMO") },
            navigationIcon = { IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("demo_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            } }
        )
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Live backend demonstration", style = MaterialTheme.typography.titleMedium)
            Text(message, color = Slate600)
            DemoRow("Backend", "Connected when this page loads")
            DemoRow("Robot", robot?.let { "${it.name.ifBlank { it.id.take(8) }} — ${it.status} / ${it.control_mode}" } ?: "Unavailable")
            DemoRow("Destinations", destinations.size.toString())
            DemoRow("Deliveries", deliveries.size.toString())
            Text("Dispatch requires a real robot gateway. This page never invents telemetry or robot state.", color = StatusAmber)
            Button(onClick = { refresh() }, modifier = Modifier.fillMaxWidth().testTag("demo_refresh")) { Text("Refresh live data") }
        }
    }
}

@Composable
private fun DemoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Slate600)
        Text(value, color = Slate900)
    }
}
