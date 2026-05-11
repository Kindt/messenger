package com.avandocmsg.messenger.api.contacts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ImportContactsRequest(
    @JsonProperty("phone_hashes") List<String> phoneHashes
) {}
