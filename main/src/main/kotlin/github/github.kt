package main.github

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.OffsetDateTime

fun notifications(credential: Credential): List<Notification> {
    val mapper = jacksonObjectMapper()
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    mapper.registerModule(JavaTimeModule())

    val httpRequest: HttpRequest = HttpRequest.newBuilder()
        .uri(URI.create("https://api.github.com/notifications"))
        .GET()
        .header("authorization", "token ${credential.personalAccessToken}")
        .build()
    val client = HttpClient.newHttpClient()
    val httpResponse = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
    val responseTypeRef = object : TypeReference<List<Notification>>() {}
    return mapper.readValue(httpResponse.body(), responseTypeRef)
}

data class Notification(
    val id: String,
    val reason: String,
    val updated_at: OffsetDateTime,
    val subject: Subject,
    val repository: Repository,
) {
    data class Subject(
        val title: String,
        val url: String?
    )

    data class Repository(val name: String, val html_url: String, val owner: Owner) {
        data class Owner(val avatar_url: String)
    }

}

data class Credential(val personalAccessToken: String) {
    companion object {
        fun load(path: String): Credential {
            val mapper = jacksonObjectMapper()
            javaClass.classLoader.getResourceAsStream(path).use {
                return mapper.readValue(it, Credential::class.java)
            }
        }
    }
}
