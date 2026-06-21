package com.avandocmsg.messenger.api.phase5.dto;

import com.avandocmsg.messenger.api.phase5.Phase5AdrRepository;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record BreakoutRoomResponse(
    @JsonProperty("room_id") String roomId,
    String name,
    @JsonProperty("livekit_room") String livekitRoom,
    @JsonProperty("created_at") Instant createdAt
) {
    public static BreakoutRoomResponse created(String id, String name) {
        return new BreakoutRoomResponse(id, name, "breakout-" + id.substring(0, 8), Instant.now());
    }

    public static BreakoutRoomResponse from(Phase5AdrRepository.BreakoutRow row) {
        return new BreakoutRoomResponse(
            row.id().toString(),
            row.name(),
            row.livekitRoom(),
            row.createdAt());
    }
}
