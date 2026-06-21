package com.avandocmsg.messenger.worker.indexer;

import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import io.nats.client.Connection;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class IndexerWorkerSolrUpdateTest {

    @Test
    void clearContentTxt_usesAtomicSetEmptyString() throws Exception {
        var nats = mock(Connection.class);
        var solr = mock(SolrClient.class);
        var workerMessages = WorkerMessageSources.forWorker(
            IndexerWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_indexer");
        var worker = new IndexerWorker(
            nats,
            solr,
            true,
            false,
            "messages_meta",
            "indexer-test",
            1000L,
            1000L,
            workerMessages,
            null
        );

        Method method = IndexerWorker.class.getDeclaredMethod("clearContentTxt", String.class);
        method.setAccessible(true);
        method.invoke(worker, "msg-42");

        var docCaptor = ArgumentCaptor.forClass(SolrInputDocument.class);
        verify(solr).add(docCaptor.capture());
        verify(solr).commit(isNull());

        SolrInputDocument doc = docCaptor.getValue();
        assertNotNull(doc);
        assertEquals("msg-42", doc.getFieldValue("id"));

        Object contentField = doc.getFieldValue("content_txt");
        assertNotNull(contentField);
        @SuppressWarnings("unchecked")
        Map<String, String> op = (Map<String, String>) contentField;
        assertEquals("", op.get("set"));
    }

    @Test
    void solrMessageIndexBackendUpsertsAndDeletesThroughNeutralAdapter() throws Exception {
        var solr = mock(SolrClient.class);
        var backend = new SolrMessageIndexBackend(solr, false, "messages_meta");

        backend.upsert(new SearchDocument(
            "msg-43",
            "chat-7",
            "plain text",
            Map.of("sender_id", "user-1", "encrypted", false)
        ));
        backend.delete("msg-43");

        var docCaptor = ArgumentCaptor.forClass(SolrInputDocument.class);
        verify(solr).add(docCaptor.capture());
        verify(solr).deleteById("msg-43");

        var doc = docCaptor.getValue();
        assertEquals("msg-43", doc.getFieldValue("id"));
        assertEquals("chat-7", doc.getFieldValue("chat_id_s"));
        assertEquals("plain text", doc.getFieldValue("content_txt"));
    }
}
