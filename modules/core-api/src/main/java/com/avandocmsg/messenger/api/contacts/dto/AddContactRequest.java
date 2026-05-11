package com.avandocmsg.messenger.api.contacts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AddContactRequest(
    @JsonProperty("user_id") String userId
) {}
