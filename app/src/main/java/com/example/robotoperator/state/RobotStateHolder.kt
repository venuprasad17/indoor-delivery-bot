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

    private val _poseTrail = MutableStateFlow<List<Pose>>(emptyList())
    val poseTrail: StateFlow<List<Pose>> = _poseTrail.asStateFlow()

    private val _isWsConnected = MutableStateFlow(false)
    val isWsConnected: StateFlow<Boolean> = _isWsConnected.asStateFlow()

    private val _connectionStatusText = MutableStateFlow("Disconnected")
    val connectionStatusText: StateFlow<String> = _connectionStatusText.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _activeDeliveryId = MutableStateFlow<String?>(null)
    val activeDeliveryId: StateFlow<String?> = _activeDeliveryId.asStateFlow()

    private val _needsModeReselection = MutableStateFlow(false)
    val needsModeReselection: StateFlow<Boolean> = _needsModeReselection.asStateFlow()

    fun updateRobotStatus(status: RobotStatus) {
        _robotStatus.value = status
    }

    fun updateMode(newMode: String) {
        _robotStatus.update { it.copy(control_mode = newMode) }
    }

    fun updateSafetyState(newSafetyState: String) {
        _robotStatus.update { it.copy(safety_state = newSafetyState) }
    }

    fun updateOnlineStatus(isOnline: Boolean, lastSeen: String? = null) {
        _robotStatus.update {
            it.copy(
                status = if (isOnline) "ONLINE" else "OFFLINE",
                last_seen = lastSeen ?: it.last_seen
            )
        }
    }

    fun updatePose(pose: Pose) {
        _currentPose.value = pose
        _poseTrail.update { current ->
            val updated = current + pose
            // Keep recent 300 points for smooth performance
            if (updated.size > 300) updated.takeLast(300) else updated
        }
    }

    fun clearPoseTrail() {
        _poseTrail.value = emptyList()
    }

    fun setWsConnected(connected: Boolean, statusText: String) {
        _isWsConnected.value = connected
        _connectionStatusText.value = statusText
    }

    fun setActiveSessionId(sessionId: String?) {
        _activeSessionId.value = sessionId
    }

    fun setActiveDeliveryId(deliveryId: String?) {
        _activeDeliveryId.value = deliveryId
    }

    fun setNeedsModeReselection(needed: Boolean) {
        _needsModeReselection.value = needed
    }
}
