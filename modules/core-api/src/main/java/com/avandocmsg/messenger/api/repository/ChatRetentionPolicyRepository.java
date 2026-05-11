package com.avandocmsg.messenger.api.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Строка {@code chat_retention_policy} (миграция {@code V012}). */
public class ChatRetentionPolicyRepository {
    private static final Logger log = LoggerFactory.getLogger(ChatRetentionPolicyRepository.class);
    private final DataSource dataSource;

    public ChatRetentionPolicyRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<StoredRow> findByChatId(UUID chatId) {
        if (dataSource == null) {
            return Optional.empty();
        }
        var sql = """
            SELECT chat_id, hot_message_body_max_age_days, hot_metadata_min_age_days,
                   archive_metadata_enabled, deep_archive_enabled, legal_hold, updated_at, updated_by
            FROM chat_retention_policy WHERE chat_id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                var updatedBy = rs.getObject("updated_by", UUID.class);
                return Optional.of(new StoredRow(
                    rs.getObject("chat_id", UUID.class),
                    (Integer) rs.getObject("hot_message_body_max_age_days"),
                    (Integer) rs.getObject("hot_metadata_min_age_days"),
                    rs.getBoolean("archive_metadata_enabled"),
                    rs.getBoolean("deep_archive_enabled"),
                    rs.getBoolean("legal_hold"),
                    rs.getTimestamp("updated_at").toInstant(),
                    updatedBy != null ? updatedBy.toString() : null
                ));
            }
        } catch (Exception e) {
            log.error("find chat retention policy failed chatId={}", chatId, e);
            return Optional.empty();
        }
    }

    public boolean upsert(UUID chatId, Integer hotMessageBodyMaxAgeDays, Integer hotMetadataMinAgeDays,
                          boolean archiveMetadataEnabled, boolean deepArchiveEnabled, boolean legalHold,
                          UUID updatedBy) {
        if (dataSource == null) {
            return false;
        }
        var updateSql = """
            UPDATE chat_retention_policy SET
                hot_message_body_max_age_days = ?,
                hot_metadata_min_age_days = ?,
                archive_metadata_enabled = ?,
                deep_archive_enabled = ?,
                legal_hold = ?,
                updated_at = now(),
                updated_by = ?
            WHERE chat_id = ?
            """;
        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.prepareStatement(updateSql)) {
                stmt.setObject(1, hotMessageBodyMaxAgeDays);
                stmt.setObject(2, hotMetadataMinAgeDays);
                stmt.setBoolean(3, archiveMetadataEnabled);
                stmt.setBoolean(4, deepArchiveEnabled);
                stmt.setBoolean(5, legalHold);
                stmt.setObject(6, updatedBy);
                stmt.setObject(7, chatId);
                if (stmt.executeUpdate() > 0) {
                    return true;
                }
            }
            var insertSql = """
                INSERT INTO chat_retention_policy (
                    chat_id, hot_message_body_max_age_days, hot_metadata_min_age_days,
                    archive_metadata_enabled, deep_archive_enabled, legal_hold, updated_at, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, now(), ?)
                """;
            try (var stmt = conn.prepareStatement(insertSql)) {
                stmt.setObject(1, chatId);
                stmt.setObject(2, hotMessageBodyMaxAgeDays);
                stmt.setObject(3, hotMetadataMinAgeDays);
                stmt.setBoolean(4, archiveMetadataEnabled);
                stmt.setBoolean(5, deepArchiveEnabled);
                stmt.setBoolean(6, legalHold);
                stmt.setObject(7, updatedBy);
                return stmt.executeUpdate() > 0;
            }
        } catch (Exception e) {
            log.error("upsert chat retention policy failed chatId={}", chatId, e);
            return false;
        }
    }

    public record StoredRow(
        UUID chatId,
        Integer hotMessageBodyMaxAgeDays,
        Integer hotMetadataMinAgeDays,
        boolean archiveMetadataEnabled,
        boolean deepArchiveEnabled,
        boolean legalHold,
        Instant updatedAt,
        String updatedBy
    ) {}
}
