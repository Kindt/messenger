package com.avandocmsg.messenger.desktop.sdk.storage;

import com.avandocmsg.messenger.desktop.sdk.json.JsonSupport;
import com.avandocmsg.messenger.desktop.sdk.model.LocalProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ProfileStore {

    private final Path profilesRoot;

    public ProfileStore(Path appRoot) {
        this.profilesRoot = appRoot.resolve("profiles");
    }

    public List<LocalProfile> listProfiles() throws IOException {
        if (!Files.isDirectory(profilesRoot)) {
            return List.of();
        }
        var out = new ArrayList<LocalProfile>();
        try (var stream = Files.list(profilesRoot)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                try {
                    out.add(readProfile(dir));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
        return List.copyOf(out);
    }

    public LocalProfile createProfile(String displayName) throws IOException {
        var id = UUID.randomUUID().toString();
        var now = Instant.now().toString();
        var profile = new LocalProfile(id, displayName.trim(), now, now);
        writeProfile(profile);
        return profile;
    }

    public LocalProfile readProfile(String profileId) throws IOException {
        return readProfile(profileDir(profileId));
    }

    public void touchProfile(String profileId) throws IOException {
        var profile = readProfile(profileId);
        writeProfile(new LocalProfile(
            profile.schemaVersion(),
            profile.profileId(),
            profile.displayName(),
            profile.avatarPath(),
            profile.createdAt(),
            Instant.now().toString(),
            profile.settings()
        ));
    }

    public Path profileRoot(String profileId) {
        return profileDir(profileId);
    }

    public Path settingsPath(String profileId) {
        return profileDir(profileId).resolve("settings.json");
    }

    public Path stateDir(String profileId) {
        return profileDir(profileId).resolve("state");
    }

    private Path profileDir(String profileId) {
        return profilesRoot.resolve(profileId);
    }

    private LocalProfile readProfile(Path dir) throws IOException {
        var text = Files.readString(dir.resolve("profile.json"));
        return JsonSupport.mapper().readValue(text, LocalProfile.class);
    }

    private void writeProfile(LocalProfile profile) throws IOException {
        var dir = profileDir(profile.profileId());
        Files.createDirectories(dir);
        Files.createDirectories(dir.resolve("state"));
        Files.writeString(dir.resolve("profile.json"), JsonSupport.mapper().writeValueAsString(profile));
        var settingsPath = dir.resolve("settings.json");
        if (!Files.exists(settingsPath)) {
            Files.writeString(settingsPath, JsonSupport.mapper().writeValueAsString(profile.settings()));
        }
    }
}
