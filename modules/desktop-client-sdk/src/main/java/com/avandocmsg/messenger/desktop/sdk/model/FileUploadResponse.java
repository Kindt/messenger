package com.avandocmsg.messenger.desktop.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FileUploadResponse(
    String id,
    String filename,
    @JsonProperty("mime_type") String mimeType,
    long size,
    String url
) {}
