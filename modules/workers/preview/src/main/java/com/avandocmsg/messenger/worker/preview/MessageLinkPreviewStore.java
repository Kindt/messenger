package com.avandocmsg.messenger.worker.preview;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.UUID;

/** Persists fetched link previews into {@code message_link_previews}. */
final class MessageLinkPreviewStore {
    private static final Logger log = LoggerFactory.getLogger(MessageLinkPreviewStore.class);

    private final DataSource dataSource;

    MessageLinkPreviewStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    void upsert(UUID messageId, String url, String title) {
        if (dataSource == null || messageId == null || url == null || url.isBlank()) {
            return;
        }
        var sql = """
            INSERT INTO message_link_previews (message_id, url, title, fetched_at)
            VALUES (?, ?, ?, now())
            ON CONFLICT (message_id) DO UPDATE SET url = EXCLUDED.url, title = EXCLUDED.title, fetched_at = now()
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, messageId);
            stmt.setString(2, url);
            stmt.setString(3, title);
            stmt.executeUpdate();
        } catch (Exception e) {
            log.warn("link preview store failed messageId={}", messageId, e);
        }
    }
}
