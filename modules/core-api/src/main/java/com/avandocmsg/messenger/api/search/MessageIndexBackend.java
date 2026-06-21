package com.avandocmsg.messenger.api.search;

import java.util.List;

public interface MessageIndexBackend {

    String profileId();

    boolean enabled();

    void upsert(SearchDocument document) throws Exception;

    void delete(String messageId) throws Exception;

    default void upsertBatch(List<SearchDocument> documents) throws Exception {
        for (var document : documents) {
            upsert(document);
        }
    }
}
