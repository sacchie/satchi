package main.notificationlist

import main.Client
import main.GatewayId
import main.Notification
import main.NotificationId
import java.io.InputStream

typealias StateUpdater = (currentState: State) -> State

interface State

interface NotificationHolder {
    fun getUnread(): List<Notification>

    val pooledCount: Int

    fun addToUnread(added: List<Notification>): NotificationHolder

    fun addToPooled(added: List<Notification>): NotificationHolder

    fun read(id: NotificationId): NotificationHolder

    fun flushPool(): NotificationHolder
}

class NullState : State
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

interface Service {
    fun updateState(stateUpdater: StateUpdater)

    fun getGateways(): Map<GatewayId, Gateway>

    fun viewLatest() {
        updateState { currentState ->
            when (currentState) {
                is NullState -> LoadingState(getGateways().map { it.key to it.value.makeHolder() }.toMap())
                is LoadingState -> currentState
                is ViewingState -> LoadingState(currentState.holders)
                else -> throw IllegalStateException()
            }
        }

        getGateways().forEach { (gatewayId, gateway) ->
            val fetched = gateway.fetchNotifications()

            updateState { currentState ->
                when (currentState) {
                    is NullState -> ViewingState(mapOf(Pair(gatewayId, gateway.makeHolder().addToUnread(fetched))))
                    is LoadingState -> ViewingState(
                        getGateways().map {
                            if (it.key == gatewayId) {
                                Pair(it.key, it.value.makeHolder().addToUnread(fetched))
                            } else
                                Pair(it.key, it.value.makeHolder())
                        }.toMap()
                    )
                    is ViewingState -> ViewingState(
                        currentState.holders.map {
                            if (it.key == gatewayId) {
                                Pair(it.key, it.value.addToUnread(fetched))
                            } else
                                Pair(it.key, it.value)
                        }.toMap()
                    )
                    else -> throw IllegalStateException()
                }
            }
        }
    }

    fun fetchToPool() {
        getGateways().forEach { (gatewayId, gateway) ->
            val fetched = gateway.fetchNotifications()
            updateState { currentState ->
                when (currentState) {
                    is ViewingState -> ViewingState(
                        currentState.holders.map {
                            if (it.key == gatewayId) {
                                Pair(it.key, it.value.addToPooled(fetched))
                            } else
                                Pair(it.key, it.value)
                        }.toMap()
                    )
                    else -> currentState
                }
            }
        }
    }

    fun viewIncomingNotifications() {
        updateState { currentState ->
            when (currentState) {
                is ViewingState -> ViewingState(
                    currentState.holders.map {
                        it.key to it.value.flushPool()
                    }.toMap()
                )
                else -> currentState
            }
        }
    }

    fun markAsRead(gatewayId: GatewayId, notificationId: NotificationId) {
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

        getGateways()[gatewayId]!!.markAsRead(notificationId)
    }

    fun onUnreadFetched(gatewayId: GatewayId, ntfs: List<Notification>) {
        updateState { currentState ->
            when (currentState) {
                is ViewingState -> ViewingState(
                    currentState.holders.map {
                        it.key to it.value.addToUnread(ntfs)
                    }.toMap()
                )
                else -> currentState
            }
        }
    }
}

// 未読既読管理してくれるClient用
private class ManagedGateway(
    client: Client
) : Gateway(client) {
    data class Holder(
        private val unread: List<Notification>,
        private val pooled: List<Notification>
    ) : NotificationHolder {
        override fun getUnread() = unread

        override val pooledCount: Int
            get() = pooled.size

        override fun addToUnread(added: List<Notification>): NotificationHolder {
            val addedIds = added.map(Notification::id).distinct().toSet()
            return Holder((unread + added).distinctBy(Notification::id), pooled.filter { it.id !in addedIds })
        }

        override fun addToPooled(added: List<Notification>): NotificationHolder {
            val unreadIds = unread.map(Notification::id).distinct().toSet()
            return Holder(unread, (pooled + added.filter { it.id !in unreadIds }).distinctBy(Notification::id))
        }

        override fun read(id: NotificationId): NotificationHolder {
            return Holder(unread.filter { it.id != id }, pooled.filter { it.id != id })
        }

        override fun flushPool(): NotificationHolder {
            return Holder((unread + pooled).distinctBy(Notification::id), listOf())
        }
    }

    override fun makeHolder(): Holder {
        return Holder(listOf(), listOf())
    }
}

// 未読既読管理してくれないClient用
private class UnmanagedGateway(
    client: Client
) : Gateway(client) {
    data class Holder(
        private val all: List<Notification>,
        private val unreadIds: Set<NotificationId>,
        private val pooledIds: Set<NotificationId>
    ) : NotificationHolder {
        override fun getUnread(): List<Notification> {
            return all.filter { it.id in unreadIds }
        }

        override val pooledCount: Int
            get() = pooledIds.size

        override fun addToUnread(added: List<Notification>): NotificationHolder {
            val addedIds = added.map(Notification::id).distinct().toSet()
            return Holder((all + added).distinctBy(Notification::id), unreadIds + addedIds, pooledIds - addedIds)
        }

        override fun addToPooled(added: List<Notification>): NotificationHolder {
            val addedIds = added.map(Notification::id).distinct().toSet()
            return Holder((all + added).distinctBy(Notification::id), unreadIds, pooledIds + addedIds - unreadIds)
        }

        override fun read(id: NotificationId): NotificationHolder {
            return Holder(all, unreadIds - id, pooledIds - id)
        }

        override fun flushPool(): NotificationHolder {
            return Holder(all, unreadIds + pooledIds, setOf())
        }
    }

    override fun makeHolder(): Holder {
        return Holder(listOf(), setOf(), setOf())
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
