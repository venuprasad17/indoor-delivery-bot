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
import androidx.compose.material.icons.filled.Close
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
import com.example.robotoperator.data.model.Delivery
import com.example.robotoperator.ui.components.KeyValRow
import com.example.robotoperator.ui.components.StatusBadge
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryDetailScreen(
    deliveryId: String,
    apiClient: ApiClient,
    onNavigateBack: () -> Unit
) {
    var delivery by remember { mutableStateOf<Delivery?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var isExecutingAction by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun fetchDelivery() {
        try {
            delivery = apiClient.getService().getDelivery(deliveryId)
        } catch (t: Throwable) {
            actionError = ApiErrorParser.parse(t).message
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(deliveryId) {
        while (isActive) {
            fetchDelivery()
            delay(3000)
        }
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
                        text = actionError ?: "Delivery not found",
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
            val steps = listOf("IDLE", "GO_TO_DESTINATION", "ARRIVED", "DELIVERED", "RETURN_HOME", "HOME")

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
                        val currentIndex = steps.indexOf(currentStatus)
                        val isEmergency = currentStatus == "EMERGENCY_STOP"

                        steps.forEachIndexed { index, stepName ->
                            val isCompleted = !isEmergency && currentIndex >= index
                            val isCurrent = !isEmergency && currentIndex == index

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
                                        .background(if (isCompleted && currentIndex > index) StatusGreen else BorderHairline)
                                )
                            }
                        }

                        if (isEmergency) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(StatusRedBg, RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = StatusRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Emergency Stop interrupted delivery",
                                    fontSize = 12.sp,
                                    color = StatusRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isExecutingAction = true
                                        actionError = null
                                        try {
                                            delivery = apiClient.getService().dispatchDelivery(deliveryId)
                                        } catch (t: Throwable) {
                                            actionError = ApiErrorParser.parse(t).message
                                        } finally {
                                            isExecutingAction = false
                                        }
                                    }
                                },
                                enabled = !isExecutingAction,
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 44.dp)
                                    .testTag("dispatch_delivery_button")
                            ) {
                                Text("Dispatch")
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        isExecutingAction = true
                                        actionError = null
                                        try {
                                            delivery = apiClient.getService().confirmPickupDelivery(deliveryId)
                                        } catch (t: Throwable) {
                                            actionError = ApiErrorParser.parse(t).message
                                        } finally {
                                            isExecutingAction = false
                                        }
                                    }
                                },
                                enabled = !isExecutingAction,
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 44.dp)
                                    .testTag("confirm_pickup_button")
                            ) {
                                Text("Pickup")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isExecutingAction = true
                                    actionError = null
                                    try {
                                        delivery = apiClient.getService().cancelDelivery(deliveryId)
                                    } catch (t: Throwable) {
                                        actionError = ApiErrorParser.parse(t).message
                                    } finally {
                                        isExecutingAction = false
                                    }
                                }
                            },
                            enabled = !isExecutingAction,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 44.dp)
                                .testTag("cancel_delivery_button")
                        ) {
                            Text("Cancel Delivery")
                        }
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
                            StatusBadge(status = d.status)
                        }

                        HorizontalDivider(thickness = 1.dp, color = BorderHairline)

                        KeyValRow(label = "Delivery ID", value = d.id, isMonospace = true)
                        if (!d.robot_id.isNullOrBlank()) {
                            KeyValRow(label = "Robot ID", value = d.robot_id, isMonospace = true)
                        }
                        if (!d.destination_id.isNullOrBlank()) {
                            KeyValRow(label = "Destination", value = d.destination_name ?: d.destination_id)
                        }
                        if (!d.created_at.isNullOrBlank()) {
                            KeyValRow(label = "Created At", value = d.created_at, isMonospace = true)
                        }
                        KeyValRow(
                            label = "History Steps",
                            value = "${d.history.size} entries",
                            isMonospace = true,
                            showDivider = false
                        )
                    }
                }
            }
        }
    }
}
