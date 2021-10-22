package main

import java.time.OffsetDateTime
import java.time.ZoneOffset

typealias StateUpdater<S> = (currentState: S) -> S

class State(
    var notificationList: main.notificationlist.State,
    var filter: main.filter.State,
    var desktopNotification: main.desktopnotification.State,
    val sendViewModel: (
        notificationListState: main.notificationlist.State,
        filterState: main.filter.State,
    ) -> Unit,
    val sendDesktopNotification: (
        notifications: List<Notification>
    ) -> Unit,
) {
    @Synchronized
    @JvmName("updateNotificationList")
    fun update(stateUpdater: StateUpdater<main.notificationlist.State>) {
        val prevState = notificationList::class.java
        notificationList = stateUpdater(notificationList)
        val nextState = notificationList::class.java
        System.err.println("$prevState -> $nextState")
        sendViewModel(notificationList, filter)
    }

    @Synchronized
    @JvmName("updateFilter")
    fun update(stateUpdater: StateUpdater<main.filter.State>) {
        filter = stateUpdater(filter)
        sendViewModel(notificationList, filter)
    }

    @Synchronized
    @JvmName("updateDesktopNotification")
    fun update(stateUpdater: StateUpdater<main.desktopnotification.State>) {
        val newState = stateUpdater(desktopNotification)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val toSend = desktopNotification.holders.flatMap {
            val gatewayId = it.key
            val after = newState.holders[gatewayId]!!
            val before = it.value
            after.fetched - before.fetched
        }.filter { it.timestamp > now.minusMinutes(5) }
        sendDesktopNotification(toSend)

        desktopNotification = newState
    }
}
