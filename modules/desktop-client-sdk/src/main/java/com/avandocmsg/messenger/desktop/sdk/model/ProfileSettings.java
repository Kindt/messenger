package com.avandocmsg.messenger.desktop.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfileSettings(
    String locale,
    String theme,
    String attachmentsRoot,
    String updateChannel,
    String updatePolicy,
    String updateFeedUrl
) {
    public ProfileSettings() {
        this("ru", "system", null, "stable", "notify", null);
    }
}
