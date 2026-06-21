package com.avandocmsg.messenger.api.phase5.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record GuestWaitingLinkResponse(
    @JsonProperty("link_id") String linkId,
    @JsonProperty("waiting_room") boolean waitingRoom,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("admitted") boolean admitted
) {}
