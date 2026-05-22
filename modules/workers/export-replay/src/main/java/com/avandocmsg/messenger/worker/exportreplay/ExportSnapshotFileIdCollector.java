package com.avandocmsg.messenger.worker.exportreplay;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;
import java.util.UUID;

/** Walks JSON snapshot trees and collects UUID-shaped file references (same rules as message body scan). */
final class ExportSnapshotFileIdCollector {

    private ExportSnapshotFileIdCollector() {}

    /**
     * @return true if {@code maxSinkSize} was reached while more tokens may remain
     */
    static boolean collectFromJson(JsonNode node, Set<UUID> sink, int maxSinkSize) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isTextual()) {
            return ExportReplayWorker.collectFileIdsFromText(node.asText(), sink, maxSinkSize);
        }
        boolean truncated = false;
        if (node.isObject()) {
            var names = node.fieldNames();
            while (names.hasNext()) {
                if (collectFromJson(node.get(names.next()), sink, maxSinkSize)) {
                    truncated = true;
                }
            }
        } else if (node.isArray()) {
            for (var child : node) {
                if (collectFromJson(child, sink, maxSinkSize)) {
                    truncated = true;
                }
            }
        }
        return truncated;
    }
}
