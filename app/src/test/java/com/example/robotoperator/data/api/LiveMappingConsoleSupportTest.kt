package com.example.robotoperator.ui.screens.mapping

import com.example.robotoperator.data.model.visibleMainTabNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMappingConsoleSupportTest {
    @Test
    fun cameraDocumentUsesTheVerifiedMjpegPathAndTransparentBackground() {
        val html = liveCameraHtml()

        assertEquals("http://192.0.0.4:8080/video", LIVE_CAMERA_STREAM_URL)
        assertTrue(html.contains("background:transparent"))
        assertTrue(html.contains("<img src=\"$LIVE_CAMERA_STREAM_URL\""))
    }

    @Test
    fun liveConsoleIsVisibleOnlyToAdministrators() {
        assertTrue("LIVE_CONSOLE" in visibleMainTabNames("ADMIN"))
        assertFalse("LIVE_CONSOLE" in visibleMainTabNames("USER"))
    }
}
