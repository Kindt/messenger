package com.avandocmsg.messenger.api.messages.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlaintextPreviewResponse(@JsonProperty("plaintext") String plaintext) {}
