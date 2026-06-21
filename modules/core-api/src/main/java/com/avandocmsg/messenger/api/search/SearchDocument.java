package com.avandocmsg.messenger.api.search;

import java.util.Map;

public record SearchDocument(
    String messageId,
    String chatId,
    String content,
    Map<String, Object> metadata
) {
    public SearchDocument {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
