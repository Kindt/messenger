package com.avandocmsg.messenger.desktop.sdk.capabilities;

import com.avandocmsg.messenger.desktop.sdk.identity.ServerId;
import com.avandocmsg.messenger.desktop.sdk.json.JsonSupport;
import com.avandocmsg.messenger.desktop.sdk.model.CapabilitiesResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per-server capabilities snapshot (refreshed on connect). */
public final class ServerCapabilitiesCache {

    private final Path cacheFile;
    private final ConcurrentHashMap<String, CapabilitiesResponse> byServer = new ConcurrentHashMap<>();

    public ServerCapabilitiesCache(Path stateDir) throws IOException {
        Files.createDirectories(stateDir);
        this.cacheFile = stateDir.resolve("capabilities-cache.json");
        load();
    }

    public void put(ServerId serverId, CapabilitiesResponse caps) throws IOException {
        byServer.put(serverId.value(), caps == null ? new CapabilitiesResponse() : caps);
        persist();
    }

    public CapabilitiesResponse get(ServerId serverId) {
        return byServer.getOrDefault(serverId.value(), new CapabilitiesResponse());
    }

    public void clear() throws IOException {
        byServer.clear();
        persist();
    }

    private void load() throws IOException {
        if (!Files.isRegularFile(cacheFile)) {
            return;
        }
        var map = JsonSupport.mapper().readValue(
            Files.readString(cacheFile),
            new TypeReference<Map<String, CapabilitiesResponse>>() {}
        );
        byServer.putAll(map);
    }

    private void persist() throws IOException {
        Files.writeString(cacheFile, JsonSupport.mapper().writeValueAsString(byServer));
    }
}
