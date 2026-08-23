package com.avandocmsg.messenger.desktop.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ServerEntry(
    String serverId,
    String displayName,
    String apiBaseUrl,
    String wsPublicUrl,
    boolean trustSelfSigned,
    String pinnedCertSha256,
    String colorToken,
    boolean paused,
    String lastHealthOkAt
) {
    public ServerEntry(String serverId, String displayName, String apiBaseUrl) {
        this(serverId, displayName, apiBaseUrl, null, false, null, null, false, null);
    }
}
