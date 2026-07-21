package com.avandocmsg.messenger.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Targeted Redis read-cache invalidation after message fan-out (spec 006 T202 / T302).
 *
 * @deprecated spec 025 FR-009: pipeline invalidates Redis directly; NATS path removed from publisher.
 */
@Deprecated
public record ReadCacheInvalidateEvent( // NOSONAR java:S1133 -- retained for rollback deserialize until legacy NATS path deleted
    @JsonProperty("user_ids") List<String> userIds,
    @JsonProperty("invalidate_unread") boolean invalidateUnread,
    @JsonProperty("invalidate_chat_list") boolean invalidateChatList
) {
}
