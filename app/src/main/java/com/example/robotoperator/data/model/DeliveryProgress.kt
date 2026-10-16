package com.example.robotoperator.data.model

val deliveryProgressSteps = listOf(
    "GO_TO_DESTINATION",
    "ARRIVED",
    "DELIVERED",
    "RETURN_HOME",
    "HOME"
)

fun isDeliveryLifecycleComplete(delivery: Delivery): Boolean =
    delivery.status.equals("IDLE", ignoreCase = true) &&
        delivery.history.any {
            it.from.equals("HOME", ignoreCase = true) && it.to.equals("IDLE", ignoreCase = true)
        }

fun deliveryProgressIndex(delivery: Delivery): Int = when {
    isDeliveryLifecycleComplete(delivery) -> deliveryProgressSteps.lastIndex
    delivery.status.equals("IDLE", ignoreCase = true) -> -1
    else -> deliveryProgressSteps.indexOf(delivery.status.uppercase())
}
