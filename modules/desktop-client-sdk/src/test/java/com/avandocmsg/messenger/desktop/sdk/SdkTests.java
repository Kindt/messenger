package com.avandocmsg.messenger.desktop.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.avandocmsg.messenger.desktop.sdk.api.KorusApiClient;
import com.avandocmsg.messenger.desktop.sdk.attachments.AttachmentPathResolver;
import com.avandocmsg.messenger.desktop.sdk.capabilities.CapabilityGate;
import com.avandocmsg.messenger.desktop.sdk.demo.DemoDataStore;
import com.avandocmsg.messenger.desktop.sdk.identity.ChatRef;
import com.avandocmsg.messenger.desktop.sdk.identity.ServerId;
import com.avandocmsg.messenger.desktop.sdk.mentions.MentionParser;
import com.avandocmsg.messenger.desktop.sdk.mls.MlsAesGcmCipher;
import com.avandocmsg.messenger.desktop.sdk.mls.MlsSessionKeyDeriver;
import com.avandocmsg.messenger.desktop.sdk.mls.MlsWireCodec;
import com.avandocmsg.messenger.desktop.sdk.model.ServerEntry;
import com.avandocmsg.messenger.desktop.sdk.parity.WebParityAuditor;
import com.avandocmsg.messenger.desktop.sdk.queue.OutgoingMessageQueue;
import com.avandocmsg.messenger.desktop.sdk.secure.InMemorySecureTokenStore;
import com.avandocmsg.messenger.desktop.sdk.session.DemoDesktopSession;
import com.avandocmsg.messenger.desktop.sdk.session.MultiServerSessionManager;
import com.avandocmsg.messenger.desktop.sdk.storage.ProfileStore;
import com.avandocmsg.messenger.desktop.sdk.storage.ServerRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

class KorusApiClientTest {

    @Test
    void healthAndLogin() throws Exception {
        try (var server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{\"status\":\"ok\"}").addHeader("Content-Type", "application/json"));
            server.enqueue(new MockResponse()
                .setBody("{\"access_token\":\"jwt-test\",\"expires_in\":3600}")
                .addHeader("Content-Type", "application/json"));
            server.start();
            var api = new KorusApiClient(KorusApiClient.defaultHttpClient(), server.url("/").toString().replaceAll("/$", ""));
            assertEquals("ok", api.health().status());
            assertEquals("jwt-test", api.login("alice", "secret").accessToken());
        }
    }

    @Test
    void searchEndpoint() throws Exception {
        try (var server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                .setBody("[{\"id\":\"m1\",\"chat_id\":\"c1\",\"content\":\"hello\"}]")
                .addHeader("Content-Type", "application/json"));
            server.start();
            var api = new KorusApiClient(KorusApiClient.defaultHttpClient(), server.url("/").toString().replaceAll("/$", ""));
            var resp = api.search("token", "hello");
            assertEquals(1, resp.hits().size());
        }
    }

    @Test
    void multiServerLogin() throws Exception {
        try (var server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{\"status\":\"ok\"}").addHeader("Content-Type", "application/json"));
            server.enqueue(new MockResponse()
                .setBody("{\"access_token\":\"t1\"}")
                .addHeader("Content-Type", "application/json"));
            server.start();
            var root = Files.createTempDirectory("korus-desktop-ms");
            var profile = new ProfileStore(root).createProfile("Bob");
            var registry = new ServerRegistry(new ProfileStore(root).profileRoot(profile.profileId()));
            var mgr = new MultiServerSessionManager(registry, new InMemorySecureTokenStore(), KorusApiClient.defaultHttpClient());
            var base = server.url("/").toString().replaceAll("/$", "");
            mgr.registerServer(new ServerEntry("s1", "Lab", base));
            var token = mgr.login(new ServerId("s1"), "alice", "alice");
            assertEquals("t1", token);
        }
    }
}

class ProfileStoreTest {

    @Test
    void createAndList() throws Exception {
        var root = Files.createTempDirectory("korus-desktop-profile");
        var store = new ProfileStore(root);
        var p = store.createProfile("Alice");
        assertEquals(1, store.listProfiles().size());
        assertEquals("Alice", store.readProfile(p.profileId()).displayName());
    }
}

class ProfileSettingsStoreTest {

    @Test
    void roundTrip() throws Exception {
        var root = Files.createTempDirectory("korus-desktop-settings");
        var profiles = new ProfileStore(root);
        var p = profiles.createProfile("Bob");
        var settingsStore = new com.avandocmsg.messenger.desktop.sdk.storage.ProfileSettingsStore(profiles);
        settingsStore.write(p.profileId(), new com.avandocmsg.messenger.desktop.sdk.model.ProfileSettings(
            "en", "dark", null, "beta", "notify", "https://feed.example/manifest.json"
        ));
        var read = settingsStore.read(p.profileId());
        assertEquals("en", read.locale());
        assertEquals("beta", read.updateChannel());
    }
}

