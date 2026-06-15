package com.avandocmsg.messenger.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Targeted Redis read-cache invalidation after message fan-out (spec 006 T202 / T302).
 */
public record ReadCacheInvalidateEvent(
    @JsonProperty("user_ids") List<String> userIds,
    @JsonProperty("invalidate_unread") boolean invalidateUnread,
    @JsonProperty("invalidate_chat_list") boolean invalidateChatList
) {
}
