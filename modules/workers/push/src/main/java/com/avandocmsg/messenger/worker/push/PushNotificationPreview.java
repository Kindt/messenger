package com.avandocmsg.messenger.worker.push;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;

/**
 * Visible push text derived from {@link MessageWorkerEvent} metadata (no extra DB body fetch).
 */
public record PushNotificationPreview(String title, String body, String url) {

    public static PushNotificationPreview forEvent(MessageWorkerEvent event, String chatTitle,
                                                     UserMessageSource messages) {
        var title = (chatTitle != null && !chatTitle.isBlank())
            ? chatTitle.trim()
            : messages.get("worker.push.preview.default_title");
        var body = bodyFor(event, messages);
        var url = "/";
        if (event.chatId() != null && !event.chatId().isBlank()) {
            url = "/?chat=" + event.chatId();
        }
        return new PushNotificationPreview(title, body, url);
    }

    static String bodyFor(MessageWorkerEvent event, UserMessageSource messages) {
        if (event == null) {
            return messages.get("worker.push.preview.new_message");
        }
        if (event.encrypted() || isE2eeType(event.type())) {
            return messages.get("worker.push.preview.encrypted");
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
            return messages.get("worker.push.preview.new_message");
        }
        return switch (type) {
            case "image" -> messages.get("worker.push.preview.image");
            case "video" -> messages.get("worker.push.preview.video");
            case "file" -> messages.get("worker.push.preview.file");
            default -> messages.get("worker.push.preview.new_message");
        };
    }

    private static boolean isE2eeType(String type) {
        return type != null && type.startsWith("e2ee-");
    }
}
