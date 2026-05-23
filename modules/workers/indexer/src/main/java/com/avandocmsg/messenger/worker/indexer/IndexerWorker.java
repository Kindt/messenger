package com.avandocmsg.messenger.worker.indexer;

import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/**
 * Solr indexer: metadata + optional {@code content_txt} for non-E2EE plaintext (see {@link MessageWorkerEvent#searchText()}).
 * {@link MessageWorkerEvent#indexOp()} {@code delete} — {@code deleteById}; {@code update} или отсутствие поля — upsert документа.
 */
public class IndexerWorker {
    private static final Logger log = LoggerFactory.getLogger(IndexerWorker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String QUEUE_GROUP = "indexer-workers";

    private final SolrClient solrClient;
    private final boolean solrEnabled;
    private final boolean cloudMode;
    private final String solrCollection;

    private final Connection connection;

    public IndexerWorker(String natsUrl, SolrClient solrClient, boolean solrEnabled, boolean cloudMode, String solrCollection)
        throws Exception {
        this.solrClient = solrClient;
        this.solrEnabled = solrEnabled;
        this.cloudMode = cloudMode;
        this.solrCollection = solrCollection;
        var options = Options.builder()
            .server(natsUrl)
            .connectionName("indexer-worker")
            .reconnectWait(Duration.ofSeconds(2))
            .maxReconnects(-1)
            .build();
        this.connection = Nats.connect(options);
        log.info("Connected to NATS at {}", natsUrl);
    }

    public void start() {
        var dispatcher = connection.createDispatcher(this::handle);
        dispatcher.subscribe(NatsSubjects.MSG_EVENT_INDEX, QUEUE_GROUP);
        log.info("Subscribed to {} (queue: {})", NatsSubjects.MSG_EVENT_INDEX, QUEUE_GROUP);
    }

    private void handle(io.nats.client.Message msg) {
        try {
            var payload = new String(msg.getData(), StandardCharsets.UTF_8);
            var event = MAPPER.readValue(payload, MessageWorkerEvent.class);
            if (!solrEnabled) {
                return;
            }
            if (event.messageId() == null || event.messageId().isBlank()) {
                log.warn("Indexer skip: empty message_id");
                return;
            }
            if ("delete".equalsIgnoreCase(event.indexOp())) {
                deleteFromSolr(event.messageId());
                return;
            }
            if ("update".equalsIgnoreCase(event.indexOp())) {
                clearContentTxt(event.messageId());
                return;
            }
            indexMetadata(event);
        } catch (Exception e) {
            log.error("Failed to handle indexer message", e);
        }
    }

    private void deleteFromSolr(String messageId) throws Exception {
        if (cloudMode) {
            solrClient.deleteById(solrCollection, messageId);
        } else {
            solrClient.deleteById(messageId);
        }
        solrClient.commit(cloudMode ? solrCollection : null);
        log.debug("Deleted from Solr id={}", messageId);
    }

    /** Atomic partial update: clears content_txt when retention removes the body. */
    private void clearContentTxt(String messageId) throws Exception {
        var doc = new SolrInputDocument();
        doc.addField("id", messageId);
        var clearOp = new HashMap<String, String>();
        clearOp.put("set", "");
        doc.addField("content_txt", clearOp);
        if (cloudMode) {
            solrClient.add(solrCollection, doc);
        } else {
            solrClient.add(doc);
        }
        solrClient.commit(cloudMode ? solrCollection : null);
        log.debug("Cleared content_txt for Solr id={}", messageId);
    }

    private void indexMetadata(MessageWorkerEvent event) throws Exception {
        var doc = new SolrInputDocument();
        doc.addField("id", event.messageId());
        doc.addField("chat_id_s", event.chatId());
        doc.addField("sender_id_s", event.senderId());
        if (event.clientMsgId() != null) {
            doc.addField("client_msg_id_s", event.clientMsgId());
        }
        if (event.createdAtEpochMs() != null) {
            doc.addField("created_at_epoch_ms_l", event.createdAtEpochMs());
        }
        if (event.type() != null) {
            doc.addField("msg_type_s", event.type());
        }
        doc.addField("flags_i", event.flags());
        doc.addField("encrypted_b", event.encrypted());
        if (event.storageByteLength() != null) {
            doc.addField("storage_byte_length_i", event.storageByteLength());
        }
        if (event.searchText() != null && !event.searchText().isBlank()) {
            doc.addField("content_txt", event.searchText());
        }
        if (cloudMode) {
            solrClient.add(solrCollection, doc);
        } else {
            solrClient.add(doc);
        }
        solrClient.commit(cloudMode ? solrCollection : null);
        log.debug("Indexed Solr id={}", event.messageId());
    }

    public void shutdown() {
        try {
            connection.close();
        } catch (Exception e) {
            log.warn("Error closing NATS connection", e);
        }
        if (solrClient != null) {
            try {
                solrClient.close();
            } catch (Exception e) {
                log.warn("Error closing Solr client", e);
            }
        }
    }

    public static void main(String[] args) {
        var natsUrl = System.getenv().getOrDefault("NATS_URL", "nats://localhost:4222");
        var zk = System.getenv("SOLR_ZK");
        var solrUrl = System.getenv("SOLR_URL");
        var collection = System.getenv().getOrDefault("SOLR_COLLECTION", "messages_meta");

        SolrClient client = null;
        boolean cloud = false;
        boolean enabled = false;

        if (zk != null && !zk.isBlank()) {
            var zkHosts = List.of(zk.split("\\s*,\\s*"));
            client = new CloudSolrClient.Builder(zkHosts, Optional.empty())
                    .withDefaultCollection(collection)
                    .build();
            cloud = true;
            enabled = true;
            log.info("Solr Cloud mode ZK={} collection={}", zk, collection);
        } else if (solrUrl != null && !solrUrl.isBlank()) {
            var base = solrUrl.endsWith("/") ? solrUrl.substring(0, solrUrl.length() - 1) : solrUrl;
            var coreUrl = base.contains("/solr/" + collection) ? base : base + "/solr/" + collection;
            client = new HttpJdkSolrClient.Builder(coreUrl).build();
            enabled = true;
            log.info("Solr HTTP mode baseUrl={}", coreUrl);
        }

        if (!enabled) {
            log.info("Solr indexing disabled (set SOLR_URL or SOLR_ZK plus SOLR_COLLECTION); msg.event.index events will not be indexed");
        }

        try {
            var worker = new IndexerWorker(natsUrl, client, enabled, cloud, collection);
            worker.start();
            Runtime.getRuntime().addShutdownHook(new Thread(worker::shutdown));
            Thread.currentThread().join();
        } catch (Exception e) {
            log.error("Fatal error", e);
            System.exit(1);
        }
    }
}
