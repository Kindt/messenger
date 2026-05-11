package com.avandocmsg.messenger.api.chats.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AddMemberRequest(
    @JsonProperty("user_id") String userId
) {}
