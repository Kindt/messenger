package com.avandocmsg.messenger.api.devices.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DeviceListResponse(
    @JsonProperty("devices") List<DeviceResponse> devices
) {}
