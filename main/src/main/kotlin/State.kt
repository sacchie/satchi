package main

typealias StateUpdater<S> = (currentState: S) -> S

class State(
    var notificationList: main.notificationlist.State,
    var filter: main.filter.State,
    var desktopNotification: main.desktopnotification.State,
    val onChangeTriggeringViewUpdate: (
        notificationListState: main.notificationlist.State,
        filterState: main.filter.State,
    ) -> Unit,
    val onChangeTriggeringDesktopNotification: (
        main.desktopnotification.State,
        main.desktopnotification.State
    ) -> Unit,
) {
    @Synchronized
    @JvmName("updateNotificationList")
    fun update(stateUpdater: StateUpdater<main.notificationlist.State>) {
        val prevState = notificationList::class.java
        notificationList = stateUpdater(notificationList)
        val nextState = notificationList::class.java
        System.err.println("$prevState -> $nextState")
        onChangeTriggeringViewUpdate(notificationList, filter)
    }

    @Synchronized
    @JvmName("updateFilter")
    fun update(stateUpdater: StateUpdater<main.filter.State>) {
        filter = stateUpdater(filter)
        onChangeTriggeringViewUpdate(notificationList, filter)
    }

    @Synchronized
    @JvmName("updateDesktopNotification")
    fun update(stateUpdater: StateUpdater<main.desktopnotification.State>) {
        val newState = stateUpdater(desktopNotification)
        onChangeTriggeringDesktopNotification(newState, desktopNotification)
        desktopNotification = newState
    }
}
