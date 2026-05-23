package com.avandocmsg.messenger.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChunkEntry(
    @JsonProperty("part_name") String partName,
    @JsonProperty("index") int index,
    @JsonProperty("size_bytes") long sizeBytes,
    @JsonProperty("sha256") String sha256
) {}
