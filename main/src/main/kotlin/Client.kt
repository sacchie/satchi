package main

import java.io.InputStream
import java.time.OffsetDateTime

interface Client {
    fun fetchNotifications(): List<Notification>
    fun privateIconFetcher(): ((iconId: String) -> InputStream)? = null
    fun markAsReadExecutor(): ((id: NotificationId) -> Unit)? = null
    fun fetchNotificationsWithOffset(offset: String):
        Pair<List<Notification>, String> = Pair(listOf(), "")
}

interface ClientFactory {
    fun createClient(args: Map<String, String>): Client
}

typealias NotificationId = String

/**
 * 通知の発生源(source)から自分に通知(notification)が届くというモデル
 * 現時点ではsourceは客体・名詞であるとする
 */
data class Notification(
    val timestamp: OffsetDateTime,
    val source: Source,
    val title: String,
    val message: String,
    val mentioned: Boolean,

    val id: NotificationId,
) {
    data class Source(
        val name: String,
        val url: String,
        val icon: Icon?,
    )

    interface Icon {
        // aタグに指定すれば表示できるアイコン
        data class Public(val iconUrl: String) : Icon

        // Clientにデータを問い合わせる必要があるアイコン
        data class Private(val iconId: String) : Icon
    }
}
