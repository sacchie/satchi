package main

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.javalin.Javalin
import main.notificationlist.*
import java.net.URL
import java.net.URLClassLoader
import java.time.OffsetDateTime


const val MAIN_URL = "http://localhost:8037"

data class ViewModel(val stateClass: String, val stateData: Any?) {
    data class ViewingData(val isMentionOnly: Boolean, val notifications: List<Notification>)

    companion object {
        fun fromState(notificationListState: main.notificationlist.State): ViewModel {
            val data: Any? = when (notificationListState) {
                is LoadingState -> null
                is ViewingState -> {
                    val ntfs = notificationListState.holders.map {
                        it.value.getUnread().map { n -> Notification.from(it.key, n) }
                    }.flatten()
                        .sortedBy { -it.timestamp.toEpochSecond() }
                    // sort from newest to oldest
                    ViewingData(false, ntfs)
                }
                else -> throw RuntimeException()
            }
            return ViewModel(notificationListState.javaClass.simpleName, data)
        }
    }

    data class Notification(
        val timestamp: OffsetDateTime,
        val source: Source,
        val title: String,
        val message: String,
        val mentioned: Boolean,
        val gatewayId: GatewayId,
        val id: String,
    ) {
        data class Source(
            val name: String,
            val url: String,
            val iconUrl: String?
        )

        companion object {
            fun from(gatewayId: GatewayId, n: main.Notification): Notification {
                return Notification(
                    n.timestamp,
                    Source(
                        n.source.name,
                        n.source.url,
                        when (n.source.icon) {
                            is main.Notification.Icon.Public -> n.source.icon.iconUrl
                            is main.Notification.Icon.Private -> "${MAIN_URL}/icon?gatewayId=${gatewayId}&iconId=${n.source.icon.iconId}"
                            null -> null
                            else -> throw RuntimeException()
                        }
                    ),
                    n.title,
                    n.message,
                    n.mentioned,
                    gatewayId,
                    n.id
                )
            }
        }
    }
}

data class Message(val op: OpType, val args: Map<String, Object>?) {
    enum class OpType {
        Notifications, MarkAsRead
    }
}

data class GatewayDefinition(
    val clientFactory: String,
    val args: Map<String, String>,
    val type: GatewayFactory,
    val jarPath: String?
)

private fun loadGateways(yamlFilename: String): Map<GatewayId, Gateway> {
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

fun main() {
    val gateways = loadGateways("gateways.yml")

    val mapper = jacksonObjectMapper() // APIレスポンスで使いまわしている
    mapper.registerModule(JavaTimeModule()) // timezoneを明示的に指定したほうがよさそう
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    var notificationListState: State? = null

    val lock = object {}

    Javalin.create().apply {
        ws("/view") { ws ->
            ws.onMessage { ctx ->
                fun sendViewModel() {
                    notificationListState!!.let {
                        ctx.send(mapper.writeValueAsString(ViewModel.fromState(it)))
                    }
                }

                fun updateState(stateUpdater: StateUpdater) {
                    synchronized(lock) {
                        val prevState = notificationListState?.let {
                            it::class.java
                        }
                        notificationListState = stateUpdater(notificationListState)
                        val nextState = notificationListState?.let {
                            it::class.java
                        }
                        System.err.println("${prevState} -> ${nextState}")
                    }
                    sendViewModel()
                }

                val msg = mapper.readValue(ctx.message(), Message::class.java)
                when (msg.op) {
                    Message.OpType.Notifications -> viewLatest(::updateState, gateways)
                    Message.OpType.MarkAsRead ->
                        markAsRead(
                            ::updateState,
                            msg.args!!["gatewayId"].toString(),
                            msg.args["notificationId"].toString(),
                            gateways
                        )
                }
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
}
