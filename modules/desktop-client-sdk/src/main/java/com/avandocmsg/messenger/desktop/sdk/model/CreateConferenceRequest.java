package com.avandocmsg.messenger.desktop.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateConferenceRequest(
    String title,
    @JsonProperty("member_ids") List<String> memberIds
) {
    public CreateConferenceRequest(String title) {
        this(title, null);
    }
}