class AttachmentPathResolverTest {

    @Test
    void buildsDownloadsPath() {
        var root = Path.of("C:", "Users", "test", "Downloads");
        var resolver = new AttachmentPathResolver(root, "Alice");
        var path = resolver.resolve("corp", "f1", "report.pdf");
        assertTrue(path.toString().contains("KorusMessenger"));
        assertTrue(path.toString().contains("attachments"));
        assertTrue(path.toString().endsWith("f1-report.pdf"));
    }
}

class MentionParserTest {

    @Test
    void parsesMentions() {
        assertTrue(MentionParser.mentionsAll("ping @all now"));
        assertEquals(1, MentionParser.parseMentionedUserIds("hi @alice").size());
    }
}

class DemoSessionTest {

    @Test
    void demoMultiServerInbox() {
        var session = new DemoDesktopSession(new DemoDataStore());
        assertEquals(2, session.servers().size());
        var sid = new ServerId(DemoDataStore.SERVER_A);
        assertFalse(session.listChats(sid, "u").isEmpty());
        var chat = session.listChats(sid, "u").getFirst();
        var ref = new ChatRef(sid, chat.resolvedId());
        session.send(sid, "u", ref, "test @bob", null);
        assertTrue(session.search(sid, "u", "test").hits().size() >= 1);
    }
}

class OutgoingQueueTest {

    @Test
    void enqueueAndLoad() throws Exception {
        var dir = Files.createTempDirectory("q");
        var q = new OutgoingMessageQueue(dir);
        var ref = new ChatRef(new ServerId("s1"), "c1");
        q.enqueue(ref, "offline msg", null);
        assertEquals(1, q.load().size());
    }
}

class WebParityAuditTest {

    @Test
    void auditsWebBlocksAgainstDesktop() throws Exception {
        var repo = repoRoot();
        var manifest = repo.resolve("specs/030-vpp-product-verification/contracts/ui-block-manifest.json");
        var matrix = repo.resolve("specs/031-desktop-java-client/contracts/feature-parity-matrix.json");
        var report = new WebParityAuditor().audit(manifest, matrix);
        assertTrue(report.webBlocks().size() >= 10);
        assertTrue(report.countsByStatus().getOrDefault("implemented_ui", 0L) >= 8);
        assertFalse(report.gaps().size() > 5, "too many gaps: " + report.gaps());
    }

    private static Path repoRoot() {
        var p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("specs/031-desktop-java-client"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("repo root not found from user.dir");
        }
        return p;
    }
}

class CapabilityGateTest {

    @Test
    void gatesAddons() {
        var session = new DemoDesktopSession(new DemoDataStore());
        var gate = new CapabilityGate(session.capabilities(new ServerId(DemoDataStore.SERVER_A), "u"));
        assertTrue(gate.isEnabled(CapabilityGate.Feature.SEARCH));
        assertFalse(gate.isEnabled(CapabilityGate.Feature.PRODUCTIVITY));
    }
}

class VpnProfileTest {

    @Test
    void validatesWireGuardConfig() {
        var profile = new com.avandocmsg.messenger.desktop.sdk.vpn.VpnProfile(
            1, "p1", "Corp VPN", "wireguard", "password", "before_api",
            "vpn.corp", 51820, "user", false, true, null,
            "[Interface]\nPrivateKey=x\n[Peer]\nPublicKey=y\nEndpoint=h:51820",
            null, java.util.Map.of(), null
        );
        assertTrue(com.avandocmsg.messenger.desktop.sdk.vpn.VpnProfileValidator.validate(profile).isEmpty());
    }

    @Test
    void stubConnectRequiresTotpFor2fa() {
        var provider = new com.avandocmsg.messenger.desktop.sdk.vpn.StubVpnProvider();
        var profile = new com.avandocmsg.messenger.desktop.sdk.vpn.VpnProfile(
            1, "p2", "2FA VPN", "openvpn", "totp_2fa", "manual",
            "vpn.corp", 1194, "user", true, true, null,
            null, "client\nremote vpn", java.util.Map.of(), null
        );
        var fail = provider.connect("s1", profile, com.avandocmsg.messenger.desktop.sdk.vpn.VpnSecrets.empty());
        assertFalse(fail.connected());
        var ok = provider.connect("s1", profile, new com.avandocmsg.messenger.desktop.sdk.vpn.VpnSecrets("pass", "123456", null));
        assertTrue(ok.connected());
    }

