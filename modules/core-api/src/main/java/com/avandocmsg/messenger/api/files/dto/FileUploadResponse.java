package com.avandocmsg.messenger.api.files.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Uploaded file info")
public record FileUploadResponse(
    @Schema(description = "File ID") String id,
    @Schema(description = "Original filename") String filename,
    @Schema(description = "MIME type") @JsonProperty("mime_type") String mimeType,
    @Schema(description = "File size in bytes") long size,
    @Schema(description = "Download URL") String url
) {}
