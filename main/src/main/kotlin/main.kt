package main

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.javalin.Javalin
import io.javalin.websocket.WsContext
import java.util.*

const val MAIN_URL = "http://localhost:8037"

data class InMessage(val op: OpType, val args: Map<String, Object>?) {
    enum class OpType {
        Notifications, MarkAsRead, ToggleMentioned, ChangeFilterKeyword,
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
class ViewingState(gatewayStateMap: Map<GatewayId, GatewayState>, val filterState: main.filter.State) : State {
    private val gatewayStateSet: GatewayStateSet = GatewayStateSet(gatewayStateMap)

    fun getUnread(limit: Int) = gatewayStateSet.getUnread(filterState, limit)
    val poolCount
        get() = gatewayStateSet.poolCount
    fun getAccessorForNotificationList(gatewayId: GatewayId) = gatewayStateSet.getState(gatewayId).getAccessorForNotificationList()
    fun getGatewayState(gatewayId: GatewayId) = gatewayStateSet.getState(gatewayId)
    fun flushPool() = gatewayStateSet.flushPool()
}

class Service(
    private val gateways: Gateways,
    private val sendUpdateView: (viewModel: ViewModel) -> Unit,
    private val sendShowDesktopNotification: (notifications: List<Notification>) -> Unit
) {
    private var state: State = NullState()

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
                val ntfs = viewingState.getUnread(100)
                    .map { ViewModel.Notification.from(it.first, it.second) }
                ViewModel.ViewingData(
                    viewingState.filterState.isMentionOnly,
                    ntfs,
                    viewingState.poolCount
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

            val gatewayStateMap =
                gateways.map { (gatewayId, gateway) ->
                    val fetched = gateway.client.fetchNotifications()
                    gatewayId to GatewayState(gateway.isManaged, fetched)
                }.toMap()

            state = ViewingState(gatewayStateMap, main.filter.State(false, ""))
        }
        onChangeTriggeringViewUpdate()
    }

    fun markAsRead(gatewayId: GatewayId, notificationId: NotificationId) =
        synchronized(state) {
            doOnlyWhenViewingState { state ->
                main.notificationlist.markAsRead(
                    state.getAccessorForNotificationList(gatewayId),
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
                main.notificationlist.fetchToPool(state.getAccessorForNotificationList(gatewayId), gateway.client)
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

    fun sendLatestMentioned() = gateways.forEach { (gatewayId, gateway) ->
        synchronized(state) {
            doOnlyWhenViewingState { state ->
                val gatewayState = state.getGatewayState(gatewayId)
                desktopNotificationService.run({ update ->
                    gatewayState.idsDesktopNotificationSent = update(gatewayState.idsDesktopNotificationSent)
                }, gateway.client)
            }
        }
    }

    fun viewIncomingNotifications() =
        synchronized(state) {
            doOnlyWhenViewingState { state ->
                state.flushPool()
                onChangeTriggeringViewUpdate()
            }
        }

    fun fetchBack() =
        gateways.forEach { (gatewayId, gateway) ->
            synchronized(state) {
                doOnlyWhenViewingState { state ->
                    val gatewayState = state.getGatewayState(gatewayId)
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
