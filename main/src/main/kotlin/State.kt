package main

typealias StateUpdater<S> = (currentState: S) -> S

class State(
    var notificationList: main.notificationlist.State,
    var filter: main.filter.State,
    var desktopNotification: main.desktopnotification.State,
    val onChange: (
        notificationListState: main.notificationlist.State,
        filterState: main.filter.State,
    ) -> Unit,
    val onChangeDesktop: ( // todo: rename
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
        onChange(notificationList, filter)
    }

    @Synchronized
    @JvmName("updateFilter")
    fun update(stateUpdater: StateUpdater<main.filter.State>) {
        filter = stateUpdater(filter)
        onChange(notificationList, filter)
    }

    @Synchronized
    @JvmName("updateDesktopNotification")
    fun update(stateUpdater: StateUpdater<main.desktopnotification.State>) {
        val newState = stateUpdater(desktopNotification)
        onChangeDesktop(newState, desktopNotification)
        desktopNotification = newState
    }
}
