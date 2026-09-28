package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.robotoperator.data.api.ApiErrorParser
import com.example.robotoperator.data.api.SimulationEngine
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.example.robotoperator.state.RobotStateHolder
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read app name string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Robot Operator", appName)
  }

  @Test
  fun `server settings store derives websocket url correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = ServerSettingsStore(context)
    store.serverUrl = "http://192.168.1.50:8000"
    assertEquals("ws://192.168.1.50:8000/ws/dashboard", store.getWsUrl("/ws/dashboard"))

    store.serverUrl = "https://robot.fleet.example.com"
    assertEquals("wss://robot.fleet.example.com/ws/dashboard", store.getWsUrl("/ws/dashboard"))
  }

  @Test
  fun `api error parser handles detail string and detail object`() {
    val detailObjJson = """{"detail": {"error": "SESSION_ACTIVE", "active_session_id": "sess-123"}}"""
    val err1 = ApiErrorParser.parseHttpError(409, detailObjJson)
    assertEquals("Another session is active", err1.message)
    assertEquals("sess-123", err1.activeSessionId)

    val detailStrJson = """{"detail": "Authentication credentials were not provided."}"""
    val err2 = ApiErrorParser.parseHttpError(401, detailStrJson)
    assertEquals("Authentication credentials were not provided.", err2.message)

    val err429 = ApiErrorParser.parseHttpError(429, null)
    assertEquals("Too many attempts. Try again later.", err429.message)

    val err501 = ApiErrorParser.parseHttpError(501, null)
    assertEquals("Not available yet on the server", err501.message)

    val errLocked = ApiErrorParser.parseHttpError(423, null)
    assertEquals("Locked by emergency stop", errLocked.message)
  }

  @Test
  fun `simulation engine handles health and login requests`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = ServerSettingsStore(context)
    val simEngine = SimulationEngine(store)

    val healthReq = Request.Builder().url("http://10.0.2.2:8000/health").get().build()
    val healthRes = simEngine.handleRequest(healthReq)
    assertNotNull(healthRes)
    assertEquals(200, healthRes?.code)

    val statusReq = Request.Builder().url("http://10.0.2.2:8000/robot/status").get().build()
    val statusRes = simEngine.handleRequest(statusReq)
    assertNotNull(statusRes)
    assertEquals(200, statusRes?.code)
  }

  @Test
  fun `robot state holder updates safety state and mode`() {
    val store = RobotStateHolder()
    assertEquals("SAFE", store.robotStatus.value.safety_state)
    assertEquals("STOPPED", store.robotStatus.value.control_mode)

    store.updateSafetyState("EMERGENCY_STOP")
    assertEquals("EMERGENCY_STOP", store.robotStatus.value.safety_state)

    store.updateMode("AUTONOMOUS")
    assertEquals("AUTONOMOUS", store.robotStatus.value.control_mode)
  }
}
