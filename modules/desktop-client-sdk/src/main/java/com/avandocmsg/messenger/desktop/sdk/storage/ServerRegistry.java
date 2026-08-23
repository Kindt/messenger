package com.avandocmsg.messenger.desktop.sdk.storage;

import com.avandocmsg.messenger.desktop.sdk.json.JsonSupport;
import com.avandocmsg.messenger.desktop.sdk.model.ServerEntry;
import com.avandocmsg.messenger.desktop.sdk.model.ServerRegistryDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public final class ServerRegistry {

    private final Path profileDir;
    private final Path path;

    public ServerRegistry(Path profileDir) {
        this.profileDir = profileDir;
        this.path = profileDir.resolve("servers.json");
    }

    public ServerRegistryDocument load() throws IOException {
        if (!Files.exists(path)) {
            return new ServerRegistryDocument();
        }
        return JsonSupport.mapper().readValue(Files.readString(path), ServerRegistryDocument.class);
    }

    public void save(ServerRegistryDocument document) throws IOException {
        Files.createDirectories(profileDir);
        Files.writeString(path, JsonSupport.mapper().writeValueAsString(document));
    }

    public ServerRegistryDocument upsert(ServerEntry entry) throws IOException {
        var current = load();
        var servers = new ArrayList<>(current.servers());
        servers.removeIf(s -> s.serverId().equals(entry.serverId()));
        servers.add(entry);
        var updated = new ServerRegistryDocument(1, servers);
        save(updated);
        return updated;
    }

    public ServerRegistryDocument remove(String serverId) throws IOException {
        var current = load();
        var servers = current.servers().stream().filter(s -> !s.serverId().equals(serverId)).toList();
        var updated = new ServerRegistryDocument(1, servers);
        save(updated);
        return updated;
    }
}
