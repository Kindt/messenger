package com.avandocmsg.messenger.api.devices.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record DeviceResponse(
    String id,
    @JsonProperty("device_name") String deviceName,
    @JsonProperty("push_provider") String pushProvider,
    @JsonProperty("push_active") boolean pushActive,
    @JsonProperty("created_at") Instant createdAt
) {}
