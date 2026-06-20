package com.avandocmsg.messenger.api.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlatformModuleOverrideRequest(
    @JsonProperty("disabled") boolean disabled,
    @JsonProperty("override_reason") String overrideReason,
    @JsonProperty("force_enabled") Boolean forceEnabled
) {}
