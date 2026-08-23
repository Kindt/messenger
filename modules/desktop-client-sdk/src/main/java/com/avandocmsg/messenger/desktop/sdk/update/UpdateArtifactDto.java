package com.avandocmsg.messenger.desktop.sdk.update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateArtifactDto(
    String platform,
    String url,
    String sha256,
    @JsonProperty("size_bytes") long sizeBytes
) {}
