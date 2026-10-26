package com.example.robotoperator.data.api

import android.util.Log
import com.example.robotoperator.data.model.Pose
import com.example.robotoperator.data.model.RobotStatus
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.robotoperator.state.RobotStateHolder
import com.example.robotoperator.state.DashboardConnectionState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import retrofit2.HttpException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DashboardWebSocketClient(
    private val settingsStore: ServerSettingsStore,
    private val apiClient: ApiClient,
    private val stateHolder: RobotStateHolder,
    private val ticketProvider: suspend () -> String = {
        apiClient.getService().createDashboardWebSocketTicket().ticket
    }
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val robotStatusAdapter = moshi.adapter(RobotStatus::class.java)
    private val poseAdapter = moshi.adapter(Pose::class.java)

    private var webSocket: WebSocket? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null
    private var stableConnectionJob: Job? = null
    private val reconnectBackoff = ReconnectBackoff()
    private val isStarted = AtomicBoolean(false)

    var onAdminForbidden: (() -> Unit)? = null
    var onAuthenticationRequired: ((String) -> Unit)? = null

    fun start() {
        isStarted.set(true)
        reconnectBackoff.reset()
        stateHolder.setDashboardConnectionState(DashboardConnectionState.CONNECTING)
        connect()
    }

    fun stop() {
        isStarted.set(false)
        reconnectJob?.cancel()
        stableConnectionJob?.cancel()
        reconnectBackoff.reset()
        webSocket?.close(1000, "App backgrounded")
        webSocket = null
        stateHolder.setLiveConnected(false)
    }

    fun reconnect() {
        if (!isStarted.get()) return
        reconnectJob?.cancel()
        stableConnectionJob?.cancel()
        reconnectBackoff.reset()
        webSocket?.close(1000, "Forced reconnect")
        webSocket = null
        connect()
    }

    private fun connect() {
        if (!isStarted.get()) return
        stateHolder.setDashboardConnectionState(DashboardConnectionState.CONNECTING)
        reconnectJob = coroutineScope.launch {
            val request = try {
                Request.Builder()
                    .url(
                        dashboardConnectionUrl(
                            settingsStore.requireServerUrl(),
                            settingsStore.requireSelectedEnvironment(),
                            ticketProvider
                        )
                    )
                    .build()
            } catch (error: Exception) {
                if (error is HttpException && error.code() in listOf(401, 403)) {
                    if (error.code() == 403) {
                        isStarted.set(false)
                        onAdminForbidden?.invoke()
                    } else {
                        stopForAuthentication("Your session could not be refreshed. Please sign in again.")
                    }
                } else {
                    scheduleReconnect()
                }
                return@launch
            }
            if (!isStarted.get()) return@launch

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                stateHolder.setDashboardConnectionState(DashboardConnectionState.CONNECTED)
                stableConnectionJob?.cancel()
                stableConnectionJob = coroutineScope.launch {
                    delay(30_000L)
                    reconnectBackoff.reset()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                stableConnectionJob?.cancel()
                stateHolder.setDashboardConnectionState(DashboardConnectionState.RECONNECTING)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                stableConnectionJob?.cancel()
                stateHolder.setDashboardConnectionState(DashboardConnectionState.RECONNECTING)
                handleDisconnection(code)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                stableConnectionJob?.cancel()
                stateHolder.setDashboardConnectionState(DashboardConnectionState.RECONNECTING)
                val code = response?.code ?: 0
                if (code == 401 || code == 403) {
                    if (code == 403) {
                        isStarted.set(false)
                        onAdminForbidden?.invoke()
                    } else {
                        stopForAuthentication("Your session is no longer valid. Please sign in again.")
                    }
                    return
                }
                scheduleReconnect()
            }
            })
        }
    }

    private fun handleDisconnection(code: Int) {
        when (dashboardDisconnectAction(code)) {
            DashboardDisconnectAction.RETRY_WITH_NEW_TICKET -> {
                // Expired tickets and transient closes reconnect through connect(), which obtains
                // a fresh one-use ticket; authenticated HTTP handles access-token refresh.
                scheduleReconnect()
            }
            DashboardDisconnectAction.REQUIRE_AUTHENTICATION -> {
                // A fresh one-use ticket was rejected: the session or dashboard grant is invalid.
                stopForAuthentication("Dashboard access was revoked or your session ended. Please sign in again.")
            }
        }
    }

    private fun scheduleReconnect() {
        if (!isStarted.get()) return
        stateHolder.setDashboardConnectionState(DashboardConnectionState.RECONNECTING)
        val delayMs = reconnectBackoff.nextDelayMs()
        if (delayMs == null) {
            isStarted.set(false)
            webSocket?.cancel()
            webSocket = null
            stateHolder.setDashboardConnectionState(DashboardConnectionState.DISCONNECTED)
            return
        }
        reconnectJob?.cancel()
        reconnectJob = coroutineScope.launch {
            delay(delayMs)
            connect()
        }
    }

    private fun stopForAuthentication(message: String) {
        isStarted.set(false)
        reconnectJob?.cancel()
        webSocket?.cancel()
        webSocket = null
        stateHolder.setDashboardConnectionState(DashboardConnectionState.AUTH_REQUIRED)
        onAuthenticationRequired?.invoke(message)
    }

    private fun handleMessage(jsonStr: String) {
        try {
            val root = JSONObject(jsonStr)
            val type = root.optString("type")
            val payloadObj = root.optJSONObject("payload")
            val payloadStr = payloadObj?.toString() ?: ""

            when (type) {
                "robot.status_snapshot" -> {
                    val status = robotStatusAdapter.fromJson(payloadStr)
                    if (status != null) {
                        stateHolder.updateRobotStatus(status)
                        if (status.id.isNotBlank()) {
                            settingsStore.cachedRobotId = status.id
                        }
                    }
                }
                "robot.online" -> {
                    val status = robotStatusAdapter.fromJson(payloadStr)
                    if (status != null) {
                        stateHolder.updateRobotStatus(status.copy(connection_state = "ONLINE"))
                        if (status.id.isNotBlank()) {
                            settingsStore.cachedRobotId = status.id
                        }
                    } else {
                        stateHolder.updateConnectionState("ONLINE")
                    }
                }
                "robot.freshness" -> {
                    val connectionState = payloadObj?.optString("connection_state")
                    if (!connectionState.isNullOrBlank()) {
                        stateHolder.updateConnectionState(
                            connectionState,
                            payloadObj.optString("last_seen").takeIf { it.isNotBlank() && it != "null" },
                            clearLastSeen = payloadObj.has("last_seen") && payloadObj.isNull("last_seen")
                        )
                    }
                }
                "robot.offline" -> {
                    val lastSeen = payloadObj?.optString("last_seen")?.takeIf { it.isNotBlank() && it != "null" }
                    stateHolder.updateConnectionState("OFFLINE", lastSeen)
                }
                "robot.mode_change" -> {
                    val mode = payloadObj?.optString("mode")
                    if (!mode.isNullOrBlank()) {
                        stateHolder.updateControlMode(mode)
                    }
                }
                "robot.pose_update" -> {
                    val pose = poseAdapter.fromJson(payloadStr)
                    if (pose != null) {
                        stateHolder.updatePose(pose)
                    }
                }
                "robot.safety_update" -> {
                    val safety = payloadObj?.optString("safety_state")
                    if (!safety.isNullOrBlank()) {
                        stateHolder.updateSafetyState(safety, payloadObj.optString("timestamp").takeIf { it.isNotBlank() && it != "null" })
                    }
                }
                "robot.telemetry" -> {
                    val safety = payloadObj?.optString("safety_state")
                    if (!safety.isNullOrBlank()) {
                        stateHolder.updateSafetyState(
                            safety,
                            payloadObj.optString("timestamp").takeIf { it.isNotBlank() && it != "null" }
                        )
                    }
                    val connectionState = payloadObj?.optString("connection_state")
                    if (!connectionState.isNullOrBlank()) {
                        stateHolder.updateConnectionState(connectionState)
                    }
                }
                "robot.emergency_stop" -> {
                    stateHolder.updateSafetyState("EMERGENCY_STOP", payloadObj?.optString("timestamp")?.takeIf { it.isNotBlank() && it != "null" })
                    val reason = payloadObj?.optString("reason")
                    stateHolder.setEmergencyStopError(reason ?: "Emergency stop triggered")
                }
            }
        } catch (e: Exception) {
            Log.e("DashboardWS", "Error parsing WebSocket message", e)
        }
    }
}
