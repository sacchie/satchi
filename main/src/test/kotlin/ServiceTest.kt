import main.Notification
import main.Service
import main.ViewModel
import main.client.hello.HelloClient
import main.notificationlist.GatewayFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal class ServiceTest {
    private lateinit var sut: Service
    private lateinit var helloClient: HelloClient
    private lateinit var sentViewModels: MutableList<ViewModel>
    private lateinit var sentDesktopNotifications: MutableList<Notification>

    @BeforeEach
    fun setup() {
        sentViewModels = mutableListOf()
        sentDesktopNotifications = mutableListOf()
        helloClient = HelloClient()
        sut = Service(
            mapOf("0" to GatewayFactory.UNMANAGED.create(helloClient)),
            sendUpdateView = sentViewModels::add,
            sendShowDesktopNotification = sentDesktopNotifications::addAll
        )
    }

    @Test
    fun `User can view latest notifications in the list`() {
        sut.viewLatest()
        expectThat(sentViewModels.size).isEqualTo(2)
        expectThat(sentViewModels[0].stateClass).isEqualTo("LoadingState")
        expectThat(sentViewModels[1].stateClass).isEqualTo("ViewingState")
        expectThat(sentViewModels[1].stateData).isA<ViewModel.ViewingData>()

        val viewingData = sentViewModels[1].stateData as ViewModel.ViewingData
        expectThat(viewingData.notifications.size).isEqualTo(5)
        viewingData.notifications.forEach {
            expectThat(it.message).isEqualTo("Hello")
        }
    }

    @Test
    fun `User can remove a read notification from the list`() {
        sut.viewLatest()
        sut.markAsRead("0", "0")
        expectThat(sentViewModels.size).isEqualTo(3)
        expectThat(sentViewModels[2].stateClass).isEqualTo("ViewingState")
        (sentViewModels[2].stateData as ViewModel.ViewingData).let {
            expectThat(it.notifications.size).isEqualTo(4)
            expectThat(it.notifications[0].id).isEqualTo("1")
        }
    }

    @Test
    fun `User can view mentioned notifications only in the list`() {
        sut.viewLatest()
        sut.toggleMentioned()
        expectThat(sentViewModels.size).isEqualTo(3)
        expectThat(sentViewModels[2].stateClass).isEqualTo("ViewingState")
        (sentViewModels[2].stateData as ViewModel.ViewingData).let {
            expectThat(it.isMentionOnly).isTrue()
            expectThat(it.notifications.size).isEqualTo(1)
            expectThat(it.notifications[0].id).isEqualTo("0")
        }
    }

    @Test
    fun `User can view notifications only matching the keyword in the list`() {
        helloClient.notifications = listOf(
            Notification(
                OffsetDateTime.now(ZoneOffset.UTC),
                Notification.Source("source name", "https://example.com/source/url", null),
                "title",
                "Hello",
                false,
                "1"
            ),
            Notification(
                OffsetDateTime.now(ZoneOffset.UTC),
                Notification.Source("source name", "https://example.com/source/url", null),
                "title",
                "World",
                false,
                "2"
            )
        )

        sut.viewLatest()
        expectThat(sentViewModels.size).isEqualTo(2)
        expectThat(sentViewModels[1].stateClass).isEqualTo("ViewingState")

        sut.changeFilterKeyword("World")
        expectThat(sentViewModels.size).isEqualTo(3)
        expectThat(sentViewModels[2].stateClass).isEqualTo("ViewingState")
        (sentViewModels[2].stateData as ViewModel.ViewingData).let {
            expectThat(it.notifications.size).isEqualTo(1)
            expectThat(it.notifications[0].id).isEqualTo("2")
        }

        sut.changeFilterKeyword(" World\t")
        expectThat(sentViewModels.size).isEqualTo(3)

        sut.changeFilterKeyword(" ")
        expectThat(sentViewModels.size).isEqualTo(4)
        (sentViewModels[3].stateData as ViewModel.ViewingData).let {
            expectThat(it.notifications.size).isEqualTo(2)
        }
    }

    @Test
    fun `Use can receive new mentioned notifications`() {
        sut.sendLatestMentioned()
        expectThat(sentDesktopNotifications.size).isEqualTo(1)
        expectThat(sentDesktopNotifications[0].id).isEqualTo("0")
    }
}
