package main.notificationlist

import main.*

interface NotificationHolderAccessor {
    fun addToPooled(added: List<Notification>)
    fun read(notificationId: NotificationId)
}

fun fetchToPool(accessor: NotificationHolderAccessor, client: Client) {
    val fetched = client.fetchNotifications()
    accessor.addToPooled(fetched)
}

fun markAsRead(accessor: NotificationHolderAccessor, notificationId: NotificationId, client: Client) {
    accessor.read(notificationId)
    client.markAsReadExecutor()?.let { mark -> mark(notificationId) }
}
