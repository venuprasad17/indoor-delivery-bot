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
        assertTrue(isDeliveryActionAvailable(Delivery("d", status = "RETURN_HOME"), DeliveryAction.CANCEL))
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
}
