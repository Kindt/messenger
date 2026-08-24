package com.avandocmsg.messenger.mobile.sdk

import com.avandocmsg.messenger.mobile.sdk.api.KorusApiClient
import com.avandocmsg.messenger.mobile.sdk.api.KorusHttp
import com.avandocmsg.messenger.mobile.sdk.call.InProcessCallClient
import com.avandocmsg.messenger.mobile.sdk.attachments.AttachmentPathResolver
import com.avandocmsg.messenger.mobile.sdk.identity.ServerId
import com.avandocmsg.messenger.mobile.sdk.model.ServerEntry
import com.avandocmsg.messenger.mobile.sdk.secure.InMemorySecureTokenStore
import com.avandocmsg.messenger.mobile.sdk.security.ServerUrlPolicy
import com.avandocmsg.messenger.mobile.sdk.session.MultiServerSessionManager
import com.avandocmsg.messenger.mobile.sdk.storage.ProfileStore
import com.avandocmsg.messenger.mobile.sdk.storage.ServerRegistry
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import java.nio.file.Files

class ProfileStoreTest {
    @Test
    fun createAndListProfiles() {
        val root = Files.createTempDirectory("korus-mobile-test")
        val store = ProfileStore(root)
        val p = store.createProfile("Alice")
        assertEquals(1, store.listProfiles().size)
        assertEquals("Alice", store.readProfile(p.profileId).displayName)
    }
}

class ServerUrlPolicyTest {
    @Test
    fun allowsLabHttp() {
        assertEquals(
            "http://10.0.2.2:18080",
            ServerUrlPolicy.validate("http://10.0.2.2:18080")
        )
    }

    @Test
    fun defaultsToHttps() {
        assertEquals("https://corp.example.com", ServerUrlPolicy.validate("corp.example.com"))
    }

    @Test
    fun rejectsHttpToProduction() {
        assertFailsWith<IllegalArgumentException> {
            ServerUrlPolicy.validate("http://evil.example.com")
        }
    }
}

class AttachmentPathResolverTest {
    @Test
    fun buildsDownloadsPath() {
        val root = Files.createTempDirectory("dl")
        val resolver = AttachmentPathResolver(root, "Alice")
        val path = resolver.resolve("corp", "f1", "report.pdf")
        assertTrue(path.toString().contains("KorusMessenger"))
        assertTrue(path.toString().contains("attachments"))
        assertTrue(path.toString().endsWith("f1-report.pdf"))
    }
}

class KorusApiClientTest {
  private fun client(engine: MockEngine): KorusApiClient {
        val http = io.ktor.client.HttpClient(engine) {
            install(ContentNegotiation) { json(KorusHttp.defaultJson()) }
        }
        return KorusApiClient(http, "http://lab:18080")
    }

