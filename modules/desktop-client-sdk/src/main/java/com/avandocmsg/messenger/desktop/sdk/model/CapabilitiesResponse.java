package com.avandocmsg.messenger.desktop.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CapabilitiesResponse(Map<String, AddonCapability> addons, List<String> capabilities) {
    public CapabilitiesResponse() {
        this(Map.of(), List.of());
    }
}
