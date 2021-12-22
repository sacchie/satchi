package main.desktopnotification

import main.Client
import main.Notification
import main.NotificationId
import java.time.OffsetDateTime
import java.time.ZoneOffset

typealias Updater = (update: (cur: Set<NotificationId>) -> Set<NotificationId>) -> Unit

interface Service {
    fun run(update: Updater, client: Client) {
        update { curr ->
            val newlyFetched = client.fetchNotifications().filter { it.mentioned }
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val toSend = newlyFetched.filter { it.id !in curr }
                .filter { it.timestamp > now.minusMinutes(5) }
            send(toSend)
            curr + toSend.map { it.id }
        }
    }

    fun send(notifications: List<Notification>)
}
