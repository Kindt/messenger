package com.avandocmsg.messenger.api.search;

import java.util.List;

public interface MessageIndexBackend {

    String profileId();

    boolean enabled();

    void upsert(SearchDocument document) throws MessageIndexException;

    void delete(String messageId) throws MessageIndexException;

    default void upsertBatch(List<SearchDocument> documents) throws MessageIndexException {
        for (var document : documents) {
            upsert(document);
        }
    }

    final class MessageIndexException extends Exception {
        public MessageIndexException(String message) {
            super(message);
        }

        public MessageIndexException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
