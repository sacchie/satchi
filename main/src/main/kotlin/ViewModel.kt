package main

import java.time.OffsetDateTime

data class ViewModel(val stateClass: String, val stateData: Any?) {
    data class ViewingData(val isMentionOnly: Boolean, val notifications: List<Notification>)

    companion object {
        fun fromState(notificationListState: main.notificationlist.State, filterState: main.filter.State): ViewModel {
            val data: Any? = when (notificationListState) {
                is main.notificationlist.LoadingState -> null
                is main.notificationlist.ViewingState -> {
                    val ntfs = notificationListState.holders.flatMap {
                        it.value.getUnread().map { n -> Notification.from(it.key, n) }
                    }
                        .filter { if (filterState.isMentionOnly) it.mentioned else true }
                        .sortedBy { -it.timestamp.toEpochSecond() }
                    // sort from newest to oldest
                    ViewingData(filterState.isMentionOnly, ntfs)
                }
                else -> throw RuntimeException()
            }
            return ViewModel(notificationListState.javaClass.simpleName, data)
        }
    }

    data class Notification(
        val timestamp: OffsetDateTime,
        val source: Source,
        val title: String,
        val message: String,
        val mentioned: Boolean,
        val gatewayId: GatewayId,
        val id: String,
    ) {
        data class Source(
            val name: String,
            val url: String,
            val iconUrl: String?
        )

        companion object {
            fun from(gatewayId: GatewayId, n: main.Notification): Notification {
                return Notification(
                    n.timestamp,
                    Source(
                        n.source.name,
                        n.source.url,
                        when (n.source.icon) {
                            is main.Notification.Icon.Public -> n.source.icon.iconUrl
                            is main.Notification.Icon.Private -> "$MAIN_URL/icon?gatewayId=$gatewayId&iconId=${n.source.icon.iconId}"
                            null -> null
                            else -> throw RuntimeException()
                        }
                    ),
                    n.title,
                    n.message,
                    n.mentioned,
                    gatewayId,
                    n.id
                )
            }
        }
    }
}