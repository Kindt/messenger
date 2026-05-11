package com.avandocmsg.messenger.api.chats.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CreateChatRequest(
    String type,
    String title,
    @JsonProperty("member_ids") List<String> memberIds
) {}
