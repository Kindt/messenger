package com.avandocmsg.messenger.api.devices.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RegisterDeviceRequest(
    @JsonProperty("device_name") String deviceName,
    @JsonProperty("push_provider") String pushProvider,
    @JsonProperty("push_token") String pushToken
) {}
