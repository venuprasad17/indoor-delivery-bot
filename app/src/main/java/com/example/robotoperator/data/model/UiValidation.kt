package com.example.robotoperator.data.model

/** UI validation only. The server remains responsible for map and access checks. */
fun finiteCoordinate(value: String): Double? = value.trim().toDoubleOrNull()?.takeIf { it.isFinite() }

fun isDeliveryActionAvailable(delivery: Delivery, action: DeliveryAction): Boolean {
    if (isDeliveryLifecycleComplete(delivery)) return false
    return when (action) {
        DeliveryAction.DISPATCH -> delivery.status.equals("IDLE", ignoreCase = true)
        DeliveryAction.CONFIRM_PICKUP -> delivery.status.equals("ARRIVED", ignoreCase = true)
        DeliveryAction.CONFIRM_LOADING -> delivery.status.equals("AT_SOURCE", ignoreCase = true)
        DeliveryAction.CANCEL -> delivery.status.uppercase() in setOf("GO_TO_SOURCE", "AT_SOURCE", "GO_TO_DESTINATION")
    }
}

fun isValidDeliveryRoute(source: Destination?, destination: Destination?): Boolean =
    source != null && destination != null && source.id != destination.id &&
        !source.map_id.isNullOrBlank() && source.map_id == destination.map_id &&
        !source.robot_id.isNullOrBlank() && source.robot_id == destination.robot_id
