package main.notificationpool

import main.GatewayId
import main.Notification
import main.notificationlist.Gateway

data class State(val notifications: Map<GatewayId, List<Notification>>) // TODO init

fun fetchToPool(updateState: ((currentState: State) -> State) -> Unit, gateways: Map<GatewayId, Gateway>) {
    gateways.forEach { (gatewayId, gateway) ->
        val fetched = gateway.fetchNotifications()

        updateState { currentState ->
            State(
                currentState.notifications.map {
                    if (it.key == gatewayId) {
                        Pair(it.key, it.value + fetched)
                    } else
                        Pair(it.key, it.value)
                }.toMap()
            )
        }
    }
}
