package com.avandocmsg.messenger.worker.indexer;

import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndexerBatchBufferBackendTest {

    @Test
    void flushUsesMessageIndexBackend() throws Exception {
        var backend = new RecordingBackend();
        var buffer = new IndexerBatchBuffer(
            backend,
            10,
            60_000L,
            WorkerMessageSources.forWorker(IndexerWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_indexer")
        );

        buffer.offerAdd(new SearchDocument("msg-1", "chat-1", "hello", Map.of("sender_id", "user-1")));
        buffer.offerDelete("msg-2");
        buffer.flush();
        buffer.close();

        assertEquals(List.of("msg-2"), backend.deletes);
        assertEquals(List.of("msg-1"), backend.upserts.stream().map(SearchDocument::messageId).toList());
    }

    private static final class RecordingBackend implements MessageIndexBackend {
        private final List<SearchDocument> upserts = new ArrayList<>();
        private final List<String> deletes = new ArrayList<>();

        @Override
        public String profileId() {
            return "recording";
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public void upsert(SearchDocument document) {
            upserts.add(document);
        }

        @Override
        public void delete(String messageId) {
            deletes.add(messageId);
        }
    }
}
