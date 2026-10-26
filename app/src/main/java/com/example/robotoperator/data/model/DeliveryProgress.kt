package com.example.robotoperator.data.model

val deliveryProgressSteps = listOf(
    "GO_TO_SOURCE",
    "AT_SOURCE",
    "GO_TO_DESTINATION",
    "ARRIVED"
)

fun isDeliveryLifecycleComplete(delivery: Delivery): Boolean =
    delivery.status.equals("DELIVERED", ignoreCase = true) ||
        (delivery.status.equals("IDLE", ignoreCase = true) &&
            delivery.history.lastOrNull()?.let {
                it.from.equals("HOME", ignoreCase = true) &&
                    it.to.equals("IDLE", ignoreCase = true)
            } == true)

fun deliveryProgressIndex(delivery: Delivery): Int = when {
    isDeliveryLifecycleComplete(delivery) -> deliveryProgressSteps.lastIndex
    delivery.status.equals("IDLE", ignoreCase = true) -> -1
    delivery.status.equals("HOME", ignoreCase = true) ||
        delivery.status.equals("ARRIVED", ignoreCase = true) -> deliveryProgressSteps.lastIndex
    else -> deliveryProgressSteps.indexOf(delivery.status.uppercase())
}
