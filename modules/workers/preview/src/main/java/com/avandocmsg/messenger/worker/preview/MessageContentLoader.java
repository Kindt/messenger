package com.avandocmsg.messenger.worker.preview;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/** Loads {@code messages.content} for URL extraction (plaintext only; E2EE ciphertext typically yields no URLs). */
final class MessageContentLoader {
    private static final Logger log = LoggerFactory.getLogger(MessageContentLoader.class);

    private final DataSource dataSource;

    MessageContentLoader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    Optional<String> loadContent(UUID messageId) {
        var sql = "SELECT content FROM messages WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, messageId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            log.warn("Failed to load message content for {}", messageId, e);
        }
        return Optional.empty();
    }
}
