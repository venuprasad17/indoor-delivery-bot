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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.robotoperator.data.api.ApiClient
import com.example.robotoperator.data.api.ApiErrorParser
import com.example.robotoperator.data.model.Delivery
import com.example.robotoperator.ui.components.BadgeColorType
import com.example.robotoperator.ui.components.KeyValRow
import com.example.robotoperator.ui.components.StatusBadge
import com.example.robotoperator.ui.components.getStatusBadgeType
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryDetailScreen(
    deliveryId: String,
    apiClient: ApiClient,
    onBack: () -> Unit
) {
    var delivery by remember { mutableStateOf<Delivery?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isActionRunning by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var isActionError by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val fetchDelivery: suspend () -> Unit = {
        try {
            val service = apiClient.getService()
            val data = withContext(Dispatchers.IO) { service.getDelivery(deliveryId) }
            delivery = data
        } catch (e: Exception) {
            val err = ApiErrorParser.parse(e)
            actionMessage = err.message
            isActionError = true
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(deliveryId) {
        fetchDelivery()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Delivery #${deliveryId.take(8)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = Slate900
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        delivery?.let {
                            StatusBadge(
                                text = it.status,
                                type = getStatusBadgeType(it.status)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("delivery_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Slate700)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureWhite)
            )
        },
        containerColor = Slate50
    ) { innerPadding ->
        if (isLoading && delivery == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = PrimaryBlue)
            }
        } else if (delivery == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = actionMessage ?: "Delivery not found", color = StatusRedText, fontSize = 14.sp)
            }
        } else {
            val current = delivery!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Inline feedback / error / 501 banner
                if (!actionMessage.isNullOrEmpty()) {
                    Text(
                        text = actionMessage!!,
                        color = if (isActionError) StatusRedText else StatusGreenText,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isActionError) StatusRedBg else StatusGreenBg, shape = RoundedCornerShape(4.dp))
                            .border(1.dp, if (isActionError) StatusRedBorder else StatusGreenBorder, shape = RoundedCornerShape(4.dp))
                            .padding(10.dp)
                            .testTag("delivery_action_banner")
                    )
                }

                // Action Controls Card (Dispatch, Confirm Pickup, Cancel)
                Card(
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Actions",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate700
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    isActionRunning = true
                                    actionMessage = null
                                    scope.launch {
                                        try {
                                            val s = apiClient.getService()
                                            val res = withContext(Dispatchers.IO) { s.dispatchDelivery(deliveryId) }
                                            if (res.code() == 501) {
                                                isActionError = true
                                                actionMessage = "Not available yet on the server"
                                            } else if (res.isSuccessful) {
                                                isActionError = false
                                                actionMessage = "Dispatched successfully"
                                                fetchDelivery()
                                            } else {
                                                isActionError = true
                                                actionMessage = ApiErrorParser.parseHttpError(res.code(), res.errorBody()?.string()).message
                                            }
                                        } catch (e: Exception) {
                                            isActionError = true
                                            actionMessage = ApiErrorParser.parse(e).message
                                        } finally {
                                            isActionRunning = false
                                        }
                                    }
                                },
                                enabled = !isActionRunning,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("action_dispatch")
                            ) {
                                Text("Dispatch", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    isActionRunning = true
                                    actionMessage = null
                                    scope.launch {
                                        try {
                                            val s = apiClient.getService()
                                            val res = withContext(Dispatchers.IO) { s.confirmPickupDelivery(deliveryId) }
                                            if (res.code() == 501) {
                                                isActionError = true
                                                actionMessage = "Not available yet on the server"
                                            } else if (res.isSuccessful) {
                                                isActionError = false
                                                actionMessage = "Pickup confirmed"
                                                fetchDelivery()
                                            } else {
                                                isActionError = true
                                                actionMessage = ApiErrorParser.parseHttpError(res.code(), res.errorBody()?.string()).message
                                            }
                                        } catch (e: Exception) {
                                            isActionError = true
                                            actionMessage = ApiErrorParser.parse(e).message
                                        } finally {
                                            isActionRunning = false
                                        }
                                    }
                                },
                                enabled = !isActionRunning,
                                colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("action_confirm_pickup")
                            ) {
                                Text("Pickup", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    isActionRunning = true
                                    actionMessage = null
                                    scope.launch {
                                        try {
                                            val s = apiClient.getService()
                                            val res = withContext(Dispatchers.IO) { s.cancelDelivery(deliveryId) }
                                            if (res.code() == 501) {
                                                isActionError = true
                                                actionMessage = "Not available yet on the server"
                                            } else if (res.isSuccessful) {
                                                isActionError = false
                                                actionMessage = "Delivery cancelled"
                                                fetchDelivery()
                                            } else {
                                                isActionError = true
                                                actionMessage = ApiErrorParser.parseHttpError(res.code(), res.errorBody()?.string()).message
                                            }
                                        } catch (e: Exception) {
                                            isActionError = true
                                            actionMessage = ApiErrorParser.parse(e).message
                                        } finally {
                                            isActionRunning = false
                                        }
                                    }
                                },
                                enabled = !isActionRunning,
                                colors = ButtonDefaults.buttonColors(containerColor = EStopRed),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("action_cancel_delivery")
                            ) {
                                Text("Cancel", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Vertical Stepper Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Delivery Lifecycle",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate900,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        val steps = listOf(
                            "IDLE",
                            "GO_TO_DESTINATION",
                            "ARRIVED",
                            "DELIVERED",
                            "RETURN_HOME",
                            "HOME"
                        )
                        val currentIndex = steps.indexOf(current.status)
                        val isEmergencyStop = current.status == "EMERGENCY_STOP"

                        steps.forEachIndexed { index, stepName ->
                            val isCompleted = currentIndex > index
                            val isCurrent = currentIndex == index && !isEmergencyStop
                            val isPending = currentIndex < index || currentIndex == -1

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Stepper Dot / Icon
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isCurrent -> PrimaryBlue
                                                isCompleted -> StatusGreenText
                                                else -> Slate200
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isCompleted) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = PureWhite,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "${index + 1}",
                                            fontSize = 11.sp,
                                            color = if (isCurrent) PureWhite else Slate600,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = stepName.replace('_', ' '),
                                    fontSize = 13.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isCurrent -> PrimaryBlue
                                        isCompleted -> Slate800
                                        else -> Slate400
                                    }
                                )
                            }

                            // Stepper connecting line
                            if (index < steps.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 11.dp)
                                        .width(2.dp)
                                        .height(16.dp)
                                        .background(if (currentIndex > index) StatusGreenText else Slate200)
                                )
                            }
                        }

                        // Terminal Emergency Stop State Indicator if triggered
                        if (isEmergencyStop) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(StatusRedBg, shape = RoundedCornerShape(4.dp))
                                    .border(1.dp, StatusRedBorder, shape = RoundedCornerShape(4.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = StatusRedText, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("EMERGENCY STOP (TERMINAL STATE)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StatusRedText)
                            }
                        }
                    }
                }

                // Delivery Info & Transition History Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Delivery Details",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate900,
                            modifier = Modifier.padding(16.dp, 12.dp)
                        )
                        HorizontalDivider(color = Slate200, thickness = 1.dp)

                        KeyValRow(label = "Delivery ID", value = current.id, isMonospace = true)
                        KeyValRow(label = "Destination ID", value = current.destination_id ?: "None", isMonospace = true)
                        KeyValRow(label = "Robot ID", value = current.robot_id ?: "None", isMonospace = true)
                        KeyValRow(label = "Created at", value = current.created_at ?: "Unknown")

                        if (current.history.isNotEmpty()) {
                            Text(
                                text = "Transition History",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate700,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp)
                            )
                            current.history.forEach { step ->
                                KeyValRow(
                                    label = step.status,
                                    value = step.timestamp ?: "",
                                    isMonospace = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
