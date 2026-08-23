package com.avandocmsg.messenger.desktop.sdk.update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateSignatureDto(
    String algorithm,
    @com.fasterxml.jackson.annotation.JsonProperty("public_key_id") String publicKeyId,
    String value
) {}
