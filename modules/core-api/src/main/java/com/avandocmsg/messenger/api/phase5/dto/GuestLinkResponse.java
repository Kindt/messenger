package com.avandocmsg.messenger.api.phase5.dto;

import com.avandocmsg.messenger.api.phase5.Phase5AdrRepository;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record GuestLinkResponse(
    @JsonProperty("link_id") String linkId,
    @JsonProperty("guest_token") String guestToken,
    @JsonProperty("waiting_room") boolean waitingRoom,
    @JsonProperty("expires_at") Instant expiresAt
) {
    public static GuestLinkResponse from(Phase5AdrRepository.GuestLinkRow row) {
        return new GuestLinkResponse(
            row.id().toString(),
            row.guestToken(),
            row.waitingRoom(),
            row.expiresAt());
    }
}
