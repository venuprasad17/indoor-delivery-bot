package com.example.robotoperator.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RobotConnectionSupportTest {
    @Test
    fun acceptsPrivateIpv4AndPort() {
        assertEquals(RobotConnectionTarget("192.168.1.20", 9090), parseRobotConnectionTarget("192.168.1.20", "9090"))
        assertEquals(RobotConnectionTarget("10.0.0.8", 8080), parseRobotConnectionTarget("10.0.0.8", "8080"))
        assertEquals(RobotConnectionTarget("192.0.0.4", 9090), parseRobotConnectionTarget("192.0.0.4", "9090"))
    }

    @Test
    fun rejectsPublicMalformedAndNonIpv4Addresses() {
        listOf("8.8.8.8", "127.0.0.1", "192.168.1.999", "robot.local", "::1").forEach { ip ->
            assertNull("$ip must not be accepted", parseRobotConnectionTarget(ip, "9090"))
        }
    }

    @Test
    fun rejectsInvalidPorts() {
        listOf("0", "-1", "65536", "abc").forEach { port ->
            assertNull("$port must not be accepted", parseRobotConnectionTarget("192.168.1.20", port))
        }
    }
}
