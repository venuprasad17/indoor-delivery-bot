package com.example.robotoperator.data.api

import com.example.robotoperator.data.model.*
import com.example.robotoperator.data.prefs.ServerSettingsStore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SimulationEngine(
    private val settingsStore: ServerSettingsStore
) {
    // Current simulated robot state
    var robotStatus = RobotStatus(
        id = "bot-sim-01",
        name = "DeliveryBot-01 (Simulated)",
        status = "ONLINE",
        control_mode = "AUTONOMOUS",
        safety_state = "SAFE",
        slam_state = "TRACKING",
        battery_level = 88.0,
        last_seen = "Just now",
        firmware_version = "v2.4.1-indoor-sim"
    )

    var currentPose = Pose(
        x = 2.450,
        y = 1.320,
        theta = 0.785,
        timestamp = System.currentTimeMillis()
    )

    // In-memory data collections
    val sessions = mutableListOf(
        MappingSession(
            id = "sess-8a7f9b2c-sim",
            robot_id = "bot-sim-01",
            status = "MAPPING",
            elapsed_ms = 142000L,
            pose_update_count = 142,
            last_pose_json = Pose(2.450, 1.320, 0.785),
            slam_status = "TRACKING",
            map_data_ref = "floor_1_office",
            started_at = "10:14:02",
            created_at = "2026-09-28T10:14:02Z"
        ),
        MappingSession(
            id = "sess-3d1e4c7a-sim",
            robot_id = "bot-sim-01",
            status = "FINISHED",
            elapsed_ms = 890000L,
            pose_update_count = 890,
            last_pose_json = Pose(0.000, 0.000, 0.000),
            slam_status = "TRACKING",
            map_data_ref = "warehouse_zone_b",
            started_at = "08:30:00",
            finished_at = "08:44:50",
            created_at = "2026-09-28T08:30:00Z"
        )
    )

    val destinations = mutableListOf(
        Destination(
            id = "dest-home-001",
            name = "Home Base Dock",
            map_id = "floor_1_office",
            x = 0.000,
            y = 0.000,
            theta = 0.000,
            is_home = true,
            created_at = "2026-09-28T08:00:00Z"
        ),
        Destination(
            id = "dest-conf-a",
            name = "Conference Room A",
            map_id = "floor_1_office",
            x = 8.500,
            y = 4.250,
            theta = 1.571,
            is_home = false,
            created_at = "2026-09-28T08:05:00Z"
        ),
        Destination(
            id = "dest-lab-2",
            name = "Hardware Lab 2",
            map_id = "floor_1_office",
            x = 14.100,
            y = -2.800,
            theta = 3.142,
            is_home = false,
            created_at = "2026-09-28T08:10:00Z"
        )
    )

    val deliveries = mutableListOf(
        Delivery(
            id = "deliv-9021-sim",
            status = "GO_TO_DESTINATION",
            destination_id = "dest-conf-a",
            destination_name = "Conference Room A",
            robot_id = "bot-sim-01",
            history = listOf(
                DeliveryStepHistory("IDLE", "10:20:00", "Mission created"),
                DeliveryStepHistory("GO_TO_DESTINATION", "10:20:15", "En route to destination")
            ),
            created_at = "2026-09-28T10:20:00Z"
        ),
        Delivery(
            id = "deliv-8812-sim",
            status = "HOME",
            destination_id = "dest-lab-2",
            destination_name = "Hardware Lab 2",
            robot_id = "bot-sim-01",
            history = listOf(
                DeliveryStepHistory("IDLE", "09:00:00"),
                DeliveryStepHistory("GO_TO_DESTINATION", "09:00:10"),
                DeliveryStepHistory("ARRIVED", "09:05:30"),
                DeliveryStepHistory("DELIVERED", "09:06:15"),
                DeliveryStepHistory("RETURN_HOME", "09:06:30"),
                DeliveryStepHistory("HOME", "09:11:45")
            ),
            created_at = "2026-09-28T09:00:00Z"
        )
    )

    val users = mutableListOf(
        User(
            id = "usr-admin-1",
            email = settingsStore.lastLoginEmail,
            name = "Lead Robot Operator",
            role = "ADMIN",
            is_active = true
        ),
        User(
            id = "usr-ops-2",
            email = "fleet.monitor@robotics.local",
            name = "Fleet Teleoperation",
            role = "ADMIN",
            is_active = true
        )
    )

    val events = mutableListOf(
        EventItem(
            id = "evt-1001",
            type = "robot.online",
            severity = "INFO",
            payload_json = "{\"robot_id\":\"bot-sim-01\",\"ip\":\"10.0.2.15\"}",
            robot_id = "bot-sim-01",
            user_id = null,
            created_at = "2026-09-28T10:12:00Z"
        ),
        EventItem(
            id = "evt-1002",
            type = "mapping.session_started",
            severity = "INFO",
            payload_json = "{\"session_id\":\"sess-8a7f9b2c-sim\",\"map_ref\":\"floor_1_office\"}",
            robot_id = "bot-sim-01",
            user_id = "usr-admin-1",
            created_at = "2026-09-28T10:14:02Z"
        ),
        EventItem(
            id = "evt-1003",
            type = "safety.slow_zone",
            severity = "WARN",
            payload_json = "{\"reason\":\"Proximity sensor zone obstacle\",\"distance_m\":0.82}",
            robot_id = "bot-sim-01",
            user_id = null,
            created_at = "2026-09-28T10:16:45Z"
        )
    )

    init {
        settingsStore.cachedRobotId = robotStatus.id
    }

    fun handleRequest(request: Request): Response? {
        val path = request.url.encodedPath
        val method = request.method

        fun jsonResponse(code: Int, json: String): Response {
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("OK")
                .body(json.toResponseBody("application/json".toMediaType()))
                .build()
        }

        // GET /health
        if (path.endsWith("/health") && method == "GET") {
            return jsonResponse(200, """{"status":"ok"}""")
        }

        // POST /auth/login
        if (path.endsWith("/auth/login") && method == "POST") {
            return jsonResponse(
                200,
                """{"access_token":"sim_jwt_token_${System.currentTimeMillis()}","token_type":"bearer"}"""
            )
        }

        // GET /auth/me
        if (path.endsWith("/auth/me") && method == "GET") {
            val email = settingsStore.lastLoginEmail
            return jsonResponse(
                200,
                """{"id":"usr-admin-1","email":"$email","name":"Administrator Operator","role":"ADMIN","is_active":true}"""
            )
        }

        // POST /auth/refresh
        if (path.endsWith("/auth/refresh") && method == "POST") {
            return jsonResponse(
                200,
                """{"access_token":"sim_jwt_refreshed_${System.currentTimeMillis()}","token_type":"bearer"}"""
            )
        }

        // POST /auth/logout
        if (path.endsWith("/auth/logout") && method == "POST") {
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(204)
                .message("No Content")
                .body("".toResponseBody("application/json".toMediaType()))
                .build()
        }

        // GET /robot/status
        if (path.endsWith("/robot/status") && method == "GET") {
            val json = JSONObject().apply {
                put("id", robotStatus.id)
                put("name", robotStatus.name)
                put("status", robotStatus.status)
                put("control_mode", robotStatus.control_mode)
                put("safety_state", robotStatus.safety_state)
                put("slam_state", robotStatus.slam_state)
                put("battery_level", robotStatus.battery_level)
                put("last_seen", robotStatus.last_seen)
                put("firmware_version", robotStatus.firmware_version)
            }
            return jsonResponse(200, json.toString())
        }

        // POST /robot/mode
        if (path.endsWith("/robot/mode") && method == "POST") {
            val bodyStr = request.body?.let {
                val buffer = okio.Buffer()
                it.writeTo(buffer)
                buffer.readUtf8()
            } ?: "{}"
            val reqJson = JSONObject(bodyStr)
            val newMode = reqJson.optString("mode", "AUTONOMOUS")
            robotStatus = robotStatus.copy(control_mode = newMode)
            return jsonResponse(200, """{"status":"ok","mode":"$newMode"}""")
        }

        // POST /robot/emergency-stop/reset
        if (path.endsWith("/robot/emergency-stop/reset") && method == "POST") {
            robotStatus = robotStatus.copy(safety_state = "SAFE")
            return jsonResponse(
                200,
                """{"status":"ok","safety_state":"SAFE","needs_mode_reselection":true}"""
            )
        }

        // POST /robot/emergency-stop
        if (path.endsWith("/robot/emergency-stop") && method == "POST") {
            robotStatus = robotStatus.copy(safety_state = "EMERGENCY_STOP", control_mode = "STOPPED")
            return jsonResponse(
                200,
                """{"control_mode":"STOPPED","safety_state":"EMERGENCY_STOP","needs_mode_reselection":true}"""
            )
        }

        // GET /robot/safety-state
        if (path.endsWith("/robot/safety-state") && method == "GET") {
            return jsonResponse(
                200,
                """{"safety_state":"${robotStatus.safety_state}","timestamp":"2026-09-28T10:20:00Z"}"""
            )
        }

        // Mapping Sessions: GET & POST
        if (path.endsWith("/mapping/sessions") && method == "GET") {
            val array = JSONArray()
            sessions.forEach { s ->
                array.put(JSONObject().apply {
                    put("id", s.id)
                    put("robot_id", s.robot_id)
                    put("status", s.status)
                    put("elapsed_ms", s.elapsed_ms)
                    put("pose_update_count", s.pose_update_count)
                    put("slam_status", s.slam_status)
                    put("map_data_ref", s.map_data_ref)
                    put("started_at", s.started_at)
                    put("paused_at", s.paused_at)
                    put("finished_at", s.finished_at)
                    put("created_at", s.created_at)
                    if (s.last_pose_json != null) {
                        put("last_pose_json", JSONObject().apply {
                            put("x", s.last_pose_json.x)
                            put("y", s.last_pose_json.y)
                            put("theta", s.last_pose_json.theta)
                        })
                    }
                })
            }
            return jsonResponse(200, array.toString())
        }

        if (path.endsWith("/mapping/sessions") && method == "POST") {
            val newSession = MappingSession(
                id = "sess-" + UUID.randomUUID().toString().take(8) + "-sim",
                robot_id = robotStatus.id,
                status = "IDLE",
                elapsed_ms = 0L,
                pose_update_count = 0,
                last_pose_json = currentPose,
                slam_status = "TRACKING",
                created_at = "Just now"
            )
            sessions.add(0, newSession)
            val json = JSONObject().apply {
                put("id", newSession.id)
                put("robot_id", newSession.robot_id)
                put("status", newSession.status)
                put("elapsed_ms", newSession.elapsed_ms)
                put("pose_update_count", newSession.pose_update_count)
                put("slam_status", newSession.slam_status)
            }
            return jsonResponse(201, json.toString())
        }

        // Specific Mapping Session detail & actions
        if (path.contains("/mapping/sessions/")) {
            val sub = path.substringAfter("/mapping/sessions/")
            val parts = sub.split('/')
            val sId = parts[0]
            val sessionIndex = sessions.indexOfFirst { it.id == sId }
            val existing = if (sessionIndex != -1) sessions[sessionIndex] else sessions.firstOrNull()

            if (parts.size == 1 && method == "GET") {
                val s = existing ?: sessions.first()
                val json = JSONObject().apply {
                    put("id", s.id)
                    put("robot_id", s.robot_id)
                    put("status", s.status)
                    put("elapsed_ms", s.elapsed_ms)
                    put("pose_update_count", s.pose_update_count)
                    put("slam_status", s.slam_status)
                    put("map_data_ref", s.map_data_ref)
                    put("started_at", s.started_at)
                    put("paused_at", s.paused_at)
                    put("finished_at", s.finished_at)
                    put("created_at", s.created_at)
                    if (s.last_pose_json != null) {
                        put("last_pose_json", JSONObject().apply {
                            put("x", s.last_pose_json.x)
                            put("y", s.last_pose_json.y)
                            put("theta", s.last_pose_json.theta)
                        })
                    }
                }
                return jsonResponse(200, json.toString())
            }

            if (parts.size > 1 && method == "POST") {
                val action = parts[1]
                if (existing != null) {
                    val updated = when (action) {
                        "start" -> existing.copy(status = "MAPPING", started_at = "Just now")
                        "pause" -> existing.copy(status = "PAUSED", paused_at = "Just now")
                        "resume" -> existing.copy(status = "MAPPING")
                        "finish" -> existing.copy(status = "FINISHED", finished_at = "Just now")
                        else -> existing
                    }
                    if (sessionIndex != -1) sessions[sessionIndex] = updated
                    val json = JSONObject().apply {
                        put("id", updated.id)
                        put("robot_id", updated.robot_id)
                        put("status", updated.status)
                        put("elapsed_ms", updated.elapsed_ms)
                        put("pose_update_count", updated.pose_update_count)
                        put("slam_status", updated.slam_status)
                        put("map_data_ref", updated.map_data_ref)
                        put("started_at", updated.started_at)
                        put("paused_at", updated.paused_at)
                        put("finished_at", updated.finished_at)
                    }
                    return jsonResponse(200, json.toString())
                }
            }
        }

        // Destinations
        if (path.endsWith("/destinations") && method == "GET") {
            val array = JSONArray()
            destinations.forEach { d ->
                array.put(JSONObject().apply {
                    put("id", d.id)
                    put("name", d.name)
                    put("map_id", d.map_id)
                    put("x", d.x)
                    put("y", d.y)
                    put("theta", d.theta)
                    put("is_home", d.is_home)
                    put("created_at", d.created_at)
                })
            }
            return jsonResponse(200, array.toString())
        }

        if (path.endsWith("/destinations") && method == "POST") {
            val bodyStr = request.body?.let {
                val b = okio.Buffer()
                it.writeTo(b)
                b.readUtf8()
            } ?: "{}"
            val j = JSONObject(bodyStr)
            val newD = Destination(
                id = "dest-" + UUID.randomUUID().toString().take(8),
                name = j.optString("name", "New Destination"),
                map_id = j.optString("map_id", "floor_1_office"),
                x = j.optDouble("x", 0.0),
                y = j.optDouble("y", 0.0),
                theta = j.optDouble("theta", 0.0),
                is_home = j.optBoolean("is_home", false),
                created_at = "Just now"
            )
            destinations.add(newD)
            val resJson = JSONObject().apply {
                put("id", newD.id)
                put("name", newD.name)
                put("map_id", newD.map_id)
                put("x", newD.x)
                put("y", newD.y)
                put("theta", newD.theta)
                put("is_home", newD.is_home)
            }
            return jsonResponse(201, resJson.toString())
        }

        if (path.contains("/destinations/") && method == "DELETE") {
            val dId = path.substringAfter("/destinations/")
            val toRemove = destinations.firstOrNull { it.id == dId }
            if (toRemove != null) {
                if (toRemove.is_home) {
                    return jsonResponse(400, """{"error":"HOME_PROTECTED","message":"Cannot delete Home destination"}""")
                }
                destinations.remove(toRemove)
            }
            return jsonResponse(204, "")
        }

        // Deliveries
        if (path.endsWith("/deliveries") && method == "GET") {
            val array = JSONArray()
            deliveries.forEach { d ->
                array.put(JSONObject().apply {
                    put("id", d.id)
                    put("status", d.status)
                    put("destination_id", d.destination_id)
                    put("destination_name", d.destination_name)
                    put("robot_id", d.robot_id)
                    put("created_at", d.created_at)
                    val histArray = JSONArray()
                    d.history.forEach { h ->
                        histArray.put(JSONObject().apply {
                            put("status", h.status)
                            put("timestamp", h.timestamp)
                            put("note", h.note)
                        })
                    }
                    put("history", histArray)
                })
            }
            return jsonResponse(200, array.toString())
        }

        if (path.endsWith("/deliveries") && method == "POST") {
            val bodyStr = request.body?.let {
                val b = okio.Buffer()
                it.writeTo(b)
                b.readUtf8()
            } ?: "{}"
            val j = JSONObject(bodyStr)
            val destId = j.optString("destination_id")
            val destName = destinations.firstOrNull { it.id == destId }?.name ?: "Target"

            val newDeliv = Delivery(
                id = "deliv-" + UUID.randomUUID().toString().take(8) + "-sim",
                status = "GO_TO_DESTINATION",
                destination_id = destId,
                destination_name = destName,
                robot_id = robotStatus.id,
                history = listOf(
                    DeliveryStepHistory("IDLE", "Just now", "Mission created"),
                    DeliveryStepHistory("GO_TO_DESTINATION", "Just now", "Navigating to waypoint")
                ),
                created_at = "Just now"
            )
            deliveries.add(0, newDeliv)
            val resJson = JSONObject().apply {
                put("id", newDeliv.id)
                put("status", newDeliv.status)
                put("destination_id", newDeliv.destination_id)
                put("destination_name", newDeliv.destination_name)
            }
            return jsonResponse(201, resJson.toString())
        }

        if (path.contains("/deliveries/")) {
            val sub = path.substringAfter("/deliveries/")
            val parts = sub.split('/')
            val delivId = parts[0]
            val existing = deliveries.firstOrNull { it.id == delivId } ?: deliveries.firstOrNull()

            if (parts.size == 1 && method == "GET") {
                val d = existing ?: deliveries.first()
                val json = JSONObject().apply {
                    put("id", d.id)
                    put("status", d.status)
                    put("destination_id", d.destination_id)
                    put("destination_name", d.destination_name)
                    put("robot_id", d.robot_id)
                    put("created_at", d.created_at)
                    val histArray = JSONArray()
                    d.history.forEach { h ->
                        histArray.put(JSONObject().apply {
                            put("status", h.status)
                            put("timestamp", h.timestamp)
                            put("note", h.note)
                        })
                    }
                    put("history", histArray)
                }
                return jsonResponse(200, json.toString())
            }

            if (parts.size > 1 && method == "POST") {
                // Per spec: dispatch, confirm-pickup and cancel currently return 501
                return jsonResponse(501, """{"detail":"Not available yet on the server"}""")
            }
        }

        // Events
        if (path.endsWith("/events") && method == "GET") {
            val array = JSONArray()
            events.forEach { e ->
                array.put(JSONObject().apply {
                    put("id", e.id)
                    put("type", e.type)
                    put("severity", e.severity)
                    put("payload_json", e.payload_json)
                    put("robot_id", e.robot_id)
                    put("created_at", e.created_at)
                })
            }
            val resJson = JSONObject().apply {
                put("items", array)
                put("total", events.size)
                put("page", 1)
                put("page_size", 50)
            }
            return jsonResponse(200, resJson.toString())
        }

        // Users
        if (path.endsWith("/admin/users") && method == "GET") {
            val array = JSONArray()
            users.forEach { u ->
                array.put(JSONObject().apply {
                    put("id", u.id)
                    put("email", u.email)
                    put("name", u.name)
                    put("role", u.role)
                    put("is_active", u.is_active)
                })
            }
            return jsonResponse(200, array.toString())
        }

        if (path.endsWith("/admin/users") && method == "POST") {
            val bodyStr = request.body?.let {
                val b = okio.Buffer()
                it.writeTo(b)
                b.readUtf8()
            } ?: "{}"
            val j = JSONObject(bodyStr)
            val newU = User(
                id = "usr-" + UUID.randomUUID().toString().take(6),
                email = j.optString("email"),
                name = j.optString("name"),
                role = j.optString("role", "ADMIN"),
                is_active = true
            )
            users.add(newU)
            return jsonResponse(201, JSONObject().apply {
                put("id", newU.id)
                put("email", newU.email)
                put("name", newU.name)
                put("role", newU.role)
                put("is_active", newU.is_active)
            }.toString())
        }

        return jsonResponse(200, """{"status":"ok"}""")
    }
}
