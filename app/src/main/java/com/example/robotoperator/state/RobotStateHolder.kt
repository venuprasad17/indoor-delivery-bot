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
        _robotStatus.value = status
    }

    fun updateControlMode(mode: String) {
        _robotStatus.update { it.copy(control_mode = mode) }
    }

    fun updateSafetyState(safetyState: String) {
        _robotStatus.update { it.copy(safety_state = safetyState) }
    }

    fun setNeedsModeReselection(needed: Boolean) {
        _needsModeReselection.value = needed
    }

    fun updateOnline(isOnline: Boolean, lastSeen: String? = null) {
        _robotStatus.update {
            it.copy(
                status = if (isOnline) "ONLINE" else "OFFLINE",
                last_seen = lastSeen ?: it.last_seen
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
    }

    fun setEmergencyStopError(message: String?) {
        _emergencyStopError.value = message
    }
}
