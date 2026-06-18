package com.avandocmsg.messenger.api.chats.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatArchiveRequest(
    @JsonProperty("archived") boolean archived
) {}
