package com.avandocmsg.messenger.mobile.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.avandocmsg.messenger.mobile.call.AndroidCallAudio
import com.avandocmsg.messenger.mobile.sdk.call.InProcessCallClient
import com.avandocmsg.messenger.mobile.sdk.call.NativeCallAudioMedia
import com.avandocmsg.messenger.mobile.sdk.capabilities.CapabilityGating
import com.avandocmsg.messenger.mobile.sdk.identity.ServerId
import com.avandocmsg.messenger.mobile.sdk.model.CapabilitiesDto
import com.avandocmsg.messenger.mobile.sdk.model.ChatDto
import com.avandocmsg.messenger.mobile.sdk.model.ContactDto
import com.avandocmsg.messenger.mobile.sdk.model.LocalProfile
import com.avandocmsg.messenger.mobile.sdk.model.MessageDto
import com.avandocmsg.messenger.mobile.sdk.model.ProfileSettings
import com.avandocmsg.messenger.mobile.sdk.model.ServerEntry
import com.avandocmsg.messenger.mobile.sdk.security.ServerUrlPolicy
import com.avandocmsg.messenger.mobile.sdk.offline.OutgoingMessageQueue
import com.avandocmsg.messenger.mobile.sdk.secure.AndroidSecureTokenStore
import com.avandocmsg.messenger.mobile.sdk.session.MultiServerSessionManager
import com.avandocmsg.messenger.mobile.sdk.storage.ProfileSettingsStore
import com.avandocmsg.messenger.mobile.sdk.storage.ProfileStore
import com.avandocmsg.messenger.mobile.sdk.storage.ServerRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class Screen { ProfilePicker, ServerList, Login, Home }

enum class HomeTab { Chats, Contacts, Addons, Settings }

enum class LiveCallPhase { Idle, Connecting, Active }

data class AppState(
    val screen: Screen = Screen.ProfilePicker,
    val profiles: List<LocalProfile> = emptyList(),
    val activeProfile: LocalProfile? = null,
    val newProfileName: String = "",
    val servers: List<ServerEntry> = emptyList(),
    val serverUrl: String = "http://10.0.2.2:18080",
    val serverName: String = "Lab",
    val loginServerId: String? = null,
    val activeServerId: String? = null,
    val activeServerName: String = "",
    val username: String = "user1",
    val password: String = "12345",
    val loggedInUser: String? = null,
    val error: String? = null,
    val homeTab: HomeTab = HomeTab.Chats,
    val capabilities: CapabilitiesDto? = null,
    val contacts: List<ContactDto> = emptyList(),
    val searchQuery: String = "",
    val searchDone: Boolean = false,
    val settings: ProfileSettings = ProfileSettings(),
    val chats: List<ChatDto> = emptyList(),
    val selectedChatId: String? = null,
    val messages: List<MessageDto> = emptyList(),
    val composeText: String = "",
    val liveCallPhase: LiveCallPhase = LiveCallPhase.Idle,
    val liveCallDetail: String? = null
)

class KorusViewModel(private val context: Context) : ViewModel() {
    private val root = context.filesDir.toPath()
    private val profileStore = ProfileStore(root)
    private val settingsStore = ProfileSettingsStore(profileStore)
    private val tokenStore = AndroidSecureTokenStore(context)
    private val http = KorusHttp.createClient()

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state

    private var sessionManager: MultiServerSessionManager? = null
    private var serverRegistry: ServerRegistry? = null
    private var outgoingQueue: OutgoingMessageQueue? = null
    private var liveCall: InProcessCallClient? = null
    private var liveAudio: AndroidCallAudio? = null

    init {
        refreshProfiles()
        val profiles = profileStore.listProfiles()
        when {
            profiles.isEmpty() -> {
                val p = profileStore.createProfile("Default")
                selectProfile(p.profileId)
            }
            profiles.size == 1 -> selectProfile(profiles.first().profileId)
        }
    }

    private fun withProfile(profileId: String): ProfileStore {
        profileStore.touchProfile(profileId)
        val dir = profileStore.profileRoot(profileId)
        serverRegistry = ServerRegistry(dir)
        sessionManager = MultiServerSessionManager(serverRegistry!!, tokenStore, http)
        outgoingQueue = OutgoingMessageQueue(dir.resolve("state/outgoing.json"))
        return profileStore
    }

    fun refreshProfiles() {
        _state.update { it.copy(profiles = profileStore.listProfiles()) }
    }

    fun onNewProfileName(v: String) = _state.update { it.copy(newProfileName = v) }

    fun createProfile() {
        val name = _state.value.newProfileName.trim()
        if (name.isEmpty()) return
        profileStore.createProfile(name)
        _state.update { it.copy(newProfileName = "") }
        refreshProfiles()
    }

    fun selectProfile(profileId: String) {
        val profile = profileStore.readProfile(profileId)
        withProfile(profileId)
        val settings = settingsStore.read(profileId)
        val servers = serverRegistry!!.load().servers
        _state.update {
            it.copy(
                screen = Screen.ServerList,
                activeProfile = profile,
                servers = servers,
                settings = settings,
                loggedInUser = null,
                error = null
            )
        }
    }

