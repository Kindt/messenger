package com.avandocmsg.messenger.api.crypto.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UploadKeyPackageRequest(
    @JsonProperty("public_key") String publicKeyBase64,
    @JsonProperty("signature_key") String signatureKeyBase64,
    @JsonProperty("cipher_suite") String cipherSuite,
    @JsonProperty("protocol_version") String protocolVersion
) {}
