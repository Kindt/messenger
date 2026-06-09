package com.avandocmsg.messenger.api.conference.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CreateConferenceRequest(
    String title,
    @JsonProperty("member_ids") List<String> memberIds
) {
    public CreateConferenceRequest(String title) {
        this(title, null);
    }
}
