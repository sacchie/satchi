package main

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.javalin.Javalin
import io.javalin.websocket.WsContext
import main.desktopnotification.sendLatestMentioned
import main.filter.toggleMentioned
import main.notificationlist.markAsRead
import main.notificationlist.viewLatest
import java.net.URL
import java.net.URLClassLoader
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

const val MAIN_URL = "http://localhost:8037"

data class InMessage(val op: OpType, val args: Map<String, Object>?) {
    enum class OpType {
        Notifications, MarkAsRead, ToggleMentioned
    }
}

data class OutMessage(val type: Type, val value: Any) {
    enum class Type {
        UpdateView, ShowDesktopNotification
    }
}

data class GatewayDefinition(
    val clientFactory: String,
    val args: Map<String, String>,
    val type: main.notificationlist.GatewayFactory,
    val jarPath: String?
)

private fun loadGateways(yamlFilename: String): Map<GatewayId, main.notificationlist.Gateway> {
    val mapper = ObjectMapper(YAMLFactory())
    mapper.registerKotlinModule()
    val classLoader = object {}.javaClass.classLoader
    val gatewayDefinitions = classLoader.getResourceAsStream(yamlFilename).use {
        val responseTypeRef = object : TypeReference<List<GatewayDefinition>>() {}
        mapper.readValue(it, responseTypeRef)
    }
    val urls = gatewayDefinitions.mapNotNull { it.jarPath }.distinct().map { URL("jar:file://$it!/") }
        .toTypedArray()
    val extendedClassLoader = URLClassLoader(urls, classLoader)

    return gatewayDefinitions.mapIndexed { index, def ->
        val clientFactoryClass = extendedClassLoader.loadClass(def.clientFactory)
        when (val clientFactory = clientFactoryClass.getDeclaredConstructor().newInstance()) {
            is ClientFactory -> {
                val client = clientFactory.createClient(def.args)
                return@mapIndexed Pair(index.toString(), def.type.create(client))
            }
            else -> throw RuntimeException()
        }
    }.toMap()
}

typealias Gateways = Map<GatewayId, main.notificationlist.Gateway>

class Service(
    private val gateways: Gateways,
    private val sendUpdateView: (viewModel: ViewModel) -> Unit,
    private val sendShowDesktopNotification: (notifications: List<Notification>) -> Unit
) {
    private val state = State(
        main.notificationlist.NullState(),
        main.filter.State(false),
        main.desktopnotification.State(
            gateways.map {
                Pair(
                    it.key,
                    main.desktopnotification.SentNotificationHolder(listOf())
                )
            }.toMap()
        ),

        onChange = { notificationListState, filterState ->
            sendUpdateView(ViewModel.fromState(notificationListState, filterState))
        },

        onChangeDesktop = { newState, oldState ->
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val toSend = oldState.holders.flatMap {
                val gatewayId = it.key
                val after = newState.holders[gatewayId]!!
                val before = it.value
                after.fetched - before.fetched.toSet()
            }.filter { it.timestamp > now.minusMinutes(5) }
            sendShowDesktopNotification(toSend)
        }
    )

    fun viewLatest() = viewLatest(state::update, gateways)

    fun markAsRead(gatewayId: GatewayId, notificationId: NotificationId) =
        markAsRead(
            state::update,
            gatewayId,
            notificationId,
            gateways
        )

    fun toggleMentioned() = toggleMentioned(state::update)

    fun sendLatestMentioned() =
        sendLatestMentioned(state::update, gateways.map { Pair(it.key, it.value.client) }.toMap())
}

fun main() {
    val gateways = loadGateways("gateways.yml")

    val mapper = jacksonObjectMapper() // APIレスポンスで使いまわしている
    mapper.registerModule(JavaTimeModule()) // timezoneを明示的に指定したほうがよさそう
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    val webSocketContexts = Collections.synchronizedList(mutableListOf<WsContext>())

    val service = Service(
        gateways,
        sendUpdateView = { viewModel ->
            val outMessage = OutMessage(OutMessage.Type.UpdateView, viewModel)
            webSocketContexts.forEach { ctx ->
                ctx.send(mapper.writeValueAsString(outMessage))
            }
        },
        sendShowDesktopNotification = { notifications ->
            notifications.forEach { notification ->
                webSocketContexts.forEach { ctx ->
                    val outMessage = OutMessage(
                        OutMessage.Type.ShowDesktopNotification,
                        mapOf(
                            Pair("title", "${notification.source.name}: ${notification.title}"),
                            Pair("body", notification.message),
                            Pair("url", notification.source.url),
                        )
                    )
                    ctx.send(mapper.writeValueAsString(outMessage))
                }
            }
        }
    )

    Javalin.create().apply {
        ws("/connect") { ws ->
            ws.onConnect { ctx ->
                webSocketContexts.add(ctx)
                System.err.println("/view Opened (# of contexts=${webSocketContexts.size})")
            }
            ws.onMessage { ctx ->
                val msg = mapper.readValue(ctx.message(), InMessage::class.java)
                when (msg.op) {
                    InMessage.OpType.Notifications -> service.viewLatest()
                    InMessage.OpType.MarkAsRead ->
                        service.markAsRead(
                            msg.args!!["gatewayId"].toString(),
                            msg.args["notificationId"].toString(),
                        )
                    InMessage.OpType.ToggleMentioned ->
                        service.toggleMentioned()
                }
            }
            ws.onClose { ctx ->
                webSocketContexts.remove(ctx)
                System.err.println("/view Closed (# of contexts=${webSocketContexts.size})")
            }
        }

        get("/icon") { ctx ->
            run {
                val gatewayId = ctx.queryParam("gatewayId")!!
                val iconId = ctx.queryParam("iconId")!!
                gateways[gatewayId]?.let { gw ->
                    gw.fetchIcon(iconId)?.let { ctx.result(it) }
                }
            }
        }
    }.start(8037) /* 37 = "サッチ" */

    kotlin.concurrent.timer(null, false, 0, 15 * 1000) {
        System.err.println("timer fired")
        service.sendLatestMentioned()
    }
}
