package com.avandocmsg.messenger.worker.indexer;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.common.SolrInputDocument;

import java.util.List;

final class SolrMessageIndexBackend implements MessageIndexBackend {

    private final SolrClient solrClient;
    private final boolean cloudMode;
    private final String solrCollection;

    SolrMessageIndexBackend(SolrClient solrClient, boolean cloudMode, String solrCollection) {
        this.solrClient = solrClient;
        this.cloudMode = cloudMode;
        this.solrCollection = solrCollection;
    }

    @Override
    public String profileId() {
        return cloudMode ? "solr-cloud-index" : "solr-http-index";
    }

    @Override
    public boolean enabled() {
        return solrClient != null;
    }

    @Override
    public void upsert(SearchDocument document) throws Exception {
        var solrDoc = toSolrDocument(document);
        if (cloudMode) {
            solrClient.add(solrCollection, solrDoc);
        } else {
            solrClient.add(solrDoc);
        }
        solrClient.commit(cloudMode ? solrCollection : null);
    }

    @Override
    public void delete(String messageId) throws Exception {
        if (cloudMode) {
            solrClient.deleteById(solrCollection, messageId);
        } else {
            solrClient.deleteById(messageId);
        }
        solrClient.commit(cloudMode ? solrCollection : null);
    }

    @Override
    public void upsertBatch(List<SearchDocument> documents) throws Exception {
        var solrDocs = documents.stream().map(SolrMessageIndexBackend::toSolrDocument).toList();
        if (solrDocs.isEmpty()) {
            return;
        }
        if (cloudMode) {
            solrClient.add(solrCollection, solrDocs);
        } else {
            solrClient.add(solrDocs);
        }
        solrClient.commit(cloudMode ? solrCollection : null);
    }

    @Override
    public void deleteBatch(List<String> messageIds) throws Exception {
        for (var messageId : messageIds) {
            if (cloudMode) {
                solrClient.deleteById(solrCollection, messageId);
            } else {
                solrClient.deleteById(messageId);
            }
        }
        solrClient.commit(cloudMode ? solrCollection : null);
    }

    private static SolrInputDocument toSolrDocument(SearchDocument document) {
        var doc = new SolrInputDocument();
        doc.addField("id", document.messageId());
        doc.addField("chat_id_s", document.chatId());
        if (document.content() != null && !document.content().isBlank()) {
            doc.addField("content_txt", document.content());
        }
        document.metadata().forEach((key, value) -> {
            if (value != null) {
                doc.addField(key, value);
            }
        });
        return doc;
    }
}