    fun switchProfile() {
        tokenStore.clear()
        _state.update {
            AppState(screen = Screen.ProfilePicker, profiles = profileStore.listProfiles())
        }
    }

    fun onServerUrl(v: String) = _state.update { it.copy(serverUrl = v) }
    fun onServerName(v: String) = _state.update { it.copy(serverName = v) }

    fun addServer() {
        val profile = _state.value.activeProfile ?: return
        withProfile(profile.profileId)
        val url = ServerUrlPolicy.validate(_state.value.serverUrl)
        val name = _state.value.serverName.trim().ifBlank { "Server" }
        viewModelScope.launch {
            try {
                val entry = ServerEntry(
                    serverId = UUID.randomUUID().toString(),
                    displayName = name,
                    apiBaseUrl = url
                )
                sessionManager!!.registerServer(entry)
                val client = sessionManager!!.clientFor(ServerId(entry.serverId))!!
                client.health()
                _state.update {
                    it.copy(servers = serverRegistry!!.load().servers, error = null)
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun openLogin(serverId: String) {
        _state.update { it.copy(screen = Screen.Login, loginServerId = serverId, error = null) }
    }

    fun onUsername(v: String) = _state.update { it.copy(username = v) }
    fun onPassword(v: String) = _state.update { it.copy(password = v) }

    fun login() {
        val serverId = _state.value.loginServerId ?: return
        val profile = _state.value.activeProfile ?: return
        withProfile(profile.profileId)
        viewModelScope.launch {
            try {
                sessionManager!!.login(ServerId(serverId), _state.value.username, _state.value.password)
                val token = sessionManager!!.token(ServerId(serverId), _state.value.username)!!
                val me = sessionManager!!.clientFor(ServerId(serverId))!!.me(token)
                _state.update {
                    it.copy(loggedInUser = me.login ?: me.resolvedId(), error = null)
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, loggedInUser = null) }
            }
        }
    }

    fun logout() {
        hangupLiveCall()
        tokenStore.clear()
        _state.update {
            it.copy(
                loggedInUser = null,
                activeServerId = null,
                screen = Screen.ServerList,
                chats = emptyList(),
                messages = emptyList(),
                selectedChatId = null,
                contacts = emptyList(),
                capabilities = null,
                searchDone = false,
                composeText = "",
                error = null,
                liveCallPhase = LiveCallPhase.Idle,
                liveCallDetail = null
            )
        }
    }

    fun openChatsFromLogin() {
        openChats(_state.value.loginServerId ?: return)
    }

    fun openChats(serverId: String) {
        val profile = _state.value.activeProfile ?: return
        withProfile(profile.profileId)
        val server = _state.value.servers.firstOrNull { it.serverId == serverId } ?: return
        viewModelScope.launch {
            try {
                val token = sessionManager!!.token(ServerId(serverId), _state.value.username)
                    ?: sessionManager!!.login(ServerId(serverId), _state.value.username, _state.value.password)
                val client = sessionManager!!.clientFor(ServerId(serverId))!!
                val chats = client.listChats(token)
                val caps = client.capabilities(token)
                val contacts = try { client.listContacts(token) } catch (_: Exception) { emptyList() }
                flushOfflineQueue(serverId)
                _state.update {
                    it.copy(
                        screen = Screen.Home,
                        activeServerId = serverId,
                        activeServerName = server.displayName,
                        chats = chats,
                        capabilities = caps,
                        contacts = contacts,
                        selectedChatId = null,
                        messages = emptyList(),
                        homeTab = HomeTab.Chats,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    private suspend fun flushOfflineQueue(serverId: String) {
        val q = outgoingQueue ?: return
        val pending = q.drain()
        if (pending.isEmpty()) return
        val token = sessionManager!!.token(ServerId(serverId), _state.value.username) ?: return
        val client = sessionManager!!.clientFor(ServerId(serverId))!!
        for (item in pending) {
            val parts = item.chatRef.split(":", limit = 2)
            if (parts.size != 2 || parts[0] != serverId) continue
            try {
                client.sendMessage(token, parts[1], item.content)
            } catch (_: Exception) {
                q.enqueue(com.avandocmsg.messenger.mobile.sdk.identity.ChatRef(ServerId(serverId), parts[1]), item.content)
            }
        }
    }

    fun selectHomeTab(tab: HomeTab) {
        _state.update { it.copy(homeTab = tab, error = null) }
        if (tab == HomeTab.Contacts && _state.value.contacts.isEmpty()) {
            refreshContacts()
        }
    }

    fun refreshContacts() {
        val serverId = _state.value.activeServerId ?: return
        viewModelScope.launch {
            try {
                val token = sessionManager!!.token(ServerId(serverId), _state.value.username)!!
                val contacts = sessionManager!!.clientFor(ServerId(serverId))!!.listContacts(token)
                _state.update { it.copy(contacts = contacts) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun onSearchQuery(v: String) = _state.update { it.copy(searchQuery = v, searchDone = false) }

    fun runSearch() {
        val serverId = _state.value.activeServerId ?: return
        val q = _state.value.searchQuery.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            try {
                val token = sessionManager!!.token(ServerId(serverId), _state.value.username)!!
                sessionManager!!.clientFor(ServerId(serverId))!!.searchMessages(token, q)
                _state.update { it.copy(searchDone = true, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, searchDone = false) }
            }
        }
    }

    fun onSettingsLocale(v: String) {
        val profile = _state.value.activeProfile ?: return
        val settings = _state.value.settings.copy(locale = v)
        settingsStore.write(profile.profileId, settings)
        _state.update { it.copy(settings = settings) }
    }

    fun onSettingsTheme(v: String) {
        val profile = _state.value.activeProfile ?: return
        val settings = _state.value.settings.copy(theme = v)
        settingsStore.write(profile.profileId, settings)
        _state.update { it.copy(settings = settings) }
    }

    fun isAddonEnabled(addonId: String): Boolean {
        val caps = _state.value.capabilities ?: return false
        return CapabilityGating.isAddonEnabled(caps, addonId)
    }

    fun selectChat(chatId: String) {
        hangupLiveCall()
        _state.update { it.copy(selectedChatId = chatId, messages = emptyList()) }
        refreshMessages()
    }

    fun clearChatSelection() {
        hangupLiveCall()
        _state.update { it.copy(selectedChatId = null, messages = emptyList()) }
    }

    fun onComposeText(v: String) = _state.update { it.copy(composeText = v) }

    fun sendMessage() {
        val chatId = _state.value.selectedChatId ?: return
        val serverId = _state.value.activeServerId ?: return
        val text = _state.value.composeText.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            try {
                val token = sessionManager!!.token(ServerId(serverId), _state.value.username)!!
                val msg = sessionManager!!.clientFor(ServerId(serverId))!!.sendMessage(token, chatId, text)
                _state.update { it.copy(composeText = "", messages = it.messages + msg) }
            } catch (e: Exception) {
                outgoingQueue?.enqueue(
                    com.avandocmsg.messenger.mobile.sdk.identity.ChatRef(ServerId(serverId), chatId),
                    text
                )
                _state.update { it.copy(error = "Queued offline: ${e.message}", composeText = "") }
            }
        }
    }

    private fun refreshMessages() {
        val chatId = _state.value.selectedChatId ?: return
        val serverId = _state.value.activeServerId ?: return
        viewModelScope.launch {
            try {
                val token = sessionManager!!.token(ServerId(serverId), _state.value.username)!!
                val list = sessionManager!!.clientFor(ServerId(serverId))!!.listMessages(token, chatId)
                _state.update { it.copy(messages = list) }
            } catch (_: Exception) {
            }
        }
    }

    fun backToServers() {
        hangupLiveCall()
        _state.update { it.copy(screen = Screen.ServerList, selectedChatId = null) }
    }

    fun toggleLiveCall() {
        if (liveCall != null) {
            hangupLiveCall()
            return
        }
        startLiveCall()
    }

    private fun startLiveCall() {
        val chatId = _state.value.selectedChatId ?: return
        val serverId = _state.value.activeServerId ?: return
        if (!isAddonEnabled("addon-calls")) {
            _state.update { it.copy(error = "Звонки недоступны на этом сервере") }
            return
        }
        _state.update {
            it.copy(
                liveCallPhase = LiveCallPhase.Connecting,
                liveCallDetail = "Подключение…",
                error = null
            )
        }
        viewModelScope.launch {
            try {
                val token = sessionManager!!.token(ServerId(serverId), _state.value.username)!!
                val client = sessionManager!!.clientFor(ServerId(serverId))!!
                val call = InProcessCallClient(client, viewModelScope) { NativeCallAudioMedia() }
                call.onHangup { hangupLiveCall(fromRemote = true) }
                call.start(token, chatId, kind = "group", mediaIntent = "audio")
                val audio = AndroidCallAudio(viewModelScope, call).also { it.start() }
                liveCall = call
                liveAudio = audio
                val capture = if (audio.captureEnabled) "микрофон" else "без микрофона"
                val playback = if (audio.playbackEnabled) "динамик" else "без динамика"
                _state.update {
                    it.copy(
                        liveCallPhase = LiveCallPhase.Active,
                        liveCallDetail = "Разговор · $capture · $playback"
                    )
                }
            } catch (e: Exception) {
                hangupLiveCall()
                _state.update {
                    it.copy(
                        error = e.message,
                        liveCallPhase = LiveCallPhase.Idle,
                        liveCallDetail = null
                    )
                }
            }
        }
    }

    fun hangupLiveCall(fromRemote: Boolean = false) {
        liveAudio?.close()
        liveAudio = null
        liveCall?.leave()
        liveCall = null
        _state.update {
            it.copy(
                liveCallPhase = LiveCallPhase.Idle,
                liveCallDetail = if (fromRemote) "Звонок завершён" else null
            )
        }
    }

    override fun onCleared() {
        hangupLiveCall()
        super.onCleared()
    }
}

class KorusViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(KorusViewModel::class.java)) {
            return KorusViewModel(context) as T
        }
        throw IllegalArgumentException("unknown vm")
    }
}
