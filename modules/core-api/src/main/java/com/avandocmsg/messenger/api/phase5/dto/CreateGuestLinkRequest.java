package com.avandocmsg.messenger.api.phase5.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateGuestLinkRequest(@JsonProperty("waiting_room") Boolean waitingRoom) {}
