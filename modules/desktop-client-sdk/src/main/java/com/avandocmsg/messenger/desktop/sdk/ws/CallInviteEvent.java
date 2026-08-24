package com.avandocmsg.messenger.desktop.sdk.ws;

import com.avandocmsg.messenger.desktop.sdk.json.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;

/** Provider-neutral call.invited / call.invitation_declined from chat WS delivery. */
public record CallInviteEvent(
    String type,
    String chatId,
    String sessionId,
    String callerUserId,
    String mediaIntent
) {
    public static final String INVITED = "call.invited";
    public static final String INVITATION_DECLINED = "call.invitation_declined";

    public static CallInviteEvent parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = JsonSupport.mapper().readTree(json);
            var type = text(node, "type");
            if (!INVITED.equals(type) && !INVITATION_DECLINED.equals(type)) {
                return null;
            }
            var chatId = firstText(node, "chat_id", "chatId");
            var sessionId = firstText(node, "session_id", "sessionId");
            if (chatId == null || sessionId == null) {
                return null;
            }
            return new CallInviteEvent(
                type,
                chatId,
                sessionId,
                firstText(node, "caller_user_id", "callerUserId"),
                firstText(node, "media_intent", "mediaIntent")
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean invited() {
        return INVITED.equals(type);
    }

    private static String firstText(JsonNode node, String... fields) {
        for (var field : fields) {
            var value = text(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        var value = node.get(field).asText();
        return value == null || value.isBlank() ? null : value;
    }
}
