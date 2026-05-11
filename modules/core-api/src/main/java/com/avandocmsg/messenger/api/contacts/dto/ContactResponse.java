package com.avandocmsg.messenger.api.contacts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ContactResponse(
    String id,
    String username,
    @JsonProperty("display_name") String displayName,
    String phone,
    @JsonProperty("added_at") Instant addedAt
) {}
