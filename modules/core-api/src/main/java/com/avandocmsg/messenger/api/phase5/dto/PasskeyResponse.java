package com.avandocmsg.messenger.api.phase5.dto;

import com.avandocmsg.messenger.api.phase5.Phase5AdrRepository;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record PasskeyResponse(
    @JsonProperty("credential_id") String credentialId,
    @JsonProperty("created_at") Instant createdAt
) {
    public static PasskeyResponse created(String id, String credentialId) {
        return new PasskeyResponse(credentialId, Instant.now());
    }

    public static PasskeyResponse from(Phase5AdrRepository.PasskeyRow row) {
        return new PasskeyResponse(row.credentialId(), row.createdAt());
    }
}
