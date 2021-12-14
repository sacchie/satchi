package main.desktopnotification

import main.Client
import main.GatewayId
import main.Notification

data class State(val holders: Map<GatewayId, SentNotificationHolder>)

typealias StateUpdater = (currentState: State) -> State

class SentNotificationHolder(val fetched: List<Notification>) {
    fun addToFetched(newlyFetched: List<Notification>): SentNotificationHolder = SentNotificationHolder((fetched + newlyFetched).distinctBy { it.id })
}

interface Service {
    fun updateState(stateUpdater: StateUpdater)

    fun getGatewayClients(): Map<GatewayId, Client>

    fun sendLatestMentioned() {
        getGatewayClients().forEach { (gatewayId, client) ->
            val newlyFetched = client.fetchNotifications().filter { it.mentioned }
            updateState { currentState ->
                State(
                    currentState.holders.map {
                        if (it.key == gatewayId) {
                            Pair(it.key, it.value.addToFetched(newlyFetched))
                        } else {
                            Pair(it.key, it.value)
                        }
                    }.toMap()
                )
            }
        }
    }
}
