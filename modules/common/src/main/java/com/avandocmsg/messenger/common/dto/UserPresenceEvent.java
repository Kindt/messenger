package com.avandocmsg.messenger.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** Fan-out via {@code user.presence} → per-user WS deliver (spec 022 US19). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserPresenceEvent(
    @JsonProperty("type") String type,
    @JsonProperty("user_id") String userId,
    @JsonProperty("org_id") String orgId,
    @JsonProperty("presence_status") String presenceStatus,
    @JsonProperty("custom_status_text") String customStatusText,
    @JsonProperty("dnd_until") Instant dndUntil,
    long ts
) {
    public static final String TYPE = "presence"; // NOSONAR java:S1845 -- wire constant matches JSON field name type

    public static UserPresenceEvent of(
        String userId,
        String orgId,
        String presenceStatus,
        String customStatusText,
        Instant dndUntil
    ) {
        return new UserPresenceEvent(
            TYPE,
            userId,
            orgId,
            presenceStatus,
            customStatusText,
            dndUntil,
            System.currentTimeMillis());
    }
}
