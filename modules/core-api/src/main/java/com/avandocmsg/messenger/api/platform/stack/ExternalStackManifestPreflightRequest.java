package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ExternalStackManifestPreflightRequest(
    @JsonProperty("manifests") List<ComponentBackendManifest> manifests
) {
    public ExternalStackManifestPreflightRequest {
        manifests = manifests == null ? List.of() : List.copyOf(manifests);
    }
}
