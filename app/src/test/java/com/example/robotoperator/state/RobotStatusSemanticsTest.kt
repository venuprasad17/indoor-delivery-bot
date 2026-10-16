package com.example.robotoperator.state

import com.example.robotoperator.data.model.RobotStatus
import com.example.robotoperator.data.model.normalizedConnectionState
import com.example.robotoperator.data.model.safetyTelemetryContext
import com.example.robotoperator.data.model.safetyTelemetryLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotStatusSemanticsTest {
    @Test
    fun explicitlyReportsOnlineStaleAndOfflineFreshness() {
        assertEquals("ONLINE", normalizedConnectionState("online"))
        assertEquals("STALE", normalizedConnectionState("STALE"))
        assertEquals("OFFLINE", normalizedConnectionState("offline"))
        assertTrue(safetyTelemetryContext("STALE").contains("stale"))
        assertTrue(safetyTelemetryContext("OFFLINE").contains("historical"))
    }

    @Test
    fun missingConnectionAndSafetyTelemetryStayUnknownEvenWithLegacyOnlineStatus() {
        val status = RobotStatus(status = "ONLINE")

        assertEquals("UNKNOWN", normalizedConnectionState(status.connection_state))
        assertEquals("UNKNOWN", safetyTelemetryLabel(status.safety_state))
        assertTrue(safetyTelemetryContext(status.connection_state).contains("connection is unknown"))
        assertFalse(safetyTelemetryContext(status.connection_state).contains("safe"))
    }

    @Test
    fun safeTelemetryRemainsHistoricalAfterConnectionLoss() {
        val stateHolder = RobotStateHolder()
        stateHolder.updateSafetyState("SAFE", "2026-10-15T10:00:00Z")
        stateHolder.updateConnectionState("OFFLINE", "2026-10-15T10:00:00Z")

        val status = stateHolder.robotStatus.value
        assertEquals("SAFE", status.safety_state)
        assertEquals("OFFLINE", normalizedConnectionState(status.connection_state))
        assertEquals("LAST REPORTED SAFE", safetyTelemetryLabel(status.safety_state))
        val context = safetyTelemetryContext(status.connection_state)
        assertTrue(context.contains("historical"))
        assertTrue(context.contains("physical condition is unverified"))
    }

    @Test
    fun dashboardFeedLossMakesOnlineRobotObservationStaleWithoutClaimingOfflineOrSafe() {
        val stateHolder = RobotStateHolder()
        stateHolder.updateRobotStatus(RobotStatus(status = "ONLINE", connection_state = "ONLINE", safety_state = "SAFE"))
        stateHolder.setLiveConnected(true)
        stateHolder.setLiveConnected(false)

        val status = stateHolder.robotStatus.value
        assertEquals("ONLINE", status.status)
        assertEquals("STALE", normalizedConnectionState(status.connection_state))
        assertEquals("LAST REPORTED SAFE", safetyTelemetryLabel(status.safety_state))
        val context = safetyTelemetryContext(status.connection_state, dashboardFeedConnected = false).lowercase()
        assertTrue(context.contains("dashboard feed is disconnected"))
        assertTrue(context.contains("last reported and stale"))
        assertTrue(context.contains("physical condition is unverified"))
        assertFalse(context.contains("robot is offline"))
    }
}
