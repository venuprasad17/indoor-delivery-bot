package com.example.robotoperator.data.api

import android.util.Log
import com.example.robotoperator.data.model.Pose
import com.example.robotoperator.data.model.RobotStatus
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.robotoperator.state.RobotStateHolder
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import kotlin.math.sin

class DashboardWebSocketClient(
    private val settingsStore: ServerSettingsStore,
    private val apiClient: ApiClient,
    private val robotStore: RobotStateHolder,
    private val scope: CoroutineScope,
    private val onForbiddenAdmin: () -> Unit
) {

    companion object {
        private const val TAG = "DashboardWebSocket"
        private const val CLOSE_BAD_TOKEN = 4001
        private const val CLOSE_NOT_ADMIN = 4003
    }

    private var webSocket: WebSocket? = null
    private val isAppForeground = AtomicBoolean(true)
    private val isExplicitlyStopped = AtomicBoolean(false)
    private var reconnectJob: Job? = null
    private var simJob: Job? = null
    private var backoffDelayMs = 1000L

    fun start() {
        isExplicitlyStopped.set(false)
        connect()
    }

    fun stop() {
        isExplicitlyStopped.set(true)
        reconnectJob?.cancel()
        simJob?.cancel()
        disconnectSocket(1000, "Normal closure")
        robotStore.setWsConnected(false, "Disconnected")
    }

    fun onAppForegrounded() {
        isAppForeground.set(true)
        if (!isExplicitlyStopped.get()) {
            if (settingsStore.isSimulationMode) {
                startSimulationTelemetry()
            } else if (webSocket == null) {
                backoffDelayMs = 1000L
                connect()
            }
        }
    }

    fun onAppBackgrounded() {
        isAppForeground.set(false)
        reconnectJob?.cancel()
        simJob?.cancel()
        disconnectSocket(1000, "Backgrounded")
        robotStore.setWsConnected(false, "Live updates disconnected")
    }

    private fun disconnectSocket(code: Int, reason: String) {
        try {
            webSocket?.close(code, reason)
        } catch (e: Exception) {
            // ignore
        } finally {
            webSocket = null
        }
    }

    @Synchronized
    private fun connect() {
        if (!isAppForeground.get() || isExplicitlyStopped.get()) return

        if (settingsStore.isSimulationMode) {
            startSimulationTelemetry()
            return
        }

        val token = apiClient.accessToken
        if (token.isNullOrEmpty()) {
            robotStore.setWsConnected(false, "Authentication required")
            return
        }

        disconnectSocket(1000, "Reconnecting")
        val wsUrl = "${settingsStore.getWsUrl("/ws/dashboard")}?token=$token"

        robotStore.setWsConnected(false, "Connecting...")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = apiClient.okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened successfully")
                backoffDelayMs = 1000L
                robotStore.setWsConnected(true, "Connected")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing code=$code reason=$reason")
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed code=$code reason=$reason")
                robotStore.setWsConnected(false, "Live updates disconnected")
                handleClosure(code)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.message}")
                robotStore.setWsConnected(false, "Live updates disconnected")
                scheduleReconnect()
            }
        })
    }

    private fun startSimulationTelemetry() {
        simJob?.cancel()
        robotStore.updateRobotStatus(apiClient.simulationEngine.robotStatus)
        robotStore.setWsConnected(true, "Connected")

        simJob = scope.launch {
            var t = 0.0
            while (isActive && !isExplicitlyStopped.get() && isAppForeground.get()) {
                val st = robotStore.robotStatus.value
                if (st.control_mode != "STOPPED" && st.safety_state != "EMERGENCY_STOP") {
                    t += 0.05
                    val r = 3.5
                    val x = r * cos(t)
                    val y = r * sin(t)
                    val theta = (t + Math.PI / 2) % (2 * Math.PI)
                    val p = Pose(x = x, y = y, theta = theta, timestamp = System.currentTimeMillis())
                    robotStore.updatePose(p)
                    apiClient.simulationEngine.currentPose = p
                }
                delay(1000L)
            }
        }
    }

    private fun handleClosure(code: Int) {
        when (code) {
            CLOSE_BAD_TOKEN -> {
                scope.launch {
                    val refreshed = withContext(Dispatchers.IO) {
                        apiClient.refreshAccessTokenSync()
                    }
                    if (refreshed) {
                        connect()
                    } else {
                        robotStore.setWsConnected(false, "Token expired")
                    }
                }
            }
            CLOSE_NOT_ADMIN -> {
                onForbiddenAdmin()
            }
            else -> {
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        if (!isAppForeground.get() || isExplicitlyStopped.get() || settingsStore.isSimulationMode) return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(backoffDelayMs)
            backoffDelayMs = (backoffDelayMs * 2).coerceAtMost(16000L)
            connect()
        }
    }

    private fun handleMessage(text: String) {
        try {
            val root = JSONObject(text)
            val type = root.optString("type", "")
            val payload = root.optJSONObject("payload")

            when (type) {
                "robot.status_snapshot", "robot.online" -> {
                    if (payload != null) {
                        val status = parseRobotStatus(payload)
                        robotStore.updateRobotStatus(status)
                        if (status.id.isNotEmpty()) {
                            settingsStore.cachedRobotId = status.id
                        }
                    }
                }
                "robot.offline" -> {
                    val lastSeen = payload?.optString("last_seen", null)
                    robotStore.updateOnlineStatus(false, lastSeen)
                }
                "robot.mode_change" -> {
                    val mode = payload?.optString("mode", "") ?: ""
                    if (mode.isNotEmpty()) {
                        robotStore.updateMode(mode)
                    }
                }
                "robot.pose_update" -> {
                    if (payload != null) {
                        val pose = Pose(
                            x = payload.optDouble("x", 0.0),
                            y = payload.optDouble("y", 0.0),
                            theta = payload.optDouble("theta", 0.0),
                            timestamp = payload.opt("timestamp")
                        )
                        robotStore.updatePose(pose)
                    }
                }
                "robot.safety_update" -> {
                    val safety = payload?.optString("safety_state", "") ?: ""
                    if (safety.isNotEmpty()) {
                        robotStore.updateSafetyState(safety)
                    }
                }
                "robot.emergency_stop" -> {
                    robotStore.updateSafetyState("EMERGENCY_STOP")
                    robotStore.updateMode("STOPPED")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing websocket message: ${e.message}")
        }
    }

    private fun parseRobotStatus(json: JSONObject): RobotStatus {
        return RobotStatus(
            id = json.optString("id", ""),
            name = json.optString("name", "Robot"),
            status = json.optString("status", "OFFLINE"),
            control_mode = json.optString("control_mode", "STOPPED"),
            safety_state = json.optString("safety_state", "SAFE"),
            slam_state = json.optString("slam_state", "IDLE"),
            battery_level = if (json.has("battery_level") && !json.isNull("battery_level"))
                json.optDouble("battery_level") else null,
            last_seen = json.optString("last_seen", null),
            firmware_version = json.optString("firmware_version", null)
        )
    }
}
