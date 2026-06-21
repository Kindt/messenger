package com.avandocmsg.messenger.api.phase5.dto;

import com.avandocmsg.messenger.api.phase5.Phase5AdrRepository;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record GifSearchResponse(
    @JsonProperty("gif_id") String gifId,
    @JsonProperty("query_key") String queryKey,
    @JsonProperty("preview_url") String previewUrl,
    @JsonProperty("gif_url") String gifUrl,
    @JsonProperty("created_at") Instant createdAt
) {
    public static GifSearchResponse from(Phase5AdrRepository.GifRow row) {
        return new GifSearchResponse(
            row.id().toString(),
            row.queryKey(),
            row.previewUrl(),
            row.gifUrl(),
            row.createdAt());
    }
}
