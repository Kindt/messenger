package com.avandocmsg.messenger.desktop.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ServerRegistryDocument(int schemaVersion, List<ServerEntry> servers) {
    public ServerRegistryDocument() {
        this(1, List.of());
    }
}
