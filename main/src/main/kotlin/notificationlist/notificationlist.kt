package main.notificationlist

import main.Client
import main.GatewayId
import main.Notification
import main.NotificationId
import java.io.InputStream

typealias StateUpdater = (currentState: State?) -> State

interface State

interface NotificationHolder {
    fun getUnread(): List<Notification>

    fun addToUnread(ntfs: List<Notification>): NotificationHolder

    fun read(id: NotificationId): NotificationHolder
}

data class LoadingState(val holders: Map<GatewayId, NotificationHolder>) : State
data class ViewingState(val holders: Map<GatewayId, NotificationHolder>) : State

abstract class Gateway(val client: Client) {
    abstract fun makeHolder(): NotificationHolder

    fun fetchNotifications(): List<Notification> = client.fetchNotifications()

    fun markAsRead(notificationId: NotificationId) {
        client.markAsReadExecutor()?.let { mark -> mark(notificationId) }
    }

    fun fetchIcon(iconId: String): InputStream? {
        return client.privateIconFetcher()?.let { fetch -> fetch(iconId) }
    }
}

fun viewLatest(updateState: (stateUpdater: StateUpdater) -> Unit, gateways: Map<GatewayId, Gateway>) {
    updateState { currentState ->
        when (currentState) {
            is LoadingState -> currentState
            is ViewingState -> LoadingState(currentState.holders)
            null -> LoadingState(gateways.map { it.key to it.value.makeHolder() }.toMap())
            else -> throw IllegalStateException()
        }
    }

    gateways.forEach{ (gatewayId, gateway) ->
        val fetched = gateway.fetchNotifications()

        updateState { currentState ->
            when (currentState) {
                is LoadingState -> ViewingState(
                    gateways.map {
                    if (it.key == gatewayId) {
                        Pair(it.key, it.value.makeHolder().addToUnread(fetched))
                    } else
                        Pair(it.key, it.value.makeHolder())
                    }.toMap())
                is ViewingState -> ViewingState(
                    currentState.holders.map {
                        if (it.key == gatewayId) {
                            Pair(it.key, it.value.addToUnread(fetched))
                        } else
                            Pair(it.key, it.value)
                    }.toMap()
                )
                null -> ViewingState(mapOf(Pair(gatewayId, gateway.makeHolder().addToUnread(fetched))))
                else -> throw IllegalStateException()
            }
        }
    }
}

fun viewLatestMentioned(updateState: (stateUpdater: StateUpdater) -> Unit, gateways: Map<GatewayId, Gateway>) {
    gateways.forEach{ (gatewayId, gateway) ->
        val fetched = gateway.fetchNotifications()

        updateState { currentState ->
            when (currentState) {
                is LoadingState -> ViewingState(
                    gateways.map {
                        if (it.key == gatewayId) {
                            Pair(it.key, it.value.makeHolder().addToUnread(fetched))
                        } else
                            Pair(it.key, it.value.makeHolder())
                    }.toMap())
                is ViewingState -> ViewingState(
                    currentState.holders.map {
                        if (it.key == gatewayId) {
                            Pair(it.key, it.value.addToUnread(fetched))
                        } else
                            Pair(it.key, it.value)
                    }.toMap()
                )
                null -> ViewingState(mapOf(Pair(gatewayId, gateway.makeHolder().addToUnread(fetched))))
                else -> throw IllegalStateException()
            }
        }
    }
}

fun markAsRead(
    updateState: (stateUpdater: StateUpdater) -> Unit,
    gatewayId: GatewayId,
    notificationId: NotificationId,
    gateways: Map<GatewayId, Gateway>
) {
    updateState { currentState ->
        when (currentState) {
            is LoadingState -> currentState
            is ViewingState -> ViewingState(
                currentState.holders.map {
                    if (it.key == gatewayId) {
                        Pair(it.key, it.value.read(notificationId))
                    } else
                        Pair(it.key, it.value)
                }.toMap()
            )
            else -> throw IllegalStateException()
        }
    }

    gateways[gatewayId]!!.markAsRead(notificationId)
}

// 未読既読管理してくれるClient用
private class ManagedGateway(
    client: Client
) : Gateway(client) {
    data class Holder(private val unread: List<Notification>) : NotificationHolder {
        override fun getUnread() = unread

        override fun addToUnread(ntfs: List<Notification>): NotificationHolder {
            return Holder((unread + ntfs).distinctBy(Notification::id))
        }

        override fun read(id: NotificationId): NotificationHolder {
            return Holder(unread.filter { it.id != id })
        }
    }

    override fun makeHolder(): Holder {
        return Holder(listOf())
    }
}

// 未読既読管理してくれないClient用
private class UnmanagedGateway (
    client: Client
) : Gateway(client) {
    data class Holder(
        private val all: List<Notification>,
        private val read: Set<NotificationId>
    ) : NotificationHolder {
        override fun getUnread(): List<Notification> {
            return all.filter { !read.contains(it.id) }
        }

        override fun addToUnread(ntfs: List<Notification>): NotificationHolder {
            return Holder((all + ntfs).distinctBy(Notification::id), read)
        }

        override fun read(id: NotificationId): NotificationHolder {
            return Holder(all, read + id)
        }
    }

    override fun makeHolder(): Holder {
        return Holder(listOf(), setOf())
    }
}

enum class GatewayFactory {
    UNMANAGED {
        override fun create(client: Client): Gateway {
            return UnmanagedGateway(client)
        }
    },

    MANAGED {
        override fun create(client: Client): Gateway {
            return ManagedGateway(client)
        }
    };

    abstract fun create(client: Client): Gateway
}
