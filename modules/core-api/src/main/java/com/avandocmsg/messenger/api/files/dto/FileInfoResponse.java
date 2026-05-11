package com.avandocmsg.messenger.api.files.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "File metadata")
public record FileInfoResponse(
    @Schema(description = "File ID") String id,
    @Schema(description = "Original filename") String filename,
    @Schema(description = "MIME type") @JsonProperty("mime_type") String mimeType,
    @Schema(description = "File size in bytes") long size,
    @Schema(description = "Uploader user ID") @JsonProperty("uploaded_by") String uploadedBy,
    @Schema(description = "Download URL") String url
) {}
