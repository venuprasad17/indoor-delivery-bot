package com.example.robotoperator.data.api

import android.util.Log
import com.example.robotoperator.data.prefs.ServerSettingsStore
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Defensive client for secondary WebSocket `<ws-base>/ws/robot`.
 * Logs incoming messages without crashing on undocumented payload structures.
 */
class RobotWebSocketClient(
    private val settingsStore: ServerSettingsStore,
    private val apiClient: ApiClient
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null
    private var currentBackoffMs = 1000L
    private val isStarted = AtomicBoolean(false)

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
        val base = settingsStore.serverUrl
        val wsBase = when {
            base.startsWith("https://", ignoreCase = true) -> base.replaceFirst("https://", "wss://", ignoreCase = true)
            base.startsWith("http://", ignoreCase = true) -> base.replaceFirst("http://", "ws://", ignoreCase = true)
            else -> "ws://$base"
        }
        val url = if (!token.isNullOrBlank()) {
            "$wsBase/ws/robot?token=$token"
        } else {
            "$wsBase/ws/robot"
        }

        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("RobotWS", "Connected to /ws/robot successfully")
                currentBackoffMs = 1000L
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Defensively log payload; payload shape is undocumented
                Log.i("RobotWS", "Incoming /ws/robot payload: $text")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("RobotWS", "/ws/robot closing code=$code reason=$reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("RobotWS", "/ws/robot closed code=$code reason=$reason")
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("RobotWS", "/ws/robot failure: ${t.localizedMessage} code=${response?.code}")
                scheduleReconnect()
            }
        })
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
}
