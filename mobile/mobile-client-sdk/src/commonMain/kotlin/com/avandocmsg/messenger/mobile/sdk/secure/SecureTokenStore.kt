package com.avandocmsg.messenger.mobile.sdk.secure

interface SecureTokenStore {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)
    fun clear()
}

class InMemorySecureTokenStore : SecureTokenStore {
    private val map = mutableMapOf<String, String>()

    override fun put(key: String, value: String) {
        map[key] = value
    }

    override fun get(key: String): String? = map[key]

    override fun remove(key: String) {
        map.remove(key)
    }

    override fun clear() {
        map.clear()
    }
}

class ScopedSecureTokenStore(
    private val backing: SecureTokenStore,
    private val scope: String
) : SecureTokenStore {
    private fun scoped(key: String) = "$scope::$key"

    override fun put(key: String, value: String) = backing.put(scoped(key), value)
    override fun get(key: String): String? = backing.get(scoped(key))
    override fun remove(key: String) = backing.remove(scoped(key))
    override fun clear() {
        // Only clear keys in this scope — iterate not available; caller uses profile switch clear on parent
    }
}

fun tokenKey(serverId: String, username: String): String = "token::$serverId::$username"
