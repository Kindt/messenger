package com.avandocmsg.messenger.common.nats;

import io.nats.client.Connection;

import java.util.List;

/** Fan-out to {@link NatsSubjects#MSG_DELIVER_PREFIX} or chat broadcast (PS-1.3). */
public final class DeliverFanout {

    public static final int DEFAULT_DIRECT_MAX = 500;

    public record Config(int directMax, boolean chatBroadcastEnabled) {
        public Config {
            directMax = Math.max(1, directMax);
        }

        public static Config fromEnv() {
            return new Config(
                parsePositive(System.getenv("PIPELINE_FANOUT_DIRECT_MAX"), DEFAULT_DIRECT_MAX),
                !"false".equalsIgnoreCase(trimToNull(System.getenv("PIPELINE_FANOUT_CHAT_ENABLED"))));
        }

        private static int parsePositive(String raw, int defaultValue) {
            if (raw == null || raw.isBlank()) {
                return defaultValue;
            }
            try {
                return Math.max(1, Integer.parseInt(raw.trim()));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        private static String trimToNull(String raw) {
            if (raw == null) {
                return null;
            }
            var t = raw.trim();
            return t.isEmpty() ? null : t;
        }
    }

    public enum Mode {
        DIRECT,
        CHAT_BROADCAST
    }

    private DeliverFanout() {
    }

    public static Mode modeFor(int recipientCount, Config config) {
        if (config.chatBroadcastEnabled() && recipientCount > config.directMax()) {
            return Mode.CHAT_BROADCAST;
        }
        return Mode.DIRECT;
    }

    public static void publish(Connection nats, List<String> memberIds, String chatId, byte[] payload, Config config) {
        publish(nats, memberIds, chatId, payload, config, null, null);
    }

    public static void publish(Connection nats, List<String> memberIds, String chatId, byte[] payload, Config config,
                               FanoutDedup dedup, String messageId) {
        if (nats == null || memberIds == null || memberIds.isEmpty() || payload == null) {
            return;
        }
        PipelineFanoutMetrics.observeRecipients(memberIds.size());
        if (modeFor(memberIds.size(), config) == Mode.CHAT_BROADCAST
            && chatId != null && !chatId.isBlank()) {
            if (dedup != null && dedup.isDuplicate(messageId, "chat:" + chatId)) {
                return;
            }
            nats.publish(NatsSubjects.deliverChatSubject(chatId), payload);
            return;
        }
        for (var memberId : memberIds) {
            if (memberId != null && !memberId.isBlank()
                && (dedup == null || !dedup.isDuplicate(messageId, "user:" + memberId))) {
                nats.publish(NatsSubjects.deliverUserSubject(memberId), payload);
            }
        }
    }
}
