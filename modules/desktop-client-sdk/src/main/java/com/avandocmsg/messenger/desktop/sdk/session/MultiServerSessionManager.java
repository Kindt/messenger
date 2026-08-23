package com.avandocmsg.messenger.desktop.sdk.session;

import com.avandocmsg.messenger.desktop.sdk.api.KorusApiClient;
import com.avandocmsg.messenger.desktop.sdk.identity.ServerId;
import com.avandocmsg.messenger.desktop.sdk.model.ServerEntry;
import com.avandocmsg.messenger.desktop.sdk.secure.SecureTokenStore;
import com.avandocmsg.messenger.desktop.sdk.secure.TokenKeys;
import com.avandocmsg.messenger.desktop.sdk.storage.ServerRegistry;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.avandocmsg.messenger.desktop.sdk.security.HttpClientFactory;
import com.avandocmsg.messenger.desktop.sdk.security.SecuritySettings;
import okhttp3.OkHttpClient;

public final class MultiServerSessionManager {

    private final ServerRegistry registry;
    private final SecureTokenStore tokenStore;
    private final OkHttpClient defaultHttp;
    private final SecuritySettings securitySettings;
    private final Map<ServerId, KorusApiClient> clients = new HashMap<>();

    public MultiServerSessionManager(
        ServerRegistry registry,
        SecureTokenStore tokenStore,
        OkHttpClient http,
        SecuritySettings securitySettings
    ) {
        this.registry = Objects.requireNonNull(registry);
        this.tokenStore = Objects.requireNonNull(tokenStore);
        this.defaultHttp = http == null ? HttpClientFactory.defaultClient() : http;
        this.securitySettings = securitySettings == null ? new SecuritySettings() : securitySettings;
    }

    public MultiServerSessionManager(ServerRegistry registry, SecureTokenStore tokenStore, OkHttpClient http) {
        this(registry, tokenStore, http, new SecuritySettings());
    }

    public ServerEntry registerServer(ServerEntry entry) throws IOException {
        var client = clientFor(entry);
        client.health();
        var updated = entry.lastHealthOkAt() == null
            ? new ServerEntry(
                entry.serverId(),
                entry.displayName(),
                entry.apiBaseUrl(),
                entry.wsPublicUrl(),
                entry.trustSelfSigned(),
                entry.pinnedCertSha256(),
                entry.colorToken(),
                entry.paused(),
                Instant.now().toString()
            )
            : entry;
        registry.upsert(updated);
        return updated;
    }

    public KorusApiClient clientFor(ServerEntry entry) {
        var id = new ServerId(entry.serverId());
        return clients.computeIfAbsent(id, k -> new KorusApiClient(HttpClientFactory.forServer(entry, securitySettings), entry.apiBaseUrl()));
    }

    public KorusApiClient clientFor(ServerId serverId) throws IOException {
        var entry = findServer(serverId);
        return clientFor(entry);
    }

    public String login(ServerId serverId, String username, String password) throws IOException {
        return login(serverId, username, password, null);
    }

    public String login(ServerId serverId, String username, String password, String vpnTotpCode) throws IOException {
        var entry = findServer(serverId);
        var token = clientFor(entry).login(username, password).accessToken();
        tokenStore.put(TokenKeys.tokenKey(serverId.value(), username), token);
        return token;
    }

    public ServerEntry findServerEntry(ServerId serverId) throws IOException {
        return findServer(serverId);
    }

    public String token(ServerId serverId, String username) {
        return tokenStore.get(TokenKeys.tokenKey(serverId.value(), username));
    }

    public void clearTokens() {
        tokenStore.clear();
        clients.clear();
    }

    public List<ServerEntry> activeServers() throws IOException {
        return registry.load().servers().stream().filter(s -> !s.paused()).toList();
    }

    public ServerRegistry registry() {
        return registry;
    }

    private ServerEntry findServer(ServerId serverId) throws IOException {
        return registry.load().servers().stream()
            .filter(s -> s.serverId().equals(serverId.value()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("server not found: " + serverId.value()));
    }
}
