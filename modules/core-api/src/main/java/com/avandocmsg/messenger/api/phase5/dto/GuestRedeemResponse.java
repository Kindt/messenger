package com.avandocmsg.messenger.api.phase5.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GuestRedeemResponse(
    @JsonProperty("conference_id") String conferenceId,
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("waiting_room") boolean waitingRoom,
    String status
) {}
