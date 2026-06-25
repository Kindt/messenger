package com.avandocmsg.messenger.worker.indexer;

import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.scheduling.ScheduledTaskSupport;
import org.apache.solr.client.solrj.SolrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Buffers Solr upserts/deletes for batch commit (spec 006 stage 7).
 */
final class IndexerBatchBuffer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(IndexerBatchBuffer.class);

    private final MessageIndexBackend indexBackend;
    private final int batchSize;
    private final long flushMs;
    private final UserMessageSource workerMessages;
    private final Map<String, SearchDocument> pendingAdds = new LinkedHashMap<>();
    private final List<String> pendingDeletes = new ArrayList<>();
    private final ScheduledExecutorService scheduler;

    IndexerBatchBuffer(SolrClient solrClient, boolean cloudMode, String solrCollection, int batchSize, long flushMs,
                       UserMessageSource workerMessages) {
        this(new SolrMessageIndexBackend(solrClient, cloudMode, solrCollection), batchSize, flushMs, workerMessages);
    }

    IndexerBatchBuffer(MessageIndexBackend indexBackend, int batchSize, long flushMs, UserMessageSource workerMessages) {
        this.indexBackend = indexBackend;
        this.batchSize = Math.max(1, batchSize);
        this.flushMs = Math.max(50L, flushMs);
        this.workerMessages = workerMessages;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "indexer-batch-flush");
            t.setDaemon(true);
            return t;
        });
        ScheduledTaskSupport.scheduleAtFixedRateWithJitter(
            this.scheduler, this::flushQuietly, this.flushMs, this.flushMs, this.flushMs / 5, TimeUnit.MILLISECONDS);
    }

    void offerDelete(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        synchronized (this) {
            pendingAdds.remove(messageId);
            if (!pendingDeletes.contains(messageId)) {
                pendingDeletes.add(messageId);
            }
            maybeFlushLocked();
        }
    }

    void offerAdd(SearchDocument doc) {
        var id = doc.messageId();
        if (id == null || id.isBlank()) {
            return;
        }
        synchronized (this) {
            pendingDeletes.remove(id);
            pendingAdds.put(id, doc);
            maybeFlushLocked();
        }
    }

    void flush() throws Exception {
        synchronized (this) {
            flushLocked();
        }
    }

    private void flushQuietly() {
        try {
            flush();
        } catch (Exception e) {
            log.warn(workerMessages.format("worker.indexer.batch_flush_failed", e.getMessage()));
            IndexerSolrMetrics.error();
        }
    }

    private void maybeFlushLocked() {
        if (pendingAdds.size() + pendingDeletes.size() >= batchSize) {
            try {
                flushLocked();
            } catch (Exception e) {
                log.warn(workerMessages.format("worker.indexer.batch_flush_failed", e.getMessage()));
                IndexerSolrMetrics.error();
            }
        }
    }

    private void flushLocked() throws Exception {
        if (pendingAdds.isEmpty() && pendingDeletes.isEmpty()) {
            return;
        }
        var deletes = List.copyOf(pendingDeletes);
        var adds = List.copyOf(pendingAdds.values());
        pendingDeletes.clear();
        pendingAdds.clear();
        if (!deletes.isEmpty()) {
            indexBackend.deleteBatch(deletes);
        }
        if (!adds.isEmpty()) {
            indexBackend.upsertBatch(adds);
        }
        IndexerSolrMetrics.batchFlushed(adds.size(), deletes.size());
    }

    @Override
    public void close() {
        scheduler.shutdown();
        try {
            flush();
        } catch (Exception e) {
            log.warn("indexer final batch flush failed: {}", e.getMessage());
        }
    }
}
