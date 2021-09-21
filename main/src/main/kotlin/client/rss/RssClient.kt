package main.client.rss

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
        return syndFeed.entries.map {
            Notification(
                OffsetDateTime.ofInstant(it.publishedDate.toInstant(), ZoneOffset.UTC),
                Notification.Source(syndFeed.title, it.link, null),
                it.title,
                it.description?.value?:"",
                false,
                it.uri
            )
        }
    }
}

class RssClientFactory : ClientFactory {
    override fun createClient(args: Map<String, String>): Client {
        return RssClient(args.get("feedUrl")!!)
    }
}