import main.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.*
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal class ServiceTest {
    private lateinit var sut: Service
    private lateinit var mockClient: MockClient
    private lateinit var savedKeywordStoreEntries: MutableList<FilterKeywordStore.Entry>
    private lateinit var sentViewModels: MutableList<ViewModel>
    private lateinit var sentDesktopNotifications: MutableList<Notification>

    data class FilterKeywordStoreEntryImpl(private val keyword: String) : FilterKeywordStore.Entry {
        override fun keyword(): String = keyword
        override fun selectedForDesktopNotification(): Boolean = false
    }

    @BeforeEach
    fun setup() {
        savedKeywordStoreEntries = mutableListOf()
        sentViewModels = mutableListOf()
        sentDesktopNotifications = mutableListOf()
        mockClient = MockClient()

        sut = Service(
            mapOf("0" to GatewayFactory.UNMANAGED.create(mockClient)),
            object : FilterKeywordStore {
                override fun load() = savedKeywordStoreEntries.toList()
                override fun appendIfNotExists(keyword: String) {
                    savedKeywordStoreEntries.add(FilterKeywordStoreEntryImpl(keyword))
                }
                override fun selectForDesktopNotification(keyword: String, selected: Boolean) {
                    TODO("Not yet implemented")
                }
            },
            sendUpdateView = sentViewModels::add,
            sendShowDesktopNotification = sentDesktopNotifications::addAll
        )
    }

    @Test
    fun `User can view latest notifications in the list`() {
        mockClient.notifications = listOf(
            Notification(
                OffsetDateTime.now(ZoneOffset.UTC),
                Notification.Source("source name", "https://example.com/source/url", null),
                "title",
                "Hello",
                true,
                "1",
            )
        )

        sut.initializeView()
        expectThat(sentViewModels.size).isEqualTo(2)
        expectThat(sentViewModels[0].stateClass).isEqualTo("LoadingState")
        expectThat(sentViewModels[1].stateClass).isEqualTo("ViewingState")
        expectThat(sentViewModels[1].stateData).isA<ViewModel.ViewingData>()

        val viewingData = sentViewModels[1].stateData as ViewModel.ViewingData
        expectThat(viewingData.notifications!!.size).isEqualTo(1)
        viewingData.notifications!!.forEach {
            expectThat(it.message).isEqualTo("Hello")
        }
    }

    @Test
    fun `User can view at most 30 notifications`() {
        mockClient.notifications = (1..31).map {
            Notification(
                OffsetDateTime.now(ZoneOffset.UTC),
                Notification.Source("source name", "https://example.com/source/url", null),
                "title",
                "Hello",
                false,
                it.toString(),
            )
        }

        sut.initializeView()
        (sentViewModels.last().stateData as ViewModel.ViewingData).let {
            expectThat(it.notifications!!.size).isEqualTo(30)
        }
    }

    @Test
    fun `User can remove a read notification from the list`() {
        mockClient.notifications = listOf(
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

        sut.initializeView()
        sut.markAsRead("0", "1")
        sut.viewIncomingNotifications()

        expectThat(sentViewModels.size).isEqualTo(3)
        expectThat(sentViewModels[2].stateClass).isEqualTo("ViewingState")
        (sentViewModels[2].stateData as ViewModel.ViewingData).let {
            expectThat(it.notifications!!.size).isEqualTo(1)
            expectThat(it.notifications!![0].id).isEqualTo("2")
        }
        expectThat(mockClient.readNotificationIds).containsExactly("1")
    }

    @Test
    fun `User can view mentioned notifications only in the list`() {
        mockClient.notifications = listOf(
            Notification(
                OffsetDateTime.now(ZoneOffset.UTC),
                Notification.Source("source name", "https://example.com/source/url", null),
                "title",
                "Hello",
                true,
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

        sut.initializeView()
        sut.toggleMentioned()
        expectThat(sentViewModels.size).isEqualTo(3)
        expectThat(sentViewModels[2].stateClass).isEqualTo("ViewingState")
        (sentViewModels[2].stateData as ViewModel.ViewingData).let {
            expectThat(it.isMentionOnly).isTrue()
            expectThat(it.notifications!!.size).isEqualTo(1)
            expectThat(it.notifications!![0].id).isEqualTo("1")
        }
    }

    @Test
    fun `User can view notifications only matching the keyword in the list`() {
        mockClient.notifications = listOf(
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

        sut.initializeView()
        expectThat(sentViewModels.size).isEqualTo(2)
        expectThat(sentViewModels[1].stateClass).isEqualTo("ViewingState")

        sut.changeFilterKeyword("World")
        expectThat(sentViewModels.size).isEqualTo(3)
        expectThat(sentViewModels[2].stateClass).isEqualTo("ViewingState")
        (sentViewModels[2].stateData as ViewModel.ViewingData).let {
            expectThat(it.notifications!!.size).isEqualTo(1)
            expectThat(it.notifications!![0].id).isEqualTo("2")
        }

        sut.changeFilterKeyword(" World\t")
        expectThat(sentViewModels.size).isEqualTo(3)

        sut.changeFilterKeyword(" ")
        expectThat(sentViewModels.size).isEqualTo(4)
        (sentViewModels[3].stateData as ViewModel.ViewingData).let {
            expectThat(it.notifications!!.size).isEqualTo(2)
        }
    }

    @Test
    fun `User can receive new mentioned notifications`() {
        sut.initializeView()

        mockClient.notifications = listOf(
            Notification(
                OffsetDateTime.now(ZoneOffset.UTC),
                Notification.Source("source name", "https://example.com/source/url", null),
                "title",
                "Hello",
                true,
                "1"
            ),
            Notification(
                OffsetDateTime.now(ZoneOffset.UTC),
                Notification.Source("source name", "https://example.com/source/url", null),
                "title",
                "World",
                false,
                "2"
            ),
            Notification(
                OffsetDateTime.now(ZoneOffset.UTC),
                Notification.Source("source name", "https://example.com/source/url", null),
                "title",
                "!!",
                true,
                "3"
            )
        )
        sut.sendLatestMentioned()
        expectThat(sentDesktopNotifications.size).isEqualTo(2)
        expectThat(sentDesktopNotifications.map { it.id }).containsExactly("1", "3")
    }

    @Test
    fun `User can notice that new notifications exist and then view the notifications`() {
        sut.initializeView()
        mockClient.notifications = listOf(
            Notification(
                OffsetDateTime.now(ZoneOffset.UTC),
                Notification.Source("source name", "https://example.com/source/url", null),
                "title",
                "Hello",
                false,
                "1"
            )
        )
        sentViewModels.clear()
        sut.fetchToPool()

        (sentViewModels.last().stateData as ViewModel.ViewingData).let {
            expectThat(it.incomingNotificationCount).isEqualTo(1)
        }

        sut.viewIncomingNotifications()

        (sentViewModels.last().stateData as ViewModel.ViewingData).let {
            expectThat(it.notifications!!.size).isEqualTo(1)
            expectThat(it.incomingNotificationCount).isEqualTo(0)
        }
    }

    @Test
    fun `User can view the 31st mentioned notification`() {
        mockClient.notifications = (1..30).map {
            Notification(
                OffsetDateTime.now(ZoneOffset.UTC),
                Notification.Source("source name", "https://example.com/source/url", null),
                "title",
                "Hello",
                false,
                it.toString()
            )
        }

        sut.initializeView()
        (sentViewModels.last().stateData as ViewModel.ViewingData).let {
            expectThat(it.notifications!!.size).isEqualTo(30)
        }

        sut.toggleMentioned()
        (sentViewModels.last().stateData as ViewModel.ViewingData).let {
            expectThat(it.notifications!!.size).isEqualTo(0)
        }

        mockClient.notificationsWithOffset = Pair(
            listOf(
                Notification(
                    OffsetDateTime.now(ZoneOffset.UTC),
                    Notification.Source("source name", "https://example.com/source/url", null),
                    "title",
                    "Hello",
                    true,
                    "31"
                )
            ),
            ""
        )
        sut.fetchBack()

        (sentViewModels.last().stateData as ViewModel.ViewingData).let {
            expectThat(it.notifications!!.size).isEqualTo(1)
        }
    }

    @Test
    fun `User can save keywords`() {
        sut.initializeView()
        expectThat(savedKeywordStoreEntries).isEmpty()

        sut.saveFilterKeyword("ほげ")
        expectThat(savedKeywordStoreEntries.map { it.keyword() }).containsExactly("ほげ")

        sut.saveFilterKeyword("ふが")
        expectThat(savedKeywordStoreEntries.map { it.keyword() }).containsExactly("ほげ", "ふが")
    }

    @Test
    fun `User can use saved keywords to filter notifications`() {
        savedKeywordStoreEntries.addAll(
            listOf(
                FilterKeywordStoreEntryImpl("ほげ"),
                FilterKeywordStoreEntryImpl("ふが")
            )
        )
        sut.initializeView()
        (sentViewModels.last().stateData as ViewModel.ViewingData).let {
            expectThat(it.savedKeywords!!.map { e -> e.keyword() }).containsExactly("ほげ", "ふが")
        }
    }
}

private class MockClient : Client {
    var notifications = listOf<Notification>()
    var notificationsWithOffset = Pair<List<Notification>, String>(listOf(), "")
    val readNotificationIds = mutableListOf<NotificationId>()
    override fun fetchNotifications(): List<Notification> = notifications
    override fun fetchNotificationsWithOffset(offset: String): Pair<List<Notification>, String> = notificationsWithOffset
    override fun markAsReadExecutor(): ((id: NotificationId) -> Unit) = { readNotificationIds.add(it) }
}
