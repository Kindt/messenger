package com.avandocmsg.messenger.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record DeepArchiveManifest(
    @JsonProperty("message_id") String messageId,
    @JsonProperty("chunk_count") int chunkCount,
    @JsonProperty("chunks") List<ChunkEntry> chunks,
    @JsonProperty("total_size_bytes") long totalSizeBytes,
    @JsonProperty("sha256") String sha256
) {}
