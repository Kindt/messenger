package com.avandocmsg.messenger.common.hotplug;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HotPlugHeartbeatEvent(
    @JsonProperty("serviceId") String serviceId,
    @JsonProperty("state") String state,
    @JsonProperty("uptimeMs") long uptimeMs
) {
}
