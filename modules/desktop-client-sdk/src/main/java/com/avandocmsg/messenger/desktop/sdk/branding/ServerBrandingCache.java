package com.avandocmsg.messenger.desktop.sdk.branding;

import com.avandocmsg.messenger.desktop.sdk.json.JsonSupport;
import com.avandocmsg.messenger.desktop.sdk.model.BrandingSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class ServerBrandingCache {

    private final Path dir;

    public ServerBrandingCache(Path stateDir) {
        this.dir = stateDir.resolve("branding-cache");
    }

    public void put(String serverId, BrandingSnapshot snapshot) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(serverId + ".json"), JsonSupport.mapper().writeValueAsString(snapshot));
    }

    public Optional<BrandingSnapshot> get(String serverId) throws IOException {
        var path = dir.resolve(serverId + ".json");
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        return Optional.of(JsonSupport.mapper().readValue(Files.readString(path), BrandingSnapshot.class));
    }
}
