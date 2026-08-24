package com.avandocmsg.messenger.desktop.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.avandocmsg.messenger.desktop.sdk.api.KorusApiClient;
import com.avandocmsg.messenger.desktop.sdk.identity.ServerId;
import com.avandocmsg.messenger.desktop.sdk.model.SendMessageRequest;
import com.avandocmsg.messenger.desktop.sdk.session.ApiDesktopSession;
import com.avandocmsg.messenger.desktop.sdk.session.ServerConnectCoordinator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Integration tests against live QEMU stack (:18080). Skipped if server down. */
@Tag("live-server")
class LiveServerIntegrationTest {

    private static final String BASE = System.getenv().getOrDefault("KORUS_LIVE_API_URL", "http://127.0.0.1:18080");
    private static final String USER = System.getenv().getOrDefault("KORUS_DESKTOP_SMOKE_USER", "admin");
    private static final String PASS = System.getenv().getOrDefault("KORUS_DESKTOP_SMOKE_PASSWORD", "admin");

    private static boolean live;

    @BeforeAll
    static void probe() throws Exception {
        var req = HttpRequest.newBuilder(URI.create(BASE + "/api/v1/health"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();
        var resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        live = resp.statusCode() == 200 && resp.body().contains("ok");
        assumeTrue(live, "live server not available at " + BASE);
    }

    @Test
    void healthLoginBrandingChatsMessaging() throws Exception {
        var api = new KorusApiClient(KorusApiClient.defaultHttpClient(), BASE);
        assertEquals("ok", api.health().status());

        var token = api.login(USER, PASS).accessToken();
        assertNotNull(token);

        var branding = api.brandingMe(token);
        assertNotNull(branding.palette());

        var chats = api.listChats(token);
        assertFalse(chats.isEmpty());

        var chatId = chats.getFirst().resolvedId();
        var sent = api.sendMessage(token, chatId, new SendMessageRequest("desktop-sdk-live-test"));
        assertNotNull(sent.id());

        var messages = api.listMessages(token, chatId, null);
        assertFalse(messages.isEmpty());

        api.markRead(token, chatId);

        var search = api.search(token, "desktop");
        assertNotNull(search.hits());
    }

    @Test
    void connectCoordinatorAgainstLive() throws Exception {
        var root = java.nio.file.Files.createTempDirectory("korus-live");
        System.setProperty("korus.desktop.data.dir", root.toString());
        var runtime = new DesktopRuntime();
        var profile = runtime.profileStore().createProfile("LiveTest");
        runtime.activateProfile(profile);

        var entry = new com.avandocmsg.messenger.desktop.sdk.model.ServerEntry(
            java.util.UUID.randomUUID().toString(),
            "QEMU Lab",
            BASE
        );
        ServerConnectCoordinator.ConnectResult result = runtime.connectCoordinator()
            .connect(entry, USER, PASS, null, false);
        assertNotNull(result.token());

        var session = new ApiDesktopSession(runtime.sessions(), runtime.fileTransferService());
        var chats = session.listChats(new ServerId(entry.serverId()), USER);
        assertFalse(chats.isEmpty());
        assertNotNull(runtime.capabilitiesCache().get(new ServerId(entry.serverId())));
        assertTrue(runtime.wsHub().isConnected(new ServerId(entry.serverId())));
    }

    @Test
    void fileUploadConferenceTypingWs() throws Exception {
        var api = new KorusApiClient(KorusApiClient.defaultHttpClient(), BASE);
        var token = api.login(USER, PASS).accessToken();
        var chats = api.listChats(token);
        var chatId = chats.getFirst().resolvedId();

        var tmp = java.nio.file.Files.createTempFile("desktop-live", ".txt");
        java.nio.file.Files.writeString(tmp, "desktop file smoke " + java.time.Instant.now());
        var uploaded = api.uploadFile(token, tmp, "smoke.txt");
        assertNotNull(uploaded.id());
        var sent = api.sendMessage(token, chatId, new SendMessageRequest("file", uploaded.id(), null, null));
        assertNotNull(sent.id());

        api.sendTyping(token, chatId);
        var unread = api.unreadCount(token, chatId);
        assertTrue(unread.unreadCount() >= 0);

        var conf = api.createConference(token, chatId, new com.avandocmsg.messenger.desktop.sdk.model.CreateConferenceRequest("SDK smoke"));
        assertNotNull(conf.joinUrl());

        var call = api.createCall(token, chatId, "group", "audio");
        assertNotNull(call.sessionId());
        var webUrl = com.avandocmsg.messenger.desktop.sdk.web.WebUiUrlResolver.callJoinUrl(
            com.avandocmsg.messenger.desktop.sdk.web.WebUiUrlResolver.defaultFromApiBase(BASE),
            chatId,
            call.sessionId(),
            "audio"
        );
        assertTrue(webUrl.contains("call_session="));

        var entry = new com.avandocmsg.messenger.desktop.sdk.model.ServerEntry(
            java.util.UUID.randomUUID().toString(),
            "WS smoke",
            BASE
        );
        var hub = new com.avandocmsg.messenger.desktop.sdk.ws.MultiServerWsHub(KorusApiClient.defaultHttpClient());
        hub.connect(new ServerId(entry.serverId()), entry, token);
        assertTrue(hub.isConnected(new ServerId(entry.serverId())));
        hub.close();
    }

    @Test
    void twoInProcessClientsExchangePcmuOnLiveSfu() throws Exception {
        assumeTrue(live);
        var api = new KorusApiClient(KorusApiClient.defaultHttpClient(), BASE);
        var userA = System.getenv().getOrDefault("KORUS_DESKTOP_SMOKE_USER_A", "smoke_user_a");
        var userB = System.getenv().getOrDefault("KORUS_DESKTOP_SMOKE_USER_B", "smoke_user_b");
        var pass = System.getenv().getOrDefault("KORUS_CROSS_SMOKE_PASSWORD", "smokepass123");
        String tokenA;
        String tokenB;
        try {
            tokenA = api.login(userA, pass).accessToken();
            tokenB = api.login(userB, pass).accessToken();
        } catch (RuntimeException missingUsers) {
            assumeTrue(false, "cross-client smoke users are not provisioned");
            return;
        }
        var memberB = api.me(tokenB).resolvedId();
        var chat = api.createGroupChat(tokenA, "live-pcmu-" + java.time.Instant.now(), java.util.List.of(memberB));
        var received = new java.util.concurrent.ArrayBlockingQueue<byte[]>(4);
        try (
            var caller = new com.avandocmsg.messenger.desktop.sdk.call.InProcessCallClient(api);
            var callee = new com.avandocmsg.messenger.desktop.sdk.call.InProcessCallClient(api)
        ) {
            caller.start(tokenA, chat.resolvedId(), "group", "audio");
            callee.join(tokenB, chat.resolvedId(), caller.join().sessionId());
            callee.onPcmu(received::offer);
            assertTrue(caller.mediaReady());
            assertTrue(callee.mediaReady());
            var payload = new byte[160];
            java.util.Arrays.fill(payload, (byte) 0x5a);
            caller.sendPcmu(payload);
            var remote = received.poll(12, java.util.concurrent.TimeUnit.SECONDS);
            assertNotNull(remote, "callee did not receive PCMU from the live SFU");
            assertTrue(java.util.Arrays.equals(payload, remote));
        }
    }
}
