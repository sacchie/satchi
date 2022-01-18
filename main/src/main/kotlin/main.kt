package main

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.javalin.Javalin
import io.javalin.websocket.WsContext
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.*
import kotlin.streams.toList

const val MAIN_URL = "http://localhost:8037"

data class InMessage(val op: OpType, val args: Map<String, Object>?) {
    enum class OpType {
        Notifications, MarkAsRead, ToggleMentioned, ChangeFilterKeyword, SaveFilterKeyword,
        ViewIncomingNotifications
    }
}

data class OutMessage(val type: Type, val value: Any) {
    enum class Type {
        UpdateView, ShowDesktopNotification
    }
}

interface State
class NullState : State
class LoadingState : State
data class ViewingState(val gatewayStateSet: GatewayStateSet, val filterState: main.filter.State) : State

class FilterKeywordStore {
    fun load(): List<String> {
        return try {
            FileInputStream(KEYWORD_FILE_NAME).bufferedReader(CHARSET).lines().toList()
        } catch (e: FileNotFoundException) {
            listOf()
        }
    }

    fun append(keyword: String) {
        FileOutputStream(KEYWORD_FILE_NAME, true).bufferedWriter(CHARSET).use {
            it.appendLine(keyword)
        }
    }

    companion object {
        // TODO: This will not work on Windows
        private val KEYWORD_FILE_NAME = System.getProperty("user.home") + "/.keywords.txt"

        private val CHARSET = Charset.forName("UTF-8")
    }
}

