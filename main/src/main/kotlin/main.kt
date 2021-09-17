package main

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import io.javalin.Javalin
import java.net.URL
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

fun main() {
    val app = Javalin.create().start(8037) /* 37 = "サッチ" */
    val credential = main.github.Credential.load("./.credential")

    val mapper = jacksonObjectMapper()
    // timezoneを明示的に指定したほうがよさそう
    mapper.registerModule(JavaTimeModule())
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    app.get("/notifications") { ctx ->
        run {
            val githubNotifications = main.github.notifications(credential).map {
                val sourceUrl = if (it.subject.url != null)
                    it.subject.url.replace("api.github.com/repos", "github.com").replace("/pulls/", "/pull/")
                else it.repository.html_url
                Notification(
                    it.updated_at,
                    Source(it.repository.name, sourceUrl, it.repository.owner.avatar_url),
                    "GitHub notification",
                    it.subject.title,
                    it.reason in listOf("mention", "team_mention")
                )
            }

            val syndFeed = SyndFeedInput().build(XmlReader(URL("https://news.yahoo.co.jp/rss/topics/top-picks.xml")))
            val yahooNewsNotifications = syndFeed.entries.map {
                Notification(
                    OffsetDateTime.ofInstant(it.publishedDate.toInstant(), ZoneOffset.UTC),
                    Source(syndFeed.title, it.link, null),
                    it.title,
                    it.description.value,
                    false)
            }

            val notifications = (githubNotifications + yahooNewsNotifications).sortedBy { it.timestamp }.reversed()
            ctx.result(mapper.writeValueAsString(notifications))
        }
    }
}

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
)

data class Source(
    val name: String,
    val url: String,
    val iconUrl: String?
)