    @Test
    void allProtocolsRegistered() {
        var registry = new com.avandocmsg.messenger.desktop.sdk.vpn.VpnProviderRegistry();
        for (var p : com.avandocmsg.messenger.desktop.sdk.vpn.VpnProtocol.values()) {
            if (p == com.avandocmsg.messenger.desktop.sdk.vpn.VpnProtocol.DISABLED) {
                continue;
            }
            assertTrue(registry.resolve(p) != null);
        }
    }
}

class BrandingPaletteTest {

    @Test
    void resolvesVtbPalette() {
        var snap = new com.avandocmsg.messenger.desktop.sdk.model.BrandingSnapshot(
            null, "vtb", java.util.Map.of(), null, "VTB", true,
            "default", "centered", "default", 1L, null
        );
        var tokens = com.avandocmsg.messenger.desktop.sdk.branding.BrandingPaletteRegistry.resolve(snap, true);
        assertTrue(tokens.accent().startsWith("#"));
    }
}

class WebUiUrlResolverTest {

    @Test
    void derivesQemuWebPort() {
        var url = com.avandocmsg.messenger.desktop.sdk.web.WebUiUrlResolver.defaultFromApiBase("http://127.0.0.1:18080");
        assertEquals("http://127.0.0.1:19088", url);
    }

    @Test
    void buildsMeshJoinUrl() {
        var join = com.avandocmsg.messenger.desktop.sdk.web.WebUiUrlResolver.meshJoinUrl(
            "http://127.0.0.1:19088",
            "chat-1",
            "sess-2",
            "video"
        );
        assertTrue(join.contains("chat=chat-1"));
        assertTrue(join.contains("mesh_session=sess-2"));
        assertTrue(join.contains("mesh_mode=video"));
    }
}

class WsUrlResolverTest {

    @Test
    void derivesQemuWsPort() {
        var url = com.avandocmsg.messenger.desktop.sdk.ws.WsUrlResolver.defaultFromApiBase("http://127.0.0.1:18080");
        assertEquals("ws://127.0.0.1:18082/ws", url);
    }

    @Test
    void prefersServerEntryWsUrl() {
        var entry = new ServerEntry("s1", "Lab", "http://127.0.0.1:18080", "wss://corp.example/ws", false, null, null, false, null);
        assertEquals("wss://corp.example/ws", com.avandocmsg.messenger.desktop.sdk.ws.WsUrlResolver.resolve(entry));
    }
}

class ServerCapabilitiesCacheTest {

    @Test
    void persistsPerServer() throws Exception {
        var dir = Files.createTempDirectory("caps-cache");
        var cache = new com.avandocmsg.messenger.desktop.sdk.capabilities.ServerCapabilitiesCache(dir);
        var sid = new ServerId("srv-1");
        var caps = new com.avandocmsg.messenger.desktop.sdk.model.CapabilitiesResponse(
            java.util.Map.of(), java.util.List.of("chat.send")
        );
        cache.put(sid, caps);
        var reloaded = new com.avandocmsg.messenger.desktop.sdk.capabilities.ServerCapabilitiesCache(dir);
        assertTrue(reloaded.get(sid).capabilities().contains("chat.send"));
    }
}

class FileUploadApiTest {

    @Test
    void multipartUpload() throws Exception {
        try (var server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                .setBody("{\"id\":\"f1\",\"filename\":\"a.txt\",\"mime_type\":\"text/plain\",\"size\":3,\"url\":\"/x\"}")
                .addHeader("Content-Type", "application/json"));
            server.start();
            var api = new KorusApiClient(KorusApiClient.defaultHttpClient(), server.url("/").toString().replaceAll("/$", ""));
            var file = Files.createTempFile("up", ".txt");
            Files.writeString(file, "abc");
            var resp = api.uploadFile("tok", file, "a.txt");
            assertEquals("f1", resp.id());
        }
    }
}

class UpdateServiceTest {

    @Test
    void detectsNewerVersionFromFileManifest() throws Exception {
        var fixture = repoRoot().resolve("specs/031-desktop-java-client/fixtures/update-manifest-stable.json");
        var service = new com.avandocmsg.messenger.desktop.sdk.update.UpdateService(KorusApiClient.defaultHttpClient());
        var result = service.checkForUpdate(
            fixture.toUri().toString(),
            "0.0.1",
            "windows-x64",
            null,
            null
        );
        assertTrue(result.updateAvailable());
        assertEquals("0.0.2", result.latestVersion());
    }