class Service(
    private val gateways: Gateways,
    private val sendUpdateView: (viewModel: ViewModel) -> Unit,
    private val sendShowDesktopNotification: (notifications: List<Notification>) -> Unit
) {
    private var state: State = NullState()

    private val filterKeywordStore = FilterKeywordStore()

    private val desktopNotificationService = object : main.desktopnotification.Service {
        override fun send(notifications: List<Notification>) = sendShowDesktopNotification(notifications)
    }

    private fun doOnlyWhenViewingState(f: (viewingState: ViewingState) -> Unit) {
        when (state) {
            is ViewingState -> f(state as ViewingState)
        }
    }

    fun onChangeTriggeringViewUpdate() {
        val data: Any? = when (state) {
            is NullState -> return
            is LoadingState -> null
            is ViewingState -> {
                val viewingState = state as ViewingState
                val ntfs = viewingState.gatewayStateSet.getUnread(viewingState.filterState, 100)
                    .map { ViewModel.Notification.from(it.first, it.second) }
                ViewModel.ViewingData(
                    viewingState.filterState.isMentionOnly,
                    ntfs,
                    filterKeywordStore.load(),
                    viewingState.gatewayStateSet.getPoolCount()
                )
            }
            else -> throw RuntimeException()
        }

        val viewModel = ViewModel(state.javaClass.simpleName, data)
        sendUpdateView(viewModel)
    }

    fun viewLatest() {
        synchronized(state) {
            state = LoadingState()
            onChangeTriggeringViewUpdate()

            val gatewayStateSet = GatewayStateSet(
                gateways.map { (gatewayId, gateway) ->
                    val fetched = gateway.client.fetchNotifications()
                    gatewayId to GatewayState(gateway.isManaged, fetched)
                }.toMap()
            )

            state = ViewingState(gatewayStateSet, main.filter.State(false, ""))
        }
        onChangeTriggeringViewUpdate()
    }

    fun markAsRead(gatewayId: GatewayId, notificationId: NotificationId) =
        synchronized(state) {
            doOnlyWhenViewingState { state ->
                val gatewayState = state.gatewayStateSet.getState(gatewayId)
                main.notificationlist.markAsRead(
                    gatewayState.getAccessorForNotificationList(),
                    notificationId,
                    gateways[gatewayId]!!.client
                )
                onChangeTriggeringViewUpdate()
            }
        }

    fun toggleMentioned() {
        synchronized(state) {
            doOnlyWhenViewingState { state ->
                state.filterState.isMentionOnly = !state.filterState.isMentionOnly
                onChangeTriggeringViewUpdate()
            }
        }
    }

    fun fetchToPool() = gateways.forEach { (gatewayId, gateway) ->
        synchronized(state) {
            doOnlyWhenViewingState { state ->
                val gatewayState = state.gatewayStateSet.getState(gatewayId)
                main.notificationlist.fetchToPool(gatewayState.getAccessorForNotificationList(), gateway.client)
                System.err.println("Updating NotificationHolder")
                onChangeTriggeringViewUpdate()
            }
        }
    }

    fun changeFilterKeyword(keyword: String) {
        synchronized(state) {
            doOnlyWhenViewingState { state ->
                keyword.trim().let { newTrimmedKeyword ->
                    if (newTrimmedKeyword != state.filterState.keyword) {
                        state.filterState.keyword = newTrimmedKeyword
                        onChangeTriggeringViewUpdate()
                    }
                }
            }
        }
    }

    fun saveFilterKeyword(keyword: String) {
        synchronized(state) {
            doOnlyWhenViewingState { state ->
                keyword.trim().let { trimmedKeyword ->
                    if (trimmedKeyword.isNotEmpty() && trimmedKeyword !in filterKeywordStore.load()) {
                        filterKeywordStore.append(trimmedKeyword)
                        onChangeTriggeringViewUpdate()
                    }
                }
            }
        }
    }

    fun sendLatestMentioned() = gateways.forEach { (gatewayId, gateway) ->
        synchronized(state) {
            doOnlyWhenViewingState { state ->
                val gatewayState = state.gatewayStateSet.getState(gatewayId)
                desktopNotificationService.run({ update ->
                    gatewayState.idsDesktopNotificationSent = update(gatewayState.idsDesktopNotificationSent)
                }, gateway.client)
            }
        }
    }

    fun viewIncomingNotifications() =
        synchronized(state) {
            doOnlyWhenViewingState { state ->
                state.gatewayStateSet.flushPool()
                onChangeTriggeringViewUpdate()
            }
        }

    fun fetchBack() =
        gateways.forEach { (gatewayId, gateway) ->
            synchronized(state) {
                doOnlyWhenViewingState { state ->
                    val gatewayState = state.gatewayStateSet.getState(gatewayId)
                    val (ntfs, nextOffset) = gateway.client.fetchNotificationsWithOffset(gatewayState.timeMachineOffset)
                    gatewayState.apply {
                        addToUnread(ntfs)
                        timeMachineOffset = nextOffset
                        System.err.println("fetchBack(): #unreads=${getUnread().size}, gatewayId=$gatewayId")
                    }
                    onChangeTriggeringViewUpdate()
                }
            }
        }
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
                    InMessage.OpType.ChangeFilterKeyword -> service.changeFilterKeyword(msg.args!!["keyword"].toString())
                    InMessage.OpType.SaveFilterKeyword -> service.saveFilterKeyword(msg.args!!["keyword"].toString())
                    InMessage.OpType.ViewIncomingNotifications -> service.viewIncomingNotifications()
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
                    gw.fetchIcon(iconId)?.let {
                        ctx.result(it)
                        ctx.header("Cache-Control", "max-age=315360000")
                    }
                }
            }
        }
    }.start(8037) /* 37 = "サッチ" */

    run {
        var thread: Thread? = null
        kotlin.concurrent.timer(null, false, 5 * 1000, 10 * 1000) {
            System.err.println("timer fired")
            if (thread != null && thread!!.isAlive) {
                System.err.println("task skipped")
                return@timer
            }
            thread = kotlin.concurrent.thread {
                service.sendLatestMentioned()
                service.fetchToPool()
                service.fetchBack()
            }
            System.err.println("task executed")
        }
    }
}
