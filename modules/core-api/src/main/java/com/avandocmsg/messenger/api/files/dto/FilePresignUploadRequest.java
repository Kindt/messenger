package com.avandocmsg.messenger.api.files.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FilePresignUploadRequest(
    String filename,
    @JsonProperty("mime_type") String mimeType,
    long size
) {}
