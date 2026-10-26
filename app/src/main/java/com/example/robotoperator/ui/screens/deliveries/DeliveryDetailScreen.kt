package com.example.robotoperator.ui.screens.deliveries

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.robotoperator.data.model.isDeliveryActionAvailable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import com.example.robotoperator.data.model.Delivery
import com.example.robotoperator.data.model.DeliveryAction
import com.example.robotoperator.data.model.deliveryProgressIndex
import com.example.robotoperator.data.model.deliveryProgressSteps
import com.example.robotoperator.data.model.isDeliveryActionVisible
import com.example.robotoperator.data.model.isDeliveryLifecycleComplete
import com.example.robotoperator.ui.components.KeyValRow
import com.example.robotoperator.ui.components.StatusBadge
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryDetailScreen(
    deliveryId: String,
    apiClient: ApiClient,
    userRole: String?,
    onNavigateBack: () -> Unit
) {
    var delivery by remember { mutableStateOf<Delivery?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<DeliveryAction?>(null) }
    var isExecutingAction by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun fetchDelivery() {
        try {
            val fetched = apiClient.getService().getDelivery(deliveryId)
            fetchError = null
            val previous = delivery
            delivery = fetched.copy(
                physical_execution_confirmed = fetched.physical_execution_confirmed
                    ?: previous?.physical_execution_confirmed,
                cancellation_requested = fetched.cancellation_requested
                    ?: previous?.cancellation_requested
            )
        } catch (e: CancellationException) { throw e
        } catch (t: Exception) {
            fetchError = ApiErrorParser.parse(t).message
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(deliveryId) {
        while (isActive) {
            if (!isExecutingAction) fetchDelivery()
            delay(3000)
        }
    }

    pendingAction?.let { action ->
        val stillAvailable = delivery?.let { isDeliveryActionVisible(userRole, action) && isDeliveryActionAvailable(it, action) } == true
        AlertDialog(
            onDismissRequest = { if (!isExecutingAction) pendingAction = null },
            title = { Text(when(action) {
                DeliveryAction.DISPATCH -> "Request dispatch?"
                DeliveryAction.CONFIRM_PICKUP -> "Confirm package received?"
                DeliveryAction.CONFIRM_LOADING -> "Confirm package loaded?"
                DeliveryAction.CANCEL -> "Request cancellation?"
            }) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(when(action) {
                    DeliveryAction.DISPATCH -> "Check pickup and destination before requesting travel to the pickup location. Confirm loading when the robot arrives there."
                    DeliveryAction.CONFIRM_LOADING -> "Confirm only after loading the package at the selected pickup location. This requests travel to the destination."
                    DeliveryAction.CONFIRM_PICKUP -> "Confirm only after the package has been collected. The backend will record pickup; an operator handles any physical repositioning."
                    DeliveryAction.CANCEL -> "This requests cancellation. It does not confirm the robot has physically stopped."
                })
                if (!stillAvailable) Text("The delivery state has changed. Close this dialog to review it.")
                actionError?.let { Text(it, color = StatusRed, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            } },
            confirmButton = { TextButton(enabled = stillAvailable && !isExecutingAction && fetchError == null, onClick = {
                isExecutingAction = true
                actionError = null
                scope.launch {
                    try {
                        delivery = when(action) {
                            DeliveryAction.DISPATCH -> apiClient.getService().dispatchDelivery(deliveryId)
                            DeliveryAction.CONFIRM_PICKUP -> apiClient.getService().confirmPickupDelivery(deliveryId)
                            DeliveryAction.CONFIRM_LOADING -> apiClient.getService().confirmLoadingDelivery(deliveryId)
                            DeliveryAction.CANCEL -> apiClient.getService().cancelDelivery(deliveryId)
                        }
                        pendingAction = null
                    } catch (e: CancellationException) { throw e
                    } catch (e: SocketTimeoutException) {
                        actionError = "Connection timed out while the robot was active. Its current state is unknown; check the dashboard before retrying."
                    } catch (e: Exception) { actionError = ApiErrorParser.parse(e).message
                    } finally { isExecutingAction = false }
                }
            }) { Text(if (isExecutingAction) "Sending…" else "Confirm") } },
            dismissButton = { TextButton(enabled = !isExecutingAction, onClick = { pendingAction = null; actionError = null }) { Text("Back") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Delivery ${deliveryId.take(8)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate900
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Slate800
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PureWhite,
                    titleContentColor = Slate900
                )
            )
        },
        containerColor = SurfaceCanvas
    ) { innerPadding ->
        if (isLoading && delivery == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading delivery details...",
                    fontSize = 14.sp,
                    color = Slate500
                )
            }
        } else if (delivery == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = fetchError ?: "Delivery not found",
                        fontSize = 14.sp,
                        color = StatusRed
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { scope.launch { fetchDelivery() } }) {
                        Text("Retry")
                    }
                }
            }
        } else {
            val d = delivery!!
            val steps = deliveryProgressSteps

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!actionError.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (actionError!!.contains("Not available", ignoreCase = true)) StatusAmberBg else StatusRedBg)
                            .border(1.dp, if (actionError!!.contains("Not available", ignoreCase = true)) StatusAmberBorder else StatusRedBorder, RoundedCornerShape(4.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = actionError!!,
                            color = if (actionError!!.contains("Not available", ignoreCase = true)) StatusAmber else StatusRed,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Vertical Stepper
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "DELIVERY PROGRESS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Slate500,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )

                        val currentStatus = d.status.uppercase()
                        val lifecycleCompleted = isDeliveryLifecycleComplete(d)
                        val currentIndex = deliveryProgressIndex(d)
                        val isEmergency = currentStatus == "EMERGENCY_STOP"

                        steps.forEachIndexed { index, stepName ->
                            val isCompleted = !isEmergency && (lifecycleCompleted || currentIndex > index)
                            val isCurrent = !isEmergency && !lifecycleCompleted && currentIndex == index

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isCompleted -> StatusGreen
                                                isCurrent -> ActionBlue
                                                else -> Slate200
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isCompleted) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = PureWhite,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "${index + 1}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCurrent) PureWhite else Slate500
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = stepName.replace("_", " "),
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isCurrent) Slate900 else if (isCompleted) Slate800 else Slate400,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            if (index < steps.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 11.dp)
                                        .width(2.dp)
                                        .height(16.dp)
                                        .background(if (isCompleted) StatusGreen else BorderHairline)
                                )
                            }
                        }

                        if (isEmergency) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(StatusRedBg, RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "Delivery workflow stopped",
                                    fontSize = 12.sp,
                                    color = StatusRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "This backend delivery state does not confirm a physical emergency stop.",
                                    fontSize = 11.sp,
                                    color = StatusRed
                                )
                            }
                        }
                        if (lifecycleCompleted) {
                            Text(
                                    text = "Delivery reached its destination. Any physical repositioning is handled by an operator.",
                                fontSize = 11.sp,
                                color = StatusGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (currentStatus == "IDLE" && !isEmergency) {
                            Text("Delivery not started", fontSize = 11.sp, color = Slate500)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Progress is reported by the backend. A successful request does not confirm physical robot movement.",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    }
                }

                // Delivery Actions (dispatch, confirm-pickup, cancel)
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "DELIVERY ACTIONS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Slate500
                        )

                        val availableActions = DeliveryAction.values().filter {
                            isDeliveryActionVisible(userRole, it) && isDeliveryActionAvailable(d, it)
                        }
                        if (availableActions.isEmpty()) Text(
                            if (isDeliveryLifecycleComplete(d)) "This delivery is complete." else "No action is available at this stage. Progress updates automatically.",
                            style = MaterialTheme.typography.bodyMedium, color = Slate600
                        )
                        availableActions.forEach { action ->
                            OutlinedButton(
                                onClick = { pendingAction = action },
                                enabled = !isExecutingAction && fetchError == null,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(when(action) {
                                    DeliveryAction.DISPATCH -> "dispatch_delivery_button"
                                    DeliveryAction.CONFIRM_PICKUP -> "confirm_pickup_button"
                                    DeliveryAction.CONFIRM_LOADING -> "confirm_loading_button"
                                    DeliveryAction.CANCEL -> "cancel_delivery_button"
                                })
                            ) { Text(when(action) {
                                DeliveryAction.DISPATCH -> "Request dispatch"
                                DeliveryAction.CONFIRM_PICKUP -> "Confirm package received"
                                DeliveryAction.CONFIRM_LOADING -> "Confirm package loaded"
                                DeliveryAction.CANCEL -> "Request cancellation"
                            }) }
                        }
                        if (isExecutingAction) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        fetchError?.let { Text("Updates unavailable: $it. Retrying automatically.", color = StatusRed, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }

                    }
                }

                // Delivery Details
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DELIVERY METADATA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Slate500
                            )
                            StatusBadge(status = if (isDeliveryLifecycleComplete(d)) "COMPLETED" else d.status)
                        }

                        HorizontalDivider(thickness = 1.dp, color = BorderHairline)

                        KeyValRow(label = "Delivery ID", value = d.id, isMonospace = true)
                        KeyValRow(label = "Pickup / Source", value = d.source_name ?: d.source_id ?: "Not recorded (legacy delivery)")
                        if (!d.robot_id.isNullOrBlank()) {
                            KeyValRow(label = "Robot ID", value = d.robot_id, isMonospace = true)
                        }
                        if (!d.destination_id.isNullOrBlank()) {
                            KeyValRow(label = "Destination", value = d.destination_name ?: d.destination_id)
                        }
                        if (!d.created_at.isNullOrBlank()) {
                            KeyValRow(label = "Created At", value = d.created_at, isMonospace = true)
                        }
                        d.physical_execution_confirmed?.let { confirmed ->
                            KeyValRow(
                                label = "Physical Execution Confirmed",
                                value = confirmed.toString(),
                                isMonospace = true
                            )
                        }
                        d.cancellation_requested?.let { requested ->
                            KeyValRow(
                                label = "Cancellation Requested",
                                value = requested.toString(),
                                isMonospace = true
                            )
                        }
                        if (d.history.isEmpty()) {
                            KeyValRow(
                                label = "History Steps",
                                value = "No transitions",
                                isMonospace = true,
                                showDivider = false
                            )
                        } else {
                            Text(
                                text = "DELIVERY HISTORY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Slate500,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp)
                            )
                            d.history.forEach { transition ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${transition.from ?: "?"} → ${transition.to ?: "?"}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Slate800
                                    )
                                    val details = listOfNotNull(
                                        transition.timestamp,
                                        transition.actor_id?.let { "Actor $it" },
                                        transition.reason
                                    )
                                    if (details.isNotEmpty()) {
                                        Text(details.joinToString(" · "), fontSize = 11.sp, color = Slate500)
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
