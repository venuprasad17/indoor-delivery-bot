package com.example.robotoperator.state

import com.example.robotoperator.data.model.Pose
import com.example.robotoperator.data.model.RobotStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class RobotStateHolder {

    private val _robotStatus = MutableStateFlow(RobotStatus())
    val robotStatus: StateFlow<RobotStatus> = _robotStatus.asStateFlow()

    private val _currentPose = MutableStateFlow(Pose())
    val currentPose: StateFlow<Pose> = _currentPose.asStateFlow()

    // Trail since screen opened - capped to 500 points for memory safety
    private val _poseTrail = MutableStateFlow<List<Pose>>(emptyList())
    val poseTrail: StateFlow<List<Pose>> = _poseTrail.asStateFlow()

    private val _isLiveConnected = MutableStateFlow(false)
    val isLiveConnected: StateFlow<Boolean> = _isLiveConnected.asStateFlow()

    private val _emergencyStopError = MutableStateFlow<String?>(null)
    val emergencyStopError: StateFlow<String?> = _emergencyStopError.asStateFlow()

    private val _needsModeReselection = MutableStateFlow(false)
    val needsModeReselection: StateFlow<Boolean> = _needsModeReselection.asStateFlow()

    fun updateRobotStatus(status: RobotStatus) {
        val current = _robotStatus.value
        _robotStatus.value = status.copy(
            physical_execution_confirmed = status.physical_execution_confirmed
                ?: current.physical_execution_confirmed.takeIf { current.safety_state == status.safety_state }
        )
    }

    fun updateControlMode(mode: String) {
        _robotStatus.update { it.copy(control_mode = mode) }
    }

    fun updateSafetyState(
        safetyState: String?,
        reportedAt: String? = null,
        physicalExecutionConfirmed: Boolean? = null
    ) {
        _robotStatus.update {
            it.copy(
                safety_state = safetyState?.takeIf { value -> value.isNotBlank() } ?: it.safety_state,
                safety_state_reported_at = if (safetyState.isNullOrBlank()) it.safety_state_reported_at else reportedAt,
                physical_execution_confirmed = physicalExecutionConfirmed
            )
        }
    }

    fun setNeedsModeReselection(needed: Boolean) {
        _needsModeReselection.value = needed
    }

    fun updateOnline(isOnline: Boolean, lastSeen: String? = null) {
        _robotStatus.update {
            it.copy(
                status = if (isOnline) "ONLINE" else "OFFLINE",
                connection_state = if (isOnline) "ONLINE" else "OFFLINE",
                last_seen = lastSeen ?: it.last_seen
            )
        }
    }

    fun updateConnectionState(
        connectionState: String,
        lastSeen: String? = null,
        clearLastSeen: Boolean = false
    ) {
        _robotStatus.update {
            it.copy(
                connection_state = connectionState,
                status = when (connectionState.uppercase()) {
                    "ONLINE" -> "ONLINE"
                    "OFFLINE" -> "OFFLINE"
                    else -> it.status
                },
                last_seen = if (clearLastSeen) null else lastSeen ?: it.last_seen
            )
        }
    }

    fun updatePose(pose: Pose) {
        _currentPose.value = pose
        _poseTrail.update { trail ->
            val updated = trail + pose
            if (updated.size > 500) updated.takeLast(500) else updated
        }
    }

    fun clearPoseTrail() {
        _poseTrail.value = emptyList()
    }

    fun setLiveConnected(connected: Boolean) {
        _isLiveConnected.value = connected
        if (!connected) {
            // Losing the dashboard feed invalidates the last client-side observation. This does
            // not mean the physical robot is offline; wait for a new backend snapshot/event.
            _robotStatus.update { status ->
                if (status.connection_state.equals("UNKNOWN", ignoreCase = true)) {
                    status
                } else {
                    status.copy(connection_state = "STALE")
                }
            }
        }
    }

    fun setEmergencyStopError(message: String?) {
        _emergencyStopError.value = message
    }
}
