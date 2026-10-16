package com.example.robotoperator.data.api

internal data class RobotConnectionTarget(val ip: String, val port: Int)

/** Validate an explicit development-only IPv4 robot address before the API call. */
internal fun parseRobotConnectionTarget(ipValue: String, portValue: String): RobotConnectionTarget? {
    val octets = ipValue.trim().split('.')
    if (octets.size != 4 || octets.any { it.isEmpty() || (it.length > 1 && it.startsWith('0')) }) return null
    val bytes = octets.map { it.toIntOrNull()?.takeIf { value -> value in 0..255 } ?: return null }
    val privateAddress = bytes[0] == 10 ||
        (bytes[0] == 172 && bytes[1] in 16..31) ||
        (bytes[0] == 192 && bytes[1] == 168) ||
        bytes == listOf(192, 0, 0, 4)
    if (!privateAddress) return null
    val port = portValue.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    return RobotConnectionTarget(bytes.joinToString("."), port)
}
