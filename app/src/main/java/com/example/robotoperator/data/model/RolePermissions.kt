package com.example.robotoperator.data.model

enum class DeliveryAction {
    DISPATCH,
    CONFIRM_PICKUP,
    CANCEL
}

fun isAdministratorRole(role: String?): Boolean = role.equals("ADMIN", ignoreCase = true)

/** Main tabs backed by endpoints available to the current role. */
fun visibleMainTabNames(role: String?): Set<String> = if (isAdministratorRole(role)) {
    setOf("DASHBOARD", "MAPPING", "LIVE_CONSOLE", "DESTINATIONS", "DELIVERIES", "MORE")
} else {
    setOf("DELIVERIES", "MORE")
}

fun initialMainTabName(role: String?): String =
    if (isAdministratorRole(role)) "DASHBOARD" else "DELIVERIES"

fun canAccessRobotDashboard(role: String?): Boolean = isAdministratorRole(role)

fun isDeliveryActionVisible(role: String?, action: DeliveryAction): Boolean {
    if (role.isNullOrBlank()) return false
    return when (action) {
        DeliveryAction.DISPATCH, DeliveryAction.CANCEL -> isAdministratorRole(role)
        DeliveryAction.CONFIRM_PICKUP -> true // Backend additionally enforces requester ownership.
    }
}

data class RobotAccessRevocation(
    val userId: String,
    val robotId: String
)

fun RobotAccessGrant.toRevocationPath() = RobotAccessRevocation(
    userId = user_id,
    robotId = robot_id
)
