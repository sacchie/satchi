package main

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.InputStream
import java.net.URL
import java.net.URLClassLoader

typealias GatewayId = String

class Gateway(val client: Client, val isManaged: Boolean) {
    fun fetchIcon(iconId: String): InputStream? {
        return client.privateIconFetcher()?.let { fetch -> fetch(iconId) }
    }
}

enum class GatewayFactory {
    UNMANAGED {
        override fun create(client: Client): Gateway {
            return Gateway(client, false)
        }
    },

    MANAGED {
        override fun create(client: Client): Gateway {
            return Gateway(client, true)
        }
    };

    abstract fun create(client: Client): Gateway
}

typealias Gateways = Map<GatewayId, Gateway>

data class GatewayDefinition(
    val clientFactory: String,
    val args: Map<String, String>,
    val type: GatewayFactory,
    val jarPath: String?
)

fun loadGateways(yamlFilename: String): Gateways {
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

data class GatewayStateSet(private val map: Map<GatewayId, GatewayState>) {
    fun getState(id: GatewayId) = map[id]!!

    fun getUnread(filterState: main.filter.State, limit: Int) =
        map.flatMap {
            val gatewayId = it.key
            it.value.getUnread()
                .filter { if (filterState.isMentionOnly) it.mentioned else true }
                .filter {
                    if (filterState.keyword.isBlank()) true else matchKeyword(
                        it,
                        filterState.keyword
                    )
                }
                .map { n -> Pair(gatewayId, n) }
        }
            .sortedBy { -it.second.timestamp.toEpochSecond() } // sort from newest to oldest
            .take(limit)

    val poolCount: Int
        get() = map.values.sumOf(GatewayState::pooledCount)

    fun flushPool() {
        map.values.forEach(GatewayState::flushPool)
    }

    private fun matchKeyword(ntf: Notification, keyword: String) =
        ntf.message.contains(keyword) || ntf.title.contains(keyword) || ntf.source.name.contains(keyword)
}

class GatewayState(isManaged: Boolean, initialNtfs: List<Notification>) {
    private var notificationHolder: NotificationHolder

    var idsDesktopNotificationSent: Set<NotificationId> = setOf()

    var timeMachineOffset: String = ""

    init {
        notificationHolder = if (isManaged) ManagedClientHolder(listOf(), listOf())
        else UnmanagedClientHolder(listOf(), setOf(), setOf())
        notificationHolder = notificationHolder.addToUnread(initialNtfs)
    }

    fun getUnread() = notificationHolder.getUnread()

    fun flushPool() {
        notificationHolder = notificationHolder.flushPool()
    }

    val pooledCount: Int
        get() = notificationHolder.pooledCount

    fun addToUnread(ntfs: List<Notification>) {
        notificationHolder = notificationHolder.addToUnread(ntfs)
    }

    fun getAccessorForNotificationList(): main.notificationlist.NotificationHolderAccessor {
        return object : main.notificationlist.NotificationHolderAccessor {
            override fun addToPooled(added: List<Notification>) {
                notificationHolder = notificationHolder.addToPooled(added)
            }

            override fun read(notificationId: NotificationId) {
                notificationHolder = notificationHolder.read(notificationId)
            }
        }
    }

    private interface NotificationHolder {
        fun getUnread(): List<Notification>

        val pooledCount: Int

        fun addToUnread(added: List<Notification>): NotificationHolder

        fun addToPooled(added: List<Notification>): NotificationHolder

        fun read(id: NotificationId): NotificationHolder

        fun flushPool(): NotificationHolder
    }

    // 未読既読管理してくれるClient用
    private data class ManagedClientHolder(
        private val unread: List<Notification>,
        private val pooled: List<Notification>
    ) : NotificationHolder {
        override fun getUnread() = unread

        override val pooledCount: Int
            get() = pooled.size

        override fun addToUnread(added: List<Notification>): NotificationHolder {
            val addedIds = added.map(Notification::id).distinct().toSet()
            return ManagedClientHolder(
                (unread + added).distinctBy(Notification::id),
                pooled.filter { it.id !in addedIds }
            )
        }

        override fun addToPooled(added: List<Notification>): NotificationHolder {
            val unreadIds = unread.map(Notification::id).distinct().toSet()
            return ManagedClientHolder(
                unread,
                (pooled + added.filter { it.id !in unreadIds }).distinctBy(Notification::id)
            )
        }

        override fun read(id: NotificationId): NotificationHolder {
            return ManagedClientHolder(unread.filter { it.id != id }, pooled.filter { it.id != id })
        }

        override fun flushPool(): NotificationHolder {
            return ManagedClientHolder((unread + pooled).distinctBy(Notification::id), listOf())
        }
    }

    // 未読既読管理してくれないClient用
    private data class UnmanagedClientHolder(
        private val all: List<Notification>,
        private val unreadIds: Set<NotificationId>,
        private val pooledIds: Set<NotificationId>
    ) : NotificationHolder {
        override fun getUnread(): List<Notification> {
            return all.filter { it.id in unreadIds }
        }

        override val pooledCount: Int
            get() = pooledIds.size

        override fun addToUnread(added: List<Notification>): NotificationHolder {
            val addedIds = added.map(Notification::id).distinct().toSet()
            return UnmanagedClientHolder((all + added).distinctBy(Notification::id), unreadIds + addedIds, pooledIds - addedIds)
        }

        override fun addToPooled(added: List<Notification>): NotificationHolder {
            val addedIds = added.map(Notification::id).distinct().toSet()
            return UnmanagedClientHolder((all + added).distinctBy(Notification::id), unreadIds, pooledIds + addedIds - unreadIds)
        }

        override fun read(id: NotificationId): NotificationHolder {
            return UnmanagedClientHolder(all, unreadIds - id, pooledIds - id)
        }

        override fun flushPool(): NotificationHolder {
            return UnmanagedClientHolder(all, unreadIds + pooledIds, setOf())
        }
    }
}
