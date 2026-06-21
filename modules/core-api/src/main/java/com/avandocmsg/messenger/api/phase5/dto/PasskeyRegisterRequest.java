package com.avandocmsg.messenger.api.phase5.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PasskeyRegisterRequest(
    @JsonProperty("credential_id") String credentialId,
    @JsonProperty("public_key") String publicKey
) {}
