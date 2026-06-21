package com.avandocmsg.messenger.worker.indexer;

import java.util.Map;

record SearchDocument(
    String messageId,
    String chatId,
    String content,
    Map<String, Object> metadata
) {
    SearchDocument {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
