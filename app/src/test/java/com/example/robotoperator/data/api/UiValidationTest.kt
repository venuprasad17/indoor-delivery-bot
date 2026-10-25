package com.example.robotoperator.data.api

import com.example.robotoperator.data.model.*
import org.junit.Assert.*
import org.junit.Test

class UiValidationTest {
    @Test fun coordinatesRejectInvalidAndNonFiniteValues() {
        listOf("", " ", "oops", "NaN", "Infinity", "-Infinity", "1e999").forEach {
            assertNull(it, finiteCoordinate(it))
        }
        assertEquals(-1.25, finiteCoordinate(" -1.25 ")!!, 0.0)
        assertEquals(0.0, finiteCoordinate("0")!!, 0.0)
    }

    @Test fun actionsFollowLifecycle() {
        assertTrue(isDeliveryActionAvailable(Delivery("d"), DeliveryAction.DISPATCH))
        assertTrue(isDeliveryActionAvailable(Delivery("d", status = "ARRIVED"), DeliveryAction.CONFIRM_PICKUP))
        assertFalse(isDeliveryActionAvailable(Delivery("d", status = "ARRIVED"), DeliveryAction.DISPATCH))
        assertFalse(isDeliveryActionAvailable(Delivery("d", status = "RETURN_HOME"), DeliveryAction.CANCEL))
        val completed = Delivery("d", history = listOf(DeliveryHistoryItem(from = "HOME", to = "IDLE")))
        DeliveryAction.values().forEach { assertFalse(isDeliveryActionAvailable(completed, it)) }
        DeliveryAction.values().forEach { assertFalse(isDeliveryActionAvailable(Delivery("d", status = "UNKNOWN"), it)) }
    }

    @Test fun primaryNavigationIsCompactAndToolsRemainRoleRestricted() {
        assertEquals(setOf("DASHBOARD", "DELIVERIES", "MORE"), primaryMainTabNames("ADMIN"))
        assertEquals(setOf("DELIVERIES", "MORE"), primaryMainTabNames("USER"))
        assertTrue(visibleMainTabNames("ADMIN").containsAll(setOf("MAPPING", "LIVE_CONSOLE", "DESTINATIONS")))
        assertFalse(visibleMainTabNames("USER").contains("MAPPING"))
    }

    @Test fun pickupAndDestinationMustShareRobotAndMap() {
        val source = Destination("a", "Pickup", 0.0, 0.0, map_id = "floor1", robot_id = "r1")
        val destination = source.copy(id = "b", name = "Drop-off")
        assertTrue(isValidDeliveryRoute(source, destination))
        assertFalse(isValidDeliveryRoute(source, source))
        assertFalse(isValidDeliveryRoute(null, destination))
        assertFalse(isValidDeliveryRoute(source, destination.copy(map_id = "floor2")))
        assertFalse(isValidDeliveryRoute(source, destination.copy(robot_id = "r2")))
        assertFalse(isValidDeliveryRoute(source.copy(robot_id = null), destination))
    }

    @Test fun loadingOnlyAllowedAtSource() {
        assertTrue(isDeliveryActionAvailable(Delivery("d", status = "AT_SOURCE"), DeliveryAction.CONFIRM_LOADING))
        listOf("IDLE", "GO_TO_SOURCE", "GO_TO_DESTINATION", "ARRIVED").forEach {
            assertFalse(isDeliveryActionAvailable(Delivery("d", status = it), DeliveryAction.CONFIRM_LOADING))
        }
        assertTrue(isDeliveryActionVisible("USER", DeliveryAction.CONFIRM_LOADING))
    }
}
