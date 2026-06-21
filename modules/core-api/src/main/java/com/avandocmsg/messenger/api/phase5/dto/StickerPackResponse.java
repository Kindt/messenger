package com.avandocmsg.messenger.api.phase5.dto;

import com.avandocmsg.messenger.api.phase5.Phase5AdrRepository;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record StickerPackResponse(
    @JsonProperty("pack_id") String packId,
    @JsonProperty("org_id") String orgId,
    String name,
    @JsonProperty("created_at") Instant createdAt
) {
    public static StickerPackResponse from(Phase5AdrRepository.StickerPackRow row) {
        return new StickerPackResponse(
            row.id().toString(),
            row.orgId().toString(),
            row.name(),
            row.createdAt());
    }
}
