package com.example.robotoperator.data.api

import com.example.robotoperator.data.model.Delivery
import com.example.robotoperator.data.model.DeliveryAction
import com.example.robotoperator.data.model.DeliveryHistoryItem
import com.example.robotoperator.data.model.ModeResponse
import com.example.robotoperator.data.model.RobotAccessGrant
import com.example.robotoperator.data.model.SystemEvent
import com.example.robotoperator.data.model.deliveryProgressIndex
import com.example.robotoperator.data.model.deliveryProgressSteps
import com.example.robotoperator.data.model.isDeliveryActionVisible
import com.example.robotoperator.data.model.isDeliveryLifecycleComplete
import com.example.robotoperator.data.model.canAccessRobotDashboard
import com.example.robotoperator.data.model.initialMainTabName
import com.example.robotoperator.data.model.visibleMainTabNames
import com.example.robotoperator.data.model.toRevocationPath
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

class FrontendContractTest {
    private val moshi = Moshi.Builder().build()

    @Test
    fun parsesDedicatedModeResponseContract() {
        val response = moshi.adapter(ModeResponse::class.java)
            .fromJson("""{"mode":"AUTONOMOUS","safety_state":"SAFE"}""")!!

        assertEquals("AUTONOMOUS", response.mode)
        assertEquals("SAFE", response.safety_state)
    }

    @Test
    fun parsesEventPayloadAsStructuredJson() {
        val event = moshi.adapter(SystemEvent::class.java).fromJson(
            """{"id":"event-1","type":"delivery.state_transition","severity":"INFO","payload_json":{"actor_id":"user-1","transition":{"to":"HOME"}}}"""
        )!!

        assertEquals("user-1", event.payload_json["actor_id"])
        @Suppress("UNCHECKED_CAST")
        val transition = event.payload_json["transition"] as Map<String, Any?>
        assertEquals("HOME", transition["to"])
    }

    @Test
    fun mapsRobotAccessGrantToItsRevocationIdentifiers() {
        val grant = moshi.adapter(RobotAccessGrant::class.java).fromJson(
            """{"user_id":"user-1","robot_id":"robot-7","granted_by":"admin-1","granted_at":"2026-10-15T10:00:00Z"}"""
        )!!

        val revokePath = grant.toRevocationPath()
        assertEquals("user-1", revokePath.userId)
        assertEquals("robot-7", revokePath.robotId)
    }

    @Test
    fun mapsBackendDeliveryHistoryFieldsAndHomeThenIdleCompletion() {
        val delivery = moshi.adapter(Delivery::class.java).fromJson(
            """{"id":"delivery-1","status":"IDLE","history":[{"from":"HOME","to":"IDLE","timestamp":"2026-10-15T10:00:00Z","actor_id":"user-1","reason":"delivery lifecycle completed"}]}"""
        )!!
        val transition = delivery.history.single()

        assertEquals("HOME", transition.from)
        assertEquals("IDLE", transition.to)
        assertEquals("user-1", transition.actor_id)
        assertEquals("delivery lifecycle completed", transition.reason)
        assertTrue(isDeliveryLifecycleComplete(delivery))
        assertEquals(deliveryProgressSteps.lastIndex, deliveryProgressIndex(delivery))

        val initialIdle = Delivery(id = "initial", status = "IDLE")
        val homeIntermediate = Delivery(id = "home", status = "HOME")
        assertFalse(isDeliveryLifecycleComplete(initialIdle))
        assertEquals(-1, deliveryProgressIndex(initialIdle))
        assertFalse(isDeliveryLifecycleComplete(homeIntermediate))
        assertEquals(deliveryProgressSteps.lastIndex, deliveryProgressIndex(homeIntermediate))
    }

    @Test
    fun hidesAdministratorOnlyDeliveryActionsFromStandardUsers() {
        assertFalse(isDeliveryActionVisible("USER", DeliveryAction.DISPATCH))
        assertFalse(isDeliveryActionVisible("USER", DeliveryAction.CANCEL))
        assertTrue(isDeliveryActionVisible("USER", DeliveryAction.CONFIRM_PICKUP))
        assertTrue(isDeliveryActionVisible("ADMIN", DeliveryAction.DISPATCH))
        assertTrue(isDeliveryActionVisible("ADMIN", DeliveryAction.CANCEL))
        assertFalse(isDeliveryActionVisible(null, DeliveryAction.CONFIRM_PICKUP))
    }

    @Test
    fun keepsAuthorizationFailureDistinctFromAuthenticationFailure() {
        val authenticationError = ApiErrorParser.parse(httpError(401))
        val authorizationError = ApiErrorParser.parse(httpError(403))

        assertEquals(401, authenticationError.statusCode)
        assertTrue(authenticationError.message.contains("session expired"))
        assertEquals(403, authorizationError.statusCode)
        assertTrue(authorizationError.message.contains("not authorized"))
    }

    @Test
    fun routesUsersToAuthorizedDeliveriesAndKeepsAdminDashboardAccess() {
        assertEquals("DELIVERIES", initialMainTabName("USER"))
        assertTrue("DASHBOARD" !in visibleMainTabNames("USER"))
        assertTrue("MAPPING" !in visibleMainTabNames("USER"))
        assertTrue("DESTINATIONS" !in visibleMainTabNames("USER"))
        assertTrue("DELIVERIES" in visibleMainTabNames("USER"))
        assertFalse(canAccessRobotDashboard("USER"))
        assertFalse(canAccessRobotDashboard(null))
        assertTrue("DASHBOARD" !in visibleMainTabNames(null))

        assertEquals("DASHBOARD", initialMainTabName("ADMIN"))
        assertTrue("DASHBOARD" in visibleMainTabNames("ADMIN"))
        assertTrue("MAPPING" in visibleMainTabNames("ADMIN"))
        assertTrue("DESTINATIONS" in visibleMainTabNames("ADMIN"))
        assertTrue(canAccessRobotDashboard("ADMIN"))
    }

    private fun httpError(code: Int): HttpException {
        val request = Request.Builder().url("https://example.test/").build()
        val body = "".toResponseBody("application/json".toMediaType())
        val rawResponse = okhttp3.Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("error")
            .body(body)
            .build()
        return HttpException(retrofit2.Response.error<Any>(body, rawResponse))
    }
}