    @Test
    void versionComparer() {
        assertTrue(com.avandocmsg.messenger.desktop.sdk.update.VersionComparer.isNewer("1.2.3", "1.2.2"));
        assertFalse(com.avandocmsg.messenger.desktop.sdk.update.VersionComparer.isNewer("1.2.2", "1.2.3"));
    }

    @Test
    void sha256Verify() {
        var service = new com.avandocmsg.messenger.desktop.sdk.update.UpdateService(KorusApiClient.defaultHttpClient());
        assertTrue(service.verifySha256(new byte[0], "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
    }

    @Test
    void ed25519RoundTrip() throws Exception {
        var kp = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var payload = "manifest-canonical".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var signer = java.security.Signature.getInstance("Ed25519");
        signer.initSign(kp.getPrivate());
        signer.update(payload);
        var sigBytes = signer.sign();
        var verifier = new com.avandocmsg.messenger.desktop.sdk.update.UpdateVerifier();
        assertTrue(verifier.verify(kp.getPublic(), payload, sigBytes));
    }

    @Test
    void aesGcmRoundTrip() {
        var key = new byte[32];
        java.util.Arrays.fill(key, (byte) 7);
        var cipher = new com.avandocmsg.messenger.desktop.sdk.secure.AesGcmCipher(key);
        var plain = "token-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(java.util.Arrays.equals(plain, cipher.decrypt(cipher.encrypt(plain))));
    }

    @Test
    void platformSecureTokenStorePersistsEncrypted() throws Exception {
        var dir = Files.createTempDirectory("sec-store");
        System.setProperty("KORUS_DESKTOP_TEST_MASTER_KEY", "smoke-test-key-32bytes-padding!!");
        try {
            var store = com.avandocmsg.messenger.desktop.sdk.secure.PlatformSecureTokenStore.create(dir);
            store.put("token::s1::alice", "jwt-abc");
            assertEquals("jwt-abc", store.get("token::s1::alice"));
            assertTrue(Files.exists(dir.resolve("tokens.enc")));
            var store2 = com.avandocmsg.messenger.desktop.sdk.secure.PlatformSecureTokenStore.create(dir);
            assertEquals("jwt-abc", store2.get("token::s1::alice"));
        } finally {
            System.clearProperty("KORUS_DESKTOP_TEST_MASTER_KEY");
        }
    }

    @Test
    void wsEventPreviewParsesMessageEnvelope() {
        var json = """
            {"chatId":"chat-abc","senderId":"bob","text":"Привет!","chatTitle":"Общий"}
            """;
        var preview = com.avandocmsg.messenger.desktop.sdk.ws.WsEventPreview.parse(json);
        assertEquals("chat-abc", preview.chatId());
        assertEquals("Общий", preview.title());
        assertEquals("bob: Привет!", preview.body());
        assertFalse(preview.isOwnMessage("alice"));
        assertTrue(preview.isOwnMessage("bob"));
    }

    @Test
    void securitySelfCheckMaximumGrade() throws Exception {
        var dir = Files.createTempDirectory("sec-check");
        var policy = com.avandocmsg.messenger.desktop.sdk.security.SecuritySettings.fstecMaximum();
        Files.writeString(dir.resolve("tokens.enc"), "x");
        if (com.avandocmsg.messenger.desktop.sdk.secure.WindowsDpapiProtector.isAvailable()) {
            Files.write(dir.resolve("master.key.dpapi"), new byte[] { 1 });
        }
        var report = com.avandocmsg.messenger.desktop.sdk.security.SecuritySelfCheck.run(dir, policy);
        assertTrue(report.score() >= 7, report.grade() + " " + report.score());
        assertTrue(report.grade().startsWith("A"), report.grade());
    }

    @Test
    void mlsAesGcmRoundTrip() {
        var key = MlsSessionKeyDeriver.derive("demo-mls-session", "chat-general");
        var cipher = new MlsAesGcmCipher();
        var enc = cipher.encrypt("secret payload", key, "chat-general", 1L);
        assertTrue(MlsWireCodec.looksEncrypted(enc));
        assertEquals("secret payload", cipher.decrypt(enc, key, "chat-general", 1L));
    }

    @Test
    void installerStagesBytes() throws Exception {
        var dir = Files.createTempDirectory("upd");
        var path = new com.avandocmsg.messenger.desktop.sdk.update.UpdateInstaller()
            .stage("msi".getBytes(), dir, "korus.msi");
        assertTrue(Files.exists(path));
    }

    private static Path repoRoot() {
        var p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("specs/031-desktop-java-client"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("repo root not found");
        }
        return p;
    }
}

class MultiServerDemoTest {

    @Test
    void demoStoreHasTwoServers() {
        var session = new DemoDesktopSession(new DemoDataStore());
        assertEquals(2, session.servers().size());
    }
}