    @Test
    fun healthAndLogin() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/health" -> respond(
                    content = """{"status":"ok"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/api/v1/auth/login" -> respond(
                    content = """{"access_token":"jwt-test","expires_in":3600}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> respond("{}", HttpStatusCode.NotFound)
            }
        }
        val api = client(engine)
        assertEquals("ok", api.health().status)
        assertEquals("jwt-test", api.login("alice", "alice").accessToken())
    }

    @Test
    fun createsProviderNeutralCallSession() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/v1/chats/chat-1/calls", request.url.encodedPath)
            respond(
                content = """
                    {"session_id":"s1","participant_id":"p1","chat_id":"chat-1","kind":"group",
                     "role":"host","status":"active","media_node_id":"embedded-1",
                     "signaling_path":"/api/v1/chats/chat-1/calls/s1/signals","ice_servers":[]}
                """.trimIndent(),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val call = client(engine).createCall("token", "chat-1", "group")

        assertEquals("s1", call.sessionId)
        assertEquals("p1", call.participantId)
        assertEquals("embedded-1", call.mediaNodeId)
    }

    @Test
    fun joinsSignalsPollsAndLeavesCallSession() = runTest {
        val visited = mutableListOf<String>()
        val engine = MockEngine { request ->
            visited += "${request.method.value} ${request.url.encodedPath}"
            val joinJson = """
                {"session_id":"s1","participant_id":"p2","chat_id":"chat-1","kind":"group",
                 "role":"member","status":"active","media_node_id":"embedded-1",
                 "signaling_path":"/api/v1/chats/chat-1/calls/s1/signals","ice_servers":[]}
            """.trimIndent()
            when {
                request.url.encodedPath.endsWith("/join") ->
                    respond(joinJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                request.url.encodedPath.endsWith("/signals/p2") && request.method == HttpMethod.Post ->
                    respond("", HttpStatusCode.Accepted)
                request.url.encodedPath.endsWith("/signals/p2") && request.method == HttpMethod.Get ->
                    respond(
                        """[{"id":"sig-1","type":"answer","sdp":"v=0"}]""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json")
                    )
                request.url.encodedPath.endsWith("/participants/p2/leave") ->
                    respond("", HttpStatusCode.NoContent)
                else -> respond("{}", HttpStatusCode.NotFound)
            }
        }
        val api = client(engine)
        val join = api.joinCall("token", "chat-1", "s1")
        api.sendCallSignal("token", join, type = "offer", sdp = "v=0")
        val signals = api.pollCallSignals("token", join)
        api.leaveCall("token", join)

        assertEquals("p2", join.participantId)
        assertEquals("answer", signals.single().type)
        assertEquals(4, visited.size)
    }

    @Test
    fun startsInProcessCallThroughNeutralSignaling() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/calls") && request.method == HttpMethod.Post ->
                    respond(
                        """
                        {"session_id":"s1","participant_id":"p1","chat_id":"chat-1","kind":"group",
                         "role":"host","status":"active","media_node_id":"embedded-1",
                         "signaling_path":"/api/v1/chats/chat-1/calls/s1/signals","ice_servers":[]}
                        """.trimIndent(),
                        HttpStatusCode.Created,
                        headersOf(HttpHeaders.ContentType, "application/json")
                    )
                request.url.encodedPath.endsWith("/signals/p1") && request.method == HttpMethod.Post ->
                    respond("", HttpStatusCode.Accepted)
                request.url.encodedPath.endsWith("/signals/p1") && request.method == HttpMethod.Get ->
                    respond(
                        """[{"id":"sig-1","type":"answer","sdp":"v=0\r\nanswer"}]""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json")
                    )
                request.url.encodedPath.endsWith("/participants/p1/leave") ->
                    respond("", HttpStatusCode.NoContent)
                else -> respond("{}", HttpStatusCode.NotFound)
            }
        }
        val api = client(engine)
        val media = RecordingAudioMedia()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
        InProcessCallClient(api, scope) { media }.use { call ->
            call.start("token", "chat-1", kind = "group", mediaIntent = "audio")
            assertEquals("s1", call.activeJoin?.sessionId)
            assertEquals("v=0\r\nanswer", media.connectedSdp)
            assertTrue(call.mediaReady())
        }
    }

    private class RecordingAudioMedia : com.avandocmsg.messenger.mobile.sdk.call.CallAudioMedia {
        var connectedSdp: String? = null

        override fun createOffer(): String = "v=0\r\noffer"

        override suspend fun connect(answerSdp: String) {
            connectedSdp = answerSdp
        }

        override fun sendPcmu(payload: ByteArray) {}

        override fun onPcmu(listener: (ByteArray) -> Unit) {}

        override fun mediaReady(): Boolean = connectedSdp != null

        override fun close() {}
    }

    @Test
    fun multiServerRegistryAndLogin() = runTest {
        val root = Files.createTempDirectory("korus-mobile-ms")
        val profile = ProfileStore(root).createProfile("Bob")
        val registry = ServerRegistry(ProfileStore(root).profileRoot(profile.profileId))
        val tokens = InMemorySecureTokenStore()
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/health") -> respond(
                    """{"status":"ok"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json")
                )
                request.url.encodedPath.endsWith("/login") -> respond(
                    """{"access_token":"t1"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> respond("{}", HttpStatusCode.NotFound)
            }
        }
        val http = io.ktor.client.HttpClient(engine) {
            install(ContentNegotiation) { json(KorusHttp.defaultJson()) }
        }
        val mgr = MultiServerSessionManager(registry, tokens, http)
        val entry = ServerEntry(
            serverId = "s1",
            displayName = "Lab",
            apiBaseUrl = "http://lab:18080"
        )
        mgr.registerServer(entry)
        val token = mgr.login(ServerId("s1"), "alice", "alice")
        assertEquals("t1", token)
    }
}
