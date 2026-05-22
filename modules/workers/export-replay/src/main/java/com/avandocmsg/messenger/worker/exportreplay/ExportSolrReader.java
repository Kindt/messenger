package com.avandocmsg.messenger.worker.exportreplay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Optional Solr index dump for a chat ({@code chat_id_s} filter). */
final class ExportSolrReader {

    private static final Logger log = LoggerFactory.getLogger(ExportSolrReader.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SolrClient client;
    private final boolean cloudMode;
    private final String collection;
    private final boolean includeContentText;

    ExportSolrReader(SolrClient client, boolean cloudMode, String collection, boolean includeContentText) {
        this.client = client;
        this.cloudMode = cloudMode;
        this.collection = collection;
        this.includeContentText = includeContentText;
    }

    static ExportSolrReader fromEnv() {
        var includeContent = Boolean.parseBoolean(
            System.getenv().getOrDefault("EXPORT_REPLAY_SOLR_INCLUDE_CONTENT", "false"));
        var collection = System.getenv().getOrDefault("SOLR_COLLECTION", "messages_meta");
        var zk = System.getenv("SOLR_ZK");
        if (zk != null && !zk.isBlank()) {
            var zkHosts = List.of(zk.trim().split("\\s*,\\s*"));
            var client = new CloudSolrClient.Builder(zkHosts, Optional.empty())
                .withDefaultCollection(collection)
                .build();
            return new ExportSolrReader(client, true, collection, includeContent);
        }
        var url = System.getenv("SOLR_URL");
        if (url != null && !url.isBlank()) {
            var base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url.trim();
            var coreUrl = base.contains("/solr/" + collection) ? base : base + "/solr/" + collection;
            var client = new HttpJdkSolrClient.Builder(coreUrl).build();
            return new ExportSolrReader(client, false, collection, includeContent);
        }
        return null;
    }

    static String chatIdQuery(UUID chatId) {
        return "chat_id_s:" + ClientUtils.escapeQueryChars(chatId.toString());
    }

    SolrIndexAttachResult fetchChatIndex(UUID chatId, int maxDocs) throws Exception {
        var rows = Math.min(Math.max(maxDocs, 1), 50_000);
        var solrQuery = new SolrQuery(chatIdQuery(chatId));
        solrQuery.setRows(rows);
        solrQuery.setFields(
            "id", "chat_id_s", "sender_id_s", "client_msg_id_s", "created_at_epoch_ms_l",
            "msg_type_s", "flags_i", "encrypted_b", "storage_byte_length_i"
            + (includeContentText ? ",content_txt" : ""));

        QueryResponse response = cloudMode
            ? client.query(collection, solrQuery)
            : client.query(solrQuery);

        var docs = response.getResults();
        var arr = MAPPER.createArrayNode();
        for (var doc : docs) {
            arr.add(documentToNode(doc));
        }
        var truncated = docs.getNumFound() > arr.size();
        return new SolrIndexAttachResult(arr, (int) docs.getNumFound(), arr.size(), truncated);
    }

    private ObjectNode documentToNode(SolrDocument doc) {
        var n = MAPPER.createObjectNode();
        putField(n, "id", doc.getFieldValue("id"));
        putField(n, "chatId", doc.getFieldValue("chat_id_s"));
        putField(n, "senderId", doc.getFieldValue("sender_id_s"));
        putField(n, "clientMsgId", doc.getFieldValue("client_msg_id_s"));
        putField(n, "createdAtEpochMs", doc.getFieldValue("created_at_epoch_ms_l"));
        putField(n, "type", doc.getFieldValue("msg_type_s"));
        putField(n, "flags", doc.getFieldValue("flags_i"));
        putField(n, "encrypted", doc.getFieldValue("encrypted_b"));
        putField(n, "storageByteLength", doc.getFieldValue("storage_byte_length_i"));
        if (includeContentText) {
            putField(n, "contentText", doc.getFieldValue("content_txt"));
        }
        return n;
    }

    private static void putField(ObjectNode n, String key, Object value) {
        if (value == null) {
            n.putNull(key);
        } else if (value instanceof Boolean b) {
            n.put(key, b);
        } else if (value instanceof Number num) {
            n.put(key, num.longValue());
        } else {
            n.put(key, value.toString());
        }
    }

    record SolrIndexAttachResult(ArrayNode documents, int numFound, int exported, boolean truncated) {
    }
}
