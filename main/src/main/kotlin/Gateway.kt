package main

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.InputStream
import java.net.URL
import java.net.URLClassLoader

typealias GatewayId = String

abstract class Gateway(val client: Client) {
    abstract fun makeHolder(): NotificationHolder

    fun fetchIcon(iconId: String): InputStream? {
        return client.privateIconFetcher()?.let { fetch -> fetch(iconId) }
    }
}

enum class GatewayFactory {
    UNMANAGED {
        override fun create(client: Client): Gateway {
            return UnmanagedGateway(client)
        }
    },

    MANAGED {
        override fun create(client: Client): Gateway {
            return ManagedGateway(client)
        }
    };

    abstract fun create(client: Client): Gateway
}

// 未読既読管理してくれるClient用
private class ManagedGateway(
    client: Client
) : Gateway(client) {
    data class Holder(
        private val unread: List<Notification>,
        private val pooled: List<Notification>
    ) : NotificationHolder {
        override fun getUnread() = unread

        override val pooledCount: Int
            get() = pooled.size

        override fun addToUnread(added: List<Notification>): NotificationHolder {
            val addedIds = added.map(Notification::id).distinct().toSet()
            return Holder((unread + added).distinctBy(Notification::id), pooled.filter { it.id !in addedIds })
        }

        override fun addToPooled(added: List<Notification>): NotificationHolder {
            val unreadIds = unread.map(Notification::id).distinct().toSet()
            return Holder(unread, (pooled + added.filter { it.id !in unreadIds }).distinctBy(Notification::id))
        }

        override fun read(id: NotificationId): NotificationHolder {
            return Holder(unread.filter { it.id != id }, pooled.filter { it.id != id })
        }

        override fun flushPool(): NotificationHolder {
            return Holder((unread + pooled).distinctBy(Notification::id), listOf())
        }
    }

    override fun makeHolder(): Holder {
        return Holder(listOf(), listOf())
    }
}

// 未読既読管理してくれないClient用
private class UnmanagedGateway(
    client: Client
) : Gateway(client) {
    data class Holder(
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
            return Holder((all + added).distinctBy(Notification::id), unreadIds + addedIds, pooledIds - addedIds)
        }

        override fun addToPooled(added: List<Notification>): NotificationHolder {
            val addedIds = added.map(Notification::id).distinct().toSet()
            return Holder((all + added).distinctBy(Notification::id), unreadIds, pooledIds + addedIds - unreadIds)
        }

        override fun read(id: NotificationId): NotificationHolder {
            return Holder(all, unreadIds - id, pooledIds - id)
        }

        override fun flushPool(): NotificationHolder {
            return Holder(all, unreadIds + pooledIds, setOf())
        }
    }

    override fun makeHolder(): Holder {
        return Holder(listOf(), setOf(), setOf())
    }
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

interface NotificationHolder {
    fun getUnread(): List<Notification>

    val pooledCount: Int

    fun addToUnread(added: List<Notification>): NotificationHolder

    fun addToPooled(added: List<Notification>): NotificationHolder

    fun read(id: NotificationId): NotificationHolder

    fun flushPool(): NotificationHolder
}
