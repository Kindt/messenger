package com.avandocmsg.messenger.desktop.sdk.ws;

import com.avandocmsg.messenger.desktop.sdk.json.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;

/** Human-readable preview for desktop OS notifications from realtime WS JSON. */
public record WsEventPreview(String chatId, String title, String body, String senderId) {

    private static final String DEFAULT_TITLE = "Korus Messenger";
    private static final String DEFAULT_BODY = "Новое сообщение";

    public static WsEventPreview parse(String json) {
        if (json == null || json.isBlank()) {
            return new WsEventPreview(null, DEFAULT_TITLE, DEFAULT_BODY, null);
        }
        try {
            JsonNode node = JsonSupport.mapper().readTree(json);
            var chatId = firstText(node, "chatId", "chat_id");
            var senderId = firstText(node, "senderId", "sender_id", "userId", "user_id");
            var senderName = firstText(node, "senderName", "sender_name", "author", "from");
            var preview = firstText(node, "text", "body", "preview", "content", "message");
            if (preview != null && preview.length() > 140) {
                preview = preview.substring(0, 137) + "...";
            }
            var title = firstText(node, "chatTitle", "chat_title", "title");
            if (title == null || title.isBlank()) {
                title = chatId == null ? DEFAULT_TITLE : "Чат " + shortId(chatId);
            }
            var body = buildBody(senderName, senderId, preview);
            return new WsEventPreview(chatId, title, body, senderId);
        } catch (Exception ignored) {
            return new WsEventPreview(null, DEFAULT_TITLE, DEFAULT_BODY, null);
        }
    }

    public boolean isOwnMessage(String username) {
        if (username == null || username.isBlank() || senderId == null || senderId.isBlank()) {
            return false;
        }
        return username.equalsIgnoreCase(senderId);
    }

    private static String buildBody(String senderName, String senderId, String preview) {
        var who = senderName;
        if (who == null || who.isBlank()) {
            who = senderId;
        }
        if (preview != null && !preview.isBlank()) {
            return who == null || who.isBlank() ? preview : who + ": " + preview;
        }
        return who == null || who.isBlank() ? DEFAULT_BODY : who + ": " + DEFAULT_BODY;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (var field : fields) {
            if (node.hasNonNull(field)) {
                var value = node.get(field).asText();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String shortId(String chatId) {
        if (chatId.length() <= 8) {
            return chatId;
        }
        return chatId.substring(0, 8) + "…";
    }
}
