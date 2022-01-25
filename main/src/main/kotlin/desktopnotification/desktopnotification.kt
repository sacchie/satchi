package main.desktopnotification

import main.Client
import main.Notification
import main.NotificationId
import main.keywordmatch.matchKeyword
import java.time.OffsetDateTime
import java.time.ZoneOffset

typealias Updater = (update: (cur: Set<NotificationId>) -> Set<NotificationId>) -> Unit

interface Service {
    fun run(update: Updater, client: Client, keywords: List<String>) {
        update { curr ->
            val newlyFetched = client.fetchNotifications().filter { it.mentioned || keywords.any { keyword -> matchKeyword(it, keyword) } }
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val toSend = newlyFetched.filter { it.id !in curr }
                .filter { it.timestamp > now.minusMinutes(5) }
            send(toSend)
            curr + toSend.map { it.id }
        }
    }

    fun send(notifications: List<Notification>)
}
