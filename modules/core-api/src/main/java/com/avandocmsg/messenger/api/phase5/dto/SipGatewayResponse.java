package com.avandocmsg.messenger.api.phase5.dto;

import com.avandocmsg.messenger.api.phase5.Phase5AdrRepository;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record SipGatewayResponse(
    boolean enabled,
    @JsonProperty("gateway_uri") String gatewayUri,
    @JsonProperty("h323_enabled") boolean h323Enabled,
    @JsonProperty("updated_at") Instant updatedAt
) {
    public static SipGatewayResponse disabled() {
        return new SipGatewayResponse(false, null, false, null);
    }

    public static SipGatewayResponse from(Phase5AdrRepository.SipGatewayRow row) {
        return new SipGatewayResponse(row.enabled(), row.gatewayUri(), row.h323Enabled(), row.updatedAt());
    }
}
