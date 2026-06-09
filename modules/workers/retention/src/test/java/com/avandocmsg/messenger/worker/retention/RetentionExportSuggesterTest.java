package com.avandocmsg.messenger.worker.retention;

import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.prometheus.client.CollectorRegistry;

import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionExportSuggesterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void publishForChatCounts_publishesPerChat() throws Exception {
        var chatId = UUID.randomUUID();
        var subject = new AtomicReference<String>();
        var payload = new AtomicReference<byte[]>();
        var publishCount = new AtomicInteger();
        Connection nats = (Connection) java.lang.reflect.Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] { Connection.class },
            (proxy, method, args) -> {
                if ("publish".equals(method.getName()) && args != null && args.length == 2) {
                    subject.set((String) args[0]);
                    payload.set((byte[]) args[1]);
                    publishCount.incrementAndGet();
                    return null;
                }
                return defaultValue(method.getReturnType());
            });

        RetentionExportSuggester.publishForChatCounts(nats, Map.of(chatId, 2), WorkerMessageSources.forWorker(RetentionWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_retention"));

        assertEquals(1, publishCount.get());
        var suggested = CollectorRegistry.defaultRegistry.getSampleValue(
            "retention_worker_export_suggested_published_total");
        assertTrue(suggested != null && suggested >= 1.0);
        assertEquals(NatsSubjects.MSG_EXPORT_SUGGESTED, subject.get());
        var tree = MAPPER.readTree(payload.get());
        assertEquals(chatId.toString(), tree.get("chatId").asText());
        assertEquals(2, tree.get("candidateMessageCount").asInt());
        assertTrue(tree.get("suggestedAtEpochMs").asLong() > 0);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        return null;
    }
}
