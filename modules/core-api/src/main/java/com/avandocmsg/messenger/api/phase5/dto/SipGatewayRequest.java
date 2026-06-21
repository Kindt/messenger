package com.avandocmsg.messenger.api.phase5.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SipGatewayRequest(
    Boolean enabled,
    @JsonProperty("gateway_uri") String gatewayUri,
    @JsonProperty("h323_enabled") Boolean h323Enabled
) {}
