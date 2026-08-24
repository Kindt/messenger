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
        var response = clientFor(entry).login(username, password);
        storeTokens(serverId, username, response);
        return response.accessToken();
    }

    /** Returns a valid access token, refreshing when near expiry if refresh_token was stored. */
    public String ensureAccessToken(ServerId serverId, String username) throws IOException {
        var entry = findServer(serverId);
        var token = token(serverId, username);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("no saved token for " + username + "@" + serverId.value());
        }
        var expiresKey = TokenKeys.tokenExpiresKey(serverId.value(), username);
        var expiresRaw = tokenStore.get(expiresKey);
        if (expiresRaw != null && !expiresRaw.isBlank()) {
            var expiresAt = Long.parseLong(expiresRaw);
            if (Instant.now().getEpochSecond() >= expiresAt - 60) {
                return refreshAccessToken(serverId, username, entry);
            }
        }
        return token;
    }

    private String refreshAccessToken(ServerId serverId, String username, ServerEntry entry) throws IOException {
        var refreshKey = TokenKeys.refreshTokenKey(serverId.value(), username);
        var refresh = tokenStore.get(refreshKey);
        if (refresh == null || refresh.isBlank()) {
            return token(serverId, username);
        }
        var response = clientFor(entry).refresh(refresh);
        storeTokens(serverId, username, response);
        return response.accessToken();
    }

    private void storeTokens(ServerId serverId, String username, com.avandocmsg.messenger.desktop.sdk.model.LoginResponse response) {
        tokenStore.put(TokenKeys.tokenKey(serverId.value(), username), response.accessToken());
        if (response.refreshToken() != null && !response.refreshToken().isBlank()) {
            tokenStore.put(TokenKeys.refreshTokenKey(serverId.value(), username), response.refreshToken());
        }
        if (response.expiresInOrZero() > 0) {
            var expiresAt = Instant.now().plusSeconds(response.expiresInOrZero()).getEpochSecond();
            tokenStore.put(TokenKeys.tokenExpiresKey(serverId.value(), username), Long.toString(expiresAt));
        }
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
