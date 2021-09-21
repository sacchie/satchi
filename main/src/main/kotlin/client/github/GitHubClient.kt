package main.client.github

import main.Notification
import main.Client
import main.ClientFactory

class GitHubClient(private val credential: Credential) : Client {
    override fun fetchNotifications(): List<Notification> = notifications(credential).map {
        val sourceUrl = if (it.subject.url != null)
            it.subject.url.replace("api.client.github.com/repos", "client.github.com").replace("/pulls/", "/pull/")
        else it.repository.html_url

        Notification(
            it.updated_at,
            Notification.Source(
                it.repository.name,
                sourceUrl,
                Notification.Icon.Public(it.repository.owner.avatar_url)
            ),
            "GitHub notification",
            it.subject.title,
            it.reason in listOf("mention", "team_mention"),
            it.id
        )
    }
}

class GitHubClientFactory : ClientFactory {
    override fun createClient(args: Map<String, String>): Client {
        return GitHubClient(Credential(args.get("personalAccessToken")!!))
    }
}
