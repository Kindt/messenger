package com.avandocmsg.messenger.worker.indexer;

interface MessageIndexBackend {

    String profileId();

    boolean enabled();

    void upsert(SearchDocument document) throws Exception;

    void delete(String messageId) throws Exception;

    default void upsertBatch(java.util.List<SearchDocument> documents) throws Exception {
        for (var document : documents) {
            upsert(document);
        }
    }

    default void deleteBatch(java.util.List<String> messageIds) throws Exception {
        for (var messageId : messageIds) {
            delete(messageId);
        }
    }
}
