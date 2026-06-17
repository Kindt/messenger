package com.avandocmsg.messenger.common.nats;

/** Parses inbound NATS deliver subjects for ws-gateway routing. */
public final class DeliverSubject {

    public enum Type {
        USER,
        CHAT
    }

    public record Target(Type type, String id) {
    }

    private DeliverSubject() {
    }

    public static Target parse(String subject) {
        if (subject == null || subject.isBlank()) {
            return null;
        }
        if (subject.startsWith(NatsSubjects.MSG_DELIVER_CHAT_PREFIX)) {
            var chatId = subject.substring(NatsSubjects.MSG_DELIVER_CHAT_PREFIX.length());
            return chatId.isBlank() ? null : new Target(Type.CHAT, chatId);
        }
        if (subject.startsWith(NatsSubjects.MSG_DELIVER_PREFIX)) {
            var userId = subject.substring(NatsSubjects.MSG_DELIVER_PREFIX.length());
            if (userId.isBlank() || userId.startsWith("chat.")) {
                return null;
            }
            return new Target(Type.USER, userId);
        }
        return null;
    }
}
