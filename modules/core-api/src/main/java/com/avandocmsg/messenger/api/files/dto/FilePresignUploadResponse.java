package com.avandocmsg.messenger.api.files.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FilePresignUploadResponse(
    @JsonProperty("file_id") String fileId,
    @JsonProperty("upload_url") String uploadUrl,
    @JsonProperty("download_url") String downloadUrl,
    @JsonProperty("expires_in_seconds") int expiresInSeconds
) {}
