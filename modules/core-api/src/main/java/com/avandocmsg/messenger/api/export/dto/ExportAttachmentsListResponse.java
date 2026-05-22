package com.avandocmsg.messenger.api.export.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ExportAttachmentsListResponse(
    @JsonProperty("zip_bundle") boolean zipBundle,
    @JsonProperty("total_count") int totalCount,
    @JsonProperty("file_count") int fileCount,
    @JsonProperty("offset") int offset,
    @JsonProperty("limit") int limit,
    @JsonProperty("files") List<ExportAttachmentListItem> files
) {
    public record ExportAttachmentListItem(
        @JsonProperty("file_id") String fileId,
        @JsonProperty("filename") String filename,
        @JsonProperty("mime_type") String mimeType,
        @JsonProperty("size_bytes") long sizeBytes,
        @JsonProperty("sha256") String sha256,
        @JsonProperty("zip_path") String zipPath
    ) {}
}
