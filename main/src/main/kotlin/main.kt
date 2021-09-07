package main

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.javalin.Javalin
import java.time.OffsetDateTime

fun main() {
    val app = Javalin.create().start(8037) /* 37 = "サッチ" */
    val credential = main.github.Credential.load("./.credential")

    val mapper = jacksonObjectMapper()
    mapper.registerModule(JavaTimeModule())
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    app.get("/notifications") { ctx ->
        run {
            val notifications = main.github.notifications(credential).map {
                Notification(
                    it.updated_at,
                    Source(it.repository.name, it.repository.html_url, it.repository.owner.avatar_url),
                    "GitHub notification",
                    "${it.subject.title} ${it.subject.url.orEmpty()}",
                    it.reason in listOf("mention", "team_mention")
                )
            }
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
