package com.example.robotoperator.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HealthResponse(
    val status: String = "ok"
)

// Auth
@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val access_token: String,
    val token_type: String = "bearer",
    val refresh_token: String? = null
)

@JsonClass(generateAdapter = true)
data class RefreshResponse(
    val access_token: String
)

@JsonClass(generateAdapter = true)
data class User(
    val id: String,
    val email: String,
    val name: String,
    val role: String,
    val is_active: Boolean = true
)

@JsonClass(generateAdapter = true)
data class CreateUserRequest(
    val email: String,
    val password: String,
    val name: String,
    val role: String = "USER"
)

@JsonClass(generateAdapter = true)
data class UpdateUserRequest(
    val role: String? = null,
    val is_active: Boolean? = null
)

// Robot Status & Mode
@JsonClass(generateAdapter = true)
data class RobotStatus(
    val id: String = "",
    val name: String = "",
    val status: String = "OFFLINE", // "ONLINE" | "OFFLINE"
    val control_mode: String = "MANUAL", // "MANUAL" | "AUTONOMOUS" | "STOPPED"
    val safety_state: String = "SAFE", // "SAFE" | "SLOW" | "REPLAN" | "EMERGENCY_STOP"
    val slam_state: String = "IDLE", // "TRACKING" | "LOST" | "RELOCATING" | "IDLE"
    val battery_level: Float? = null,
    val last_seen: String? = null,
    val firmware_version: String? = null
)

@JsonClass(generateAdapter = true)
data class Pose(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val theta: Double = 0.0,
    val timestamp: String? = null
)

@JsonClass(generateAdapter = true)
data class ModeRequest(
    val mode: String, // "MANUAL" | "AUTONOMOUS"
    val acknowledgment: Boolean = true
)

@JsonClass(generateAdapter = true)
data class EmergencyStopRequest(
    val reason: String = "Operator initiated emergency stop"
)

@JsonClass(generateAdapter = true)
data class SafetyStateResponse(
    val control_mode: String = "MANUAL",
    val safety_state: String = "SAFE",
    val needs_mode_reselection: Boolean = false
)

// Mapping Sessions
@JsonClass(generateAdapter = true)
data class MappingSession(
    val id: String,
    val robot_id: String,
    val status: String = "IDLE", // "IDLE" | "MAPPING" | "PAUSED" | "FINISHED"
    val elapsed_ms: Long = 0L,
    val pose_update_count: Int = 0,
    val last_pose_json: Pose? = null,
    val slam_status: String? = null, // "TRACKING" | "LOST" | "RELOCATING" | null
    val map_data_ref: String? = null,
    val started_at: String? = null,
    val paused_at: String? = null,
    val finished_at: String? = null,
    val created_at: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateSessionRequest(
    val robot_id: String
)

@JsonClass(generateAdapter = true)
data class FinishSessionRequest(
    val map_data_ref: String? = null
)

// Destinations
@JsonClass(generateAdapter = true)
data class Destination(
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val theta: Double = 0.0,
    val map_id: String? = null,
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
    val theta: Double = 0.0,
    val is_home: Boolean = false
)

@JsonClass(generateAdapter = true)
data class UpdateDestinationRequest(
    val name: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val theta: Double? = null
)

@JsonClass(generateAdapter = true)
data class NavigateRequest(
    val destination_id: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val theta: Double? = null
)

// Deliveries
@JsonClass(generateAdapter = true)
data class DeliveryHistoryItem(
    val status: String? = null,
    val timestamp: String? = null,
    val note: String? = null
)

@JsonClass(generateAdapter = true)
data class Delivery(
    val id: String,
    val status: String = "IDLE", // IDLE | GO_TO_DESTINATION | ARRIVED | DELIVERED | RETURN_HOME | HOME | EMERGENCY_STOP
    val history: List<DeliveryHistoryItem> = emptyList(),
    val robot_id: String? = null,
    val destination_id: String? = null,
    val destination_name: String? = null,
    val created_at: String? = null,
    val started_at: String? = null,
    val completed_at: String? = null,
    val priority: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateDeliveryRequest(
    val destination_id: String,
    val robot_id: String
)

// Events (Audit log)
@JsonClass(generateAdapter = true)
data class SystemEvent(
    val id: String,
    val type: String = "",
    val severity: String = "INFO",
    val payload_json: String? = null,
    val robot_id: String? = null,
    val user_id: String? = null,
    val created_at: String? = null
)

@JsonClass(generateAdapter = true)
data class EventsResponse(
    val items: List<SystemEvent> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val page_size: Int = 50
)
