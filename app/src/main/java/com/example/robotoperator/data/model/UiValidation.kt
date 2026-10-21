package com.example.robotoperator.data.model

/** UI validation only. The server remains responsible for map and access checks. */
fun finiteCoordinate(value: String): Double? = value.trim().toDoubleOrNull()?.takeIf { it.isFinite() }

fun isDeliveryActionAvailable(delivery: Delivery, action: DeliveryAction): Boolean {
    if (isDeliveryLifecycleComplete(delivery)) return false
    return when (action) {
        DeliveryAction.DISPATCH -> delivery.status.equals("IDLE", ignoreCase = true)
        DeliveryAction.CONFIRM_PICKUP -> delivery.status.equals("ARRIVED", ignoreCase = true)
        DeliveryAction.CANCEL -> delivery.status.uppercase() in setOf("GO_TO_DESTINATION", "RETURN_HOME")
    }
}
