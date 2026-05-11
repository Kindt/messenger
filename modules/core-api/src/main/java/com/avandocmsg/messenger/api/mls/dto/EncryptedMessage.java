package com.avandocmsg.messenger.api.mls.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Base64;

public record EncryptedMessage(
    @JsonProperty("ciphertext_base64") String ciphertextBase64,
    @JsonProperty("nonce_base64") String nonceBase64,
    @JsonProperty("session_id") String sessionId,
    long epoch
) {
    public EncryptedMessage(byte[] fullCiphertext, String sessionId, long epoch) {
        this(
            fullCiphertext != null && fullCiphertext.length > 12
                ? Base64.getEncoder().encodeToString(java.util.Arrays.copyOfRange(fullCiphertext, 12, fullCiphertext.length))
                : null,
            fullCiphertext != null && fullCiphertext.length >= 12
                ? Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(fullCiphertext, 12))
                : null,
            sessionId, epoch);
    }
}
