package com.avandocmsg.messenger.worker.push;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;

/**
 * Visible push text derived from {@link MessageWorkerEvent} metadata (no extra DB body fetch).
 */
public record PushNotificationPreview(String title, String body, String url) {

    public static PushNotificationPreview forEvent(MessageWorkerEvent event, String chatTitle) {
        var title = (chatTitle != null && !chatTitle.isBlank()) ? chatTitle.trim() : "Korus Messenger";
        var body = bodyFor(event);
        var url = "/";
        if (event.chatId() != null && !event.chatId().isBlank()) {
            url = "/?chat=" + event.chatId();
        }
        return new PushNotificationPreview(title, body, url);
    }

    static String bodyFor(MessageWorkerEvent event) {
        if (event == null) {
            return "Новое сообщение";
        }
        if (event.encrypted() || isE2eeType(event.type())) {
            return "🔒 Новое зашифрованное сообщение";
        }
        if (event.searchText() != null && !event.searchText().isBlank()) {
            var s = event.searchText().replaceAll("\\s+", " ").trim();
            if (s.length() > 120) {
                return s.substring(0, 120) + "…";
            }
            return s;
        }
        var type = event.type();
        if (type == null) {
            return "Новое сообщение";
        }
        return switch (type) {
            case "image" -> "Изображение";
            case "video" -> "Видео";
            case "file" -> "Файл";
            default -> "Новое сообщение";
        };
    }

    private static boolean isE2eeType(String type) {
        return type != null && type.startsWith("e2ee-");
    }
}
