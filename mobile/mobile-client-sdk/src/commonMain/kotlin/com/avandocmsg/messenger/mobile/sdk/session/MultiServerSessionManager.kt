package com.avandocmsg.messenger.mobile.sdk.session

import com.avandocmsg.messenger.mobile.sdk.identity.ServerId
import com.avandocmsg.messenger.mobile.sdk.model.ServerEntry
import com.avandocmsg.messenger.mobile.sdk.secure.SecureTokenStore
import com.avandocmsg.messenger.mobile.sdk.secure.tokenKey
import com.avandocmsg.messenger.mobile.sdk.storage.ServerRegistry
import com.avandocmsg.messenger.mobile.sdk.api.KorusApiClient
import com.avandocmsg.messenger.mobile.sdk.api.KorusHttp
import io.ktor.client.HttpClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MultiServerSessionManager(
    private val registry: ServerRegistry,
    private val tokenStore: SecureTokenStore,
    private val http: HttpClient = KorusHttp.createClient()
) {
    private val mutex = Mutex()
    private val clients = mutableMapOf<ServerId, KorusApiClient>()

    suspend fun registerServer(entry: ServerEntry): ServerEntry {
        val client = clientFor(entry)
        client.health()
        return mutex.withLock {
            registry.upsert(entry.copy(lastHealthOkAt = java.time.Instant.now().toString()))
            entry
        }
    }

    fun clientFor(entry: ServerEntry): KorusApiClient {
        val id = ServerId(entry.serverId)
        return clients.getOrPut(id) {
            KorusApiClient(http, entry.apiBaseUrl)
        }
    }

    fun clientFor(serverId: ServerId): KorusApiClient? = clients[serverId]

    suspend fun login(serverId: ServerId, username: String, password: String): String {
        val entry = registry.load().servers.firstOrNull { it.serverId == serverId.value }
            ?: error("server not found: ${serverId.value}")
        val token = clientFor(entry).login(username, password).accessToken()
        if (token.isBlank()) error("empty token")
        tokenStore.put(tokenKey(serverId.value, username), token)
        return token
    }

    fun token(serverId: ServerId, username: String): String? =
        tokenStore.get(tokenKey(serverId.value, username))

    fun clearTokens() {
        tokenStore.clear()
    }

    fun activeServers(): List<ServerEntry> = registry.load().servers.filter { !it.paused }
}
