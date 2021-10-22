package main.desktopnotification

import main.Client
import main.GatewayId
import main.Notification

typealias StateUpdater = (currentState: State) -> State

class SentNotificationHolder(val fetched: List<Notification>) {
    fun addToFetched(newlyFetched: List<Notification>): SentNotificationHolder = SentNotificationHolder((fetched + newlyFetched).distinctBy{it.id})
}

data class State(val holders: Map<GatewayId, SentNotificationHolder>)

fun sendLatestMentioned(updateState: (stateUpdater: StateUpdater) -> Unit, gatewayClients: Map<GatewayId, Client>) {
    gatewayClients.forEach { (gatewayId, client) ->
        val newlyFetched = client.fetchNotifications().filter { it.mentioned }
        updateState { currentState ->
            State(currentState.holders.map {
                if (it.key == gatewayId) {
                    Pair(it.key, it.value.addToFetched(newlyFetched))
                } else {
                    Pair(it.key, it.value)
                }
            }.toMap())
        }
    }
}
