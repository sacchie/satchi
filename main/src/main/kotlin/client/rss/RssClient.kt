package main.client.rss

import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import main.Notification
import main.Client
import main.ClientFactory
import java.net.URL
import java.time.OffsetDateTime
import java.time.ZoneOffset

class RssClient(private val feedUrl: String) : Client {
    override fun fetchNotifications(): List<Notification> {
        val syndFeed = SyndFeedInput().build(XmlReader(URL(feedUrl)))

        fun entryToNotification(entry: SyndEntry): Notification {
            val instant = if (entry.publishedDate != null) {
                entry.publishedDate.toInstant()
            } else {
                entry.updatedDate.toInstant()
            }

            return Notification(
                OffsetDateTime.ofInstant(instant, ZoneOffset.UTC),
                Notification.Source(syndFeed.title, entry.link, null),
                entry.title,
                entry.description?.value?:"",
                false,
                entry.uri
            )
        }

        return syndFeed.entries.map(::entryToNotification)
    }
}

class RssClientFactory : ClientFactory {
    override fun createClient(args: Map<String, String>): Client {
        return RssClient(args.get("feedUrl")!!)
    }
}