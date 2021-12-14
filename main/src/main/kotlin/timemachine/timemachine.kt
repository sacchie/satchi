package main.timemachine

import main.Client
import main.GatewayId
import main.Notification

data class Output(val gatewayId: GatewayId, val ntfs: List<Notification>)

data class State(val offsets: Map<GatewayId, String>)

typealias StateUpdater = (currentState: State) -> Pair<State, Output?>

interface Service {
    fun updateState(stateUpdater: StateUpdater)

    fun getGatewayClients(): Map<GatewayId, Client>

    fun fetchBack() {
        getGatewayClients().forEach { (gatewayId, client) ->
            updateState { currentState ->
                val offset = currentState.offsets[gatewayId]!!
                val (newlyFetched, nextOffset) = client.fetchNotificationsWithOffset(offset)

                if (offset == nextOffset) {
                    Pair(currentState, null)
                } else {
                    val nextState = State(
                        currentState.offsets.map {
                            if (it.key == gatewayId) {
                                Pair(it.key, nextOffset)
                            } else {
                                Pair(it.key, it.value)
                            }
                        }.toMap()
                    )
                    val output = if (newlyFetched.isNotEmpty()) {
                        Output(gatewayId, newlyFetched)
                    } else {
                        null
                    }
                    Pair(nextState, output)
                }
            }
        }
    }
}
