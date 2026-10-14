package com.example.robotoperator.data.api

import android.util.Log
import com.example.robotoperator.data.model.Pose
import com.example.robotoperator.data.model.RobotStatus
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.robotoperator.state.RobotStateHolder
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DashboardWebSocketClient(
    private val settingsStore: ServerSettingsStore,
    private val apiClient: ApiClient,
    private val stateHolder: RobotStateHolder
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
    private var currentBackoffMs = 1000L
    private val isStarted = AtomicBoolean(false)

    var onAdminForbidden: (() -> Unit)? = null

    fun start() {
        isStarted.set(true)
        currentBackoffMs = 1000L
        connect()
    }

    fun stop() {
        isStarted.set(false)
        reconnectJob?.cancel()
        webSocket?.close(1000, "App backgrounded")
        webSocket = null
        stateHolder.setLiveConnected(false)
    }

    fun reconnect() {
        if (!isStarted.get()) return
        reconnectJob?.cancel()
        currentBackoffMs = 1000L
        webSocket?.close(1000, "Forced reconnect")
        webSocket = null
        connect()
    }

    private fun connect() {
        if (!isStarted.get()) return
        val token = apiClient.getAccessToken()
        val wsUrl = settingsStore.getWebSocketUrl(token)

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                stateHolder.setLiveConnected(true)
                currentBackoffMs = 1000L
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                stateHolder.setLiveConnected(false)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                stateHolder.setLiveConnected(false)
                handleDisconnection(code)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                stateHolder.setLiveConnected(false)
                val code = response?.code ?: 0
                if (code == 403) {
                    onAdminForbidden?.invoke()
                    return
                }
                scheduleReconnect()
            }
        })
    }

    private fun handleDisconnection(code: Int) {
        when (code) {
            4001 -> {
                // Invalid or expired token: refresh and reconnect
                coroutineScope.launch {
                    val refreshed = apiClient.refreshAuth()
                    if (refreshed) {
                        currentBackoffMs = 1000L
                        connect()
                    } else {
                        scheduleReconnect()
                    }
                }
            }
            4003 -> {
                // Not an admin: stop reconnecting and notify to log out
                isStarted.set(false)
                reconnectJob?.cancel()
                onAdminForbidden?.invoke()
            }
            else -> {
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        if (!isStarted.get()) return
        reconnectJob?.cancel()
        reconnectJob = coroutineScope.launch {
            delay(currentBackoffMs)
            currentBackoffMs = (currentBackoffMs * 2).coerceAtMost(16000L)
            connect()
        }
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
                        stateHolder.updateRobotStatus(status)
                        if (status.id.isNotBlank()) {
                            settingsStore.cachedRobotId = status.id
                        }
                    } else {
                        stateHolder.updateOnline(true)
                    }
                }
                "robot.offline" -> {
                    val lastSeen = payloadObj?.optString("last_seen", null)
                    stateHolder.updateOnline(false, lastSeen)
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
                        stateHolder.updateSafetyState(safety)
                    }
                }
                "robot.emergency_stop" -> {
                    stateHolder.updateSafetyState("EMERGENCY_STOP")
                    val reason = payloadObj?.optString("reason")
                    stateHolder.setEmergencyStopError(reason ?: "Emergency stop triggered")
                }
            }
        } catch (e: Exception) {
            Log.e("DashboardWS", "Error parsing WebSocket message", e)
        }
    }
}
