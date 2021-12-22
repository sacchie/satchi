package main.notificationlist

import main.*

typealias NotificationHolderUpdater = (update: (cur: NotificationHolder) -> NotificationHolder) -> Unit

fun fetchToPool(update: NotificationHolderUpdater, client: Client) {
    val fetched = client.fetchNotifications()
    update { it.addToPooled(fetched) }
}

fun markAsRead(update: NotificationHolderUpdater, notificationId: NotificationId, client: Client) {
    update { cur -> cur.read(notificationId) }
    client.markAsReadExecutor()?.let { mark -> mark(notificationId) }
}
