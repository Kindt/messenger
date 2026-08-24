package com.avandocmsg.messenger.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Provider-neutral call lifecycle event delivered to authenticated chat members. */
public record CallSessionEvent(
    String type,
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("session_id") String sessionId,
    @JsonProperty("caller_user_id") String callerUserId,
    @JsonProperty("media_intent") String mediaIntent,
    @JsonProperty("created_at") String createdAt,
    @JsonProperty("declined_by_user_id") String declinedByUserId
) {
    public static final String INVITED = "call.invited";
    public static final String INVITATION_DECLINED = "call.invitation_declined";

    public CallSessionEvent(
        String type,
        String chatId,
        String sessionId,
        String callerUserId,
        String mediaIntent,
        String createdAt
    ) {
        this(type, chatId, sessionId, callerUserId, mediaIntent, createdAt, null);
    }
}
