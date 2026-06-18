package com.avandocmsg.messenger.api.chats.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ChatResponse(
    String id,
    String title,
    String type,
    @JsonProperty("owner_id") String ownerId,
    @JsonProperty("member_count") int memberCount,
    boolean muted,
    @JsonProperty("personal_filter_active") boolean personalFilterActive,
    @JsonProperty("ttl_seconds") Integer ttlSeconds,
    @JsonProperty("created_at") Instant createdAt,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("archived") Boolean archived,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("folder_tag") String folderTag,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("channel_post_policy") String channelPostPolicy
) {
    public ChatResponse(
        String id,
        String title,
        String type,
        String ownerId,
        int memberCount,
        boolean muted,
        boolean personalFilterActive,
        Integer ttlSeconds,
        Instant createdAt
    ) {
        this(id, title, type, ownerId, memberCount, muted, personalFilterActive, ttlSeconds, createdAt,
            null, null, null);
    }
}
