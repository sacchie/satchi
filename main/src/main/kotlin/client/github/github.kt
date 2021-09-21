package main.client.github

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.OffsetDateTime

fun notifications(credential: Credential): List<GitHubNotification> {
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
    val responseTypeRef = object : TypeReference<List<GitHubNotification>>() {}
    return mapper.readValue(httpResponse.body(), responseTypeRef)
}

data class GitHubNotification(
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

data class Credential(val personalAccessToken: String)
