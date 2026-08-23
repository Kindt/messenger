package com.avandocmsg.messenger.desktop.sdk.security;

import com.avandocmsg.messenger.desktop.sdk.json.JsonSupport;
import com.avandocmsg.messenger.desktop.sdk.storage.ProfileStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SecuritySettingsStore {

    private final Path file;

    public SecuritySettingsStore(ProfileStore profileStore, String profileId) throws IOException {
        var dir = profileStore.stateDir(profileId);
        Files.createDirectories(dir);
        this.file = dir.resolve("security-settings.json");
    }

    public SecuritySettings read() throws IOException {
        if (!Files.exists(file)) {
            return SecuritySettings.fstecMaximum();
        }
        var json = Files.readString(file);
        var settings = JsonSupport.mapper().readValue(json, SecuritySettings.class);
        if (!json.contains("osNotificationsEnabled")) {
            return new SecuritySettings(
                settings.tlsPinningRequired(),
                settings.idleLockMinutes(),
                settings.clipboardAutoClearSec(),
                settings.clearTokensOnExit(),
                settings.auditLogEnabled(),
                settings.soundNotifications(),
                true,
                settings.blockScreenshots(),
                settings.requireSecureUpdates()
            );
        }
        return settings;
    }

    public void write(SecuritySettings settings) throws IOException {
        Files.writeString(file, JsonSupport.mapper().writeValueAsString(settings));
    }
}
