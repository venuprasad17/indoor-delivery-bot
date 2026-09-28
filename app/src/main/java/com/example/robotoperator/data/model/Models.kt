package com.example.robotoperator.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HealthResponse(
    val status: String = "ok"
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val access_token: String,
    val token_type: String = "bearer",
    // In case backend changes to return refresh token in body
    val refresh_token: String? = null
)

@JsonClass(generateAdapter = true)
data class User(
    val id: String,
    val email: String,
    val name: String? = null,
    val role: String,
    val is_active: Boolean = true
)

@JsonClass(generateAdapter = true)
data class CreateUserRequest(
    val email: String,
    val password: String,
    val name: String,
    val role: String = "ADMIN"
)

@JsonClass(generateAdapter = true)
data class UpdateUserRequest(
    val name: String? = null,
    val is_active: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class RobotStatus(
    val id: String = "",
    val name: String = "Robot",
    val status: String = "OFFLINE", // "ONLINE" | "OFFLINE"
    val control_mode: String = "STOPPED", // "MANUAL" | "AUTONOMOUS" | "STOPPED"
    val safety_state: String = "SAFE", // "SAFE" | "SLOW" | "REPLAN" | "EMERGENCY_STOP"
    val slam_state: String = "IDLE", // "TRACKING" | "LOST" | "RELOCATING" | "IDLE"
    val battery_level: Double? = null,
    val last_seen: String? = null,
    val firmware_version: String? = null
)

@JsonClass(generateAdapter = true)
data class Pose(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val theta: Double = 0.0,
    val timestamp: Any? = null
) {
    fun formattedX(): String = String.format(java.util.Locale.US, "%.3f", x)
    fun formattedY(): String = String.format(java.util.Locale.US, "%.3f", y)
    fun formattedTheta(): String = String.format(java.util.Locale.US, "%.3f", theta)
}

@JsonClass(generateAdapter = true)
data class ModeChangeRequest(
    val mode: String, // "MANUAL" | "AUTONOMOUS"
    val acknowledgment: Boolean = true
)

@JsonClass(generateAdapter = true)
data class EStopRequest(
    val reason: String = "Operator manual E-STOP"
)

@JsonClass(generateAdapter = true)
data class EStopResponse(
    val control_mode: String? = null,
    val safety_state: String? = null,
    val needs_mode_reselection: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ResetEStopResponse(
    val status: String? = null,
    val safety_state: String? = null,
    val needs_mode_reselection: Boolean = false
)

@JsonClass(generateAdapter = true)
data class SafetyStateResponse(
    val safety_state: String = "SAFE",
    val timestamp: String? = null
)

// Mapping Session Models
@JsonClass(generateAdapter = true)
data class MappingSession(
    val id: String,
    val robot_id: String,
    val status: String = "IDLE", // "IDLE" | "MAPPING" | "PAUSED" | "FINISHED"
    val elapsed_ms: Long = 0L,
    val pose_update_count: Int = 0,
    val last_pose_json: Pose? = null,
    val slam_status: String? = null, // "TRACKING" | "LOST" | "RELOCATING"
    val map_data_ref: String? = null,
    val started_at: String? = null,
    val paused_at: String? = null,
    val finished_at: String? = null,
    val created_at: String? = null
) {
    fun shortId(): String = if (id.length > 8) id.take(8) else id
}

@JsonClass(generateAdapter = true)
data class CreateSessionRequest(
    val robot_id: String
)

@JsonClass(generateAdapter = true)
data class FinishSessionRequest(
    val map_data_ref: String? = null
)

// Destination Models
@JsonClass(generateAdapter = true)
data class Destination(
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val theta: Double,
    val map_id: String,
    val is_home: Boolean = false,
    val created_at: String? = null,
    val updated_at: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateDestinationRequest(
    val name: String,
    val map_id: String,
    val x: Double,
    val y: Double,
    val theta: Double,
    val is_home: Boolean = false
)

@JsonClass(generateAdapter = true)
data class UpdateDestinationRequest(
    val name: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val theta: Double? = null
)

// Delivery Models
@JsonClass(generateAdapter = true)
data class DeliveryStepHistory(
    val status: String,
    val timestamp: String? = null,
    val note: String? = null
)

@JsonClass(generateAdapter = true)
data class Delivery(
    val id: String,
    val status: String, // "IDLE"|"GO_TO_DESTINATION"|"ARRIVED"|"DELIVERED"|"RETURN_HOME"|"HOME"|"EMERGENCY_STOP"
    val destination_id: String? = null,
    val destination_name: String? = null,
    val robot_id: String? = null,
    val history: List<DeliveryStepHistory> = emptyList(),
    val created_at: String? = null,
    val updated_at: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateDeliveryRequest(
    val destination_id: String,
    val robot_id: String
)

// Events Models
@JsonClass(generateAdapter = true)
data class EventItem(
    val id: String,
    val type: String,
    val severity: String, // "INFO"|"WARN"|"ERROR"|"CRITICAL"
    val payload_json: Any? = null,
    val robot_id: String? = null,
    val user_id: String? = null,
    val created_at: String? = null
)

@JsonClass(generateAdapter = true)
data class EventsPage(
    val items: List<EventItem> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val page_size: Int = 50
)

// WebSocket Message
@JsonClass(generateAdapter = true)
data class WsMessage(
    val type: String,
    val payload: Any? = null
)
