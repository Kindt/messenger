package com.avandocmsg.messenger.api.crypto.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Base64;

public record KeyPackageResponse(
    String id,
    @JsonProperty("user_id") String userId,
    @JsonProperty("public_key") String publicKeyBase64,
    @JsonProperty("signature_key") String signatureKeyBase64,
    @JsonProperty("cipher_suite") String cipherSuite,
    @JsonProperty("protocol_version") String protocolVersion,
    @JsonProperty("created_at") Instant createdAt
) {
    public KeyPackageResponse(String id, String userId, byte[] publicKey, byte[] signatureKey,
                              String cipherSuite, String protocolVersion, Instant createdAt) {
        this(id, userId,
            publicKey != null ? Base64.getEncoder().encodeToString(publicKey) : null,
            signatureKey != null ? Base64.getEncoder().encodeToString(signatureKey) : null,
            cipherSuite, protocolVersion, createdAt);
    }
}
