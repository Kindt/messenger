package com.avandocmsg.messenger.desktop.sdk.ws;

import com.avandocmsg.messenger.desktop.sdk.identity.ServerId;
import com.avandocmsg.messenger.desktop.sdk.model.ServerEntry;
import com.avandocmsg.messenger.desktop.sdk.security.HttpClientFactory;
import com.avandocmsg.messenger.desktop.sdk.security.SecuritySettings;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.OkHttpClient;

/** One WebSocket per connected server (multi-server unified inbox). */
public final class MultiServerWsHub implements AutoCloseable {

    private final SecuritySettings securitySettings;
    private final Map<String, WsSession> sessions = new ConcurrentHashMap<>();
    private volatile WsEventHandler globalHandler = WsEventHandler.NOOP;

    public MultiServerWsHub(SecuritySettings securitySettings) {
        this.securitySettings = securitySettings == null ? new SecuritySettings() : securitySettings;
    }

    /** @deprecated Prefer {@link #MultiServerWsHub(SecuritySettings)} — WS uses per-server TLS from {@link ServerEntry}. */
    @Deprecated
    public MultiServerWsHub(OkHttpClient http) {
        this(new SecuritySettings());
    }

    public void setGlobalHandler(WsEventHandler handler) {
        this.globalHandler = handler == null ? WsEventHandler.NOOP : handler;
    }

    public void connect(ServerId serverId, ServerEntry entry, String token) {
        disconnect(serverId);
        var base = WsUrlResolver.resolve(entry);
        var url = WsUrlResolver.withToken(base, token);
        var http = HttpClientFactory.forServer(entry, securitySettings);
        var session = new WsSession();
        WsEventHandler handler = globalHandler;
        session.connect(http, url, json -> {
            if (WsEventHandler.shouldRefreshTimeline(json)) {
                handler.onRawMessage(json);
            }
        });
        sessions.put(serverId.value(), session);
    }

    public void disconnect(ServerId serverId) {
        var session = sessions.remove(serverId.value());
        if (session != null) {
            session.close();
        }
    }

    public void disconnectAll() {
        for (var id : sessions.keySet().toArray(String[]::new)) {
            disconnect(new ServerId(id));
        }
    }

    public boolean isConnected(ServerId serverId) {
        return sessions.containsKey(serverId.value());
    }

    @Override
    public void close() {
        disconnectAll();
    }
}
