package com.avandocmsg.messenger.worker.indexer;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Buffers Solr upserts/deletes for batch commit (spec 006 stage 7).
 */
final class IndexerBatchBuffer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(IndexerBatchBuffer.class);

    private final SolrClient solrClient;
    private final boolean cloudMode;
    private final String solrCollection;
    private final int batchSize;
    private final long flushMs;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, SolrInputDocument> pendingAdds = new LinkedHashMap<>();
    private final List<String> pendingDeletes = new ArrayList<>();
    private final ScheduledExecutorService scheduler;

    IndexerBatchBuffer(SolrClient solrClient, boolean cloudMode, String solrCollection, int batchSize, long flushMs) {
        this.solrClient = solrClient;
        this.cloudMode = cloudMode;
        this.solrCollection = solrCollection;
        this.batchSize = Math.max(1, batchSize);
        this.flushMs = Math.max(50L, flushMs);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "indexer-batch-flush");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleAtFixedRate(this::flushQuietly, this.flushMs, this.flushMs, TimeUnit.MILLISECONDS);
    }

    void offerDelete(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        lock.lock();
        try {
            pendingAdds.remove(messageId);
            if (!pendingDeletes.contains(messageId)) {
                pendingDeletes.add(messageId);
            }
            maybeFlushLocked();
        } finally {
            lock.unlock();
        }
    }

    void offerAdd(SolrInputDocument doc) {
        var id = (String) doc.getFieldValue("id");
        if (id == null || id.isBlank()) {
            return;
        }
        lock.lock();
        try {
            pendingDeletes.remove(id);
            pendingAdds.put(id, doc);
            maybeFlushLocked();
        } finally {
            lock.unlock();
        }
    }

    void flush() throws Exception {
        lock.lock();
        try {
            flushLocked();
        } finally {
            lock.unlock();
        }
    }

    private void flushQuietly() {
        try {
            flush();
        } catch (Exception e) {
            log.warn("indexer batch flush failed: {}", e.getMessage());
            IndexerSolrMetrics.error();
        }
    }

    private void maybeFlushLocked() {
        if (pendingAdds.size() + pendingDeletes.size() >= batchSize) {
            try {
                flushLocked();
            } catch (Exception e) {
                log.warn("indexer batch flush failed: {}", e.getMessage());
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
            for (var id : deletes) {
                if (cloudMode) {
                    solrClient.deleteById(solrCollection, id);
                } else {
                    solrClient.deleteById(id);
                }
            }
        }
        if (!adds.isEmpty()) {
            if (cloudMode) {
                solrClient.add(solrCollection, adds);
            } else {
                solrClient.add(adds);
            }
        }
        solrClient.commit(cloudMode ? solrCollection : null);
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
