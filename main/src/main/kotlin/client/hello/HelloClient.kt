package main.client.hello

import main.Client
import main.ClientFactory
import main.Notification
import java.time.OffsetDateTime
import java.time.ZoneOffset

class HelloClient : Client {
    override fun fetchNotifications(): List<Notification> =
        List(5) { i ->
            Notification(
                OffsetDateTime.now(ZoneOffset.UTC),
                Notification.Source("source name", "https://example.com/source/url", null),
                "title",
                "Hello",
                false,
                i.toString()
            )
        }
}

class HelloClientFactory : ClientFactory {
    override fun createClient(args: Map<String, String>): Client {
        return HelloClient()
    }
}
