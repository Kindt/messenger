package com.avandocmsg.messenger.api.phase5.dto;

import com.avandocmsg.messenger.api.phase5.Phase5AdrRepository;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record WhiteboardResponse(
    @JsonProperty("whiteboard_id") String whiteboardId,
    @JsonProperty("chat_id") String chatId,
    String title,
    @JsonProperty("snapshot_json") String snapshotJson,
    @JsonProperty("updated_at") Instant updatedAt
) {
    public static WhiteboardResponse from(Phase5AdrRepository.WhiteboardRow row) {
        return new WhiteboardResponse(
            row.id().toString(),
            row.chatId().toString(),
            row.title(),
            row.snapshotJson(),
            row.updatedAt());
    }
}
