package com.avandocmsg.messenger.desktop.sdk.storage;

import com.avandocmsg.messenger.desktop.sdk.json.JsonSupport;
import com.avandocmsg.messenger.desktop.sdk.model.ProfileSettings;
import java.io.IOException;
import java.nio.file.Files;

public final class ProfileSettingsStore {

    private final ProfileStore profileStore;

    public ProfileSettingsStore(ProfileStore profileStore) {
        this.profileStore = profileStore;
    }

    public ProfileSettings read(String profileId) throws IOException {
        var path = profileStore.settingsPath(profileId);
        if (!Files.exists(path)) {
            return new ProfileSettings();
        }
        return JsonSupport.mapper().readValue(Files.readString(path), ProfileSettings.class);
    }

    public void write(String profileId, ProfileSettings settings) throws IOException {
        Files.writeString(profileStore.settingsPath(profileId), JsonSupport.mapper().writeValueAsString(settings));
    }
}
