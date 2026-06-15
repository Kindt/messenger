package com.avandocmsg.messenger.worker.retention;

import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phase C: delete orphaned {@code file_metadata} rows and MinIO objects ({@code {id}/{filename}}).
 */
final class FileRetentionJanitor {

    private static final Logger log = LoggerFactory.getLogger(FileRetentionJanitor.class);
    private static final String AUDIT_ACTION = "file.retention.deleted";

    private static final String SELECT_CANDIDATES = """
        SELECT fm.id, fm.filename, fm.content_hash, fm.storage_key
        FROM file_metadata fm
        WHERE fm.created_at < now() - make_interval(days => ?)
          AND NOT EXISTS (
            SELECT 1 FROM messages m WHERE m.attachment_file_id = fm.id
          )
          AND NOT EXISTS (
            SELECT 1 FROM messages m
            WHERE m.content IS NOT NULL AND m.content LIKE 'file://' || fm.id::text || '%'
          )
          AND NOT EXISTS (
            SELECT 1 FROM chats c
            WHERE c.avatar_file_id IS NOT NULL AND c.avatar_file_id = fm.id::text
          )
          AND NOT EXISTS (
            SELECT 1 FROM file_public_links fpl WHERE fpl.file_id = fm.id
          )
          AND NOT EXISTS (
            SELECT 1 FROM users u
            INNER JOIN org_retention_policy orp ON orp.org_id = u.org_id
            WHERE u.id = fm.uploaded_by AND orp.legal_hold_files = true
          )
        ORDER BY fm.created_at ASC
        LIMIT ?
        """;

    private static final String DELETE_METADATA = "DELETE FROM file_metadata WHERE id = ?";

    private static final String DECREMENT_BLOB_REF = """
        UPDATE file_blob SET ref_count = ref_count - 1
        WHERE content_hash = ? AND ref_count > 0
        """;

    private static final String SELECT_BLOB_REF = "SELECT ref_count FROM file_blob WHERE content_hash = ?";

    private static final String DELETE_BLOB_ZERO = "DELETE FROM file_blob WHERE content_hash = ? AND ref_count <= 0";

    private static final String INSERT_AUDIT = """
        INSERT INTO audit_events (actor_user_id, action, resource_type, resource_id, details_json)
        VALUES (NULL, ?, ?, ?, ?)
        """;

    private FileRetentionJanitor() {
    }

    static String candidateSelectSql() {
        return SELECT_CANDIDATES;
    }

    static int process(
        DataSource dataSource,
        MinioClient minioClient,
        boolean minioEnabled,
        String minioBucket,
        int minAgeDays,
        int batchLimit,
        boolean auditEnabled,
        boolean dryRun,
        UserMessageSource workerMessages
    ) throws Exception {
        if (!RetentionPlatformDefaults.fileMetadataCleanupEnabledFromEnv()) {
            return 0;
        }
        if (minAgeDays <= 0 || batchLimit <= 0) {
            return 0;
        }
        var candidates = loadCandidates(dataSource, minAgeDays, batchLimit);
        if (candidates.isEmpty()) {
            return 0;
        }
        if (dryRun) {
            log.info(workerMessages.format("worker.retention.file.dry_run", candidates.size()));
            return 0;
        }
        int deleted = 0;
        for (var c : candidates) {
            if (deleteOne(dataSource, minioClient, minioEnabled, minioBucket, c, auditEnabled, workerMessages)) {
                deleted++;
            }
        }
        if (deleted > 0) {
            log.info(workerMessages.format("worker.retention.file.deleted", deleted));
        }
        return deleted;
    }

    private static List<Candidate> loadCandidates(DataSource dataSource, int minAgeDays, int batchLimit)
        throws SQLException {
        var list = new ArrayList<Candidate>();
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(SELECT_CANDIDATES)) {
            ps.setInt(1, minAgeDays);
            ps.setInt(2, batchLimit);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Candidate(
                        rs.getObject("id", UUID.class),
                        rs.getString("filename"),
                        rs.getString("content_hash"),
                        rs.getString("storage_key")));
                }
            }
        }
        return list;
    }

    private static boolean deleteOne(
        DataSource dataSource,
        MinioClient minioClient,
        boolean minioEnabled,
        String minioBucket,
        Candidate c,
        boolean auditEnabled,
        UserMessageSource workerMessages
    ) throws Exception {
        var objectName = resolveObjectName(c);
        int rows;
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(DELETE_METADATA)) {
            ps.setObject(1, c.fileId());
            rows = ps.executeUpdate();
        }
        if (rows <= 0) {
            return false;
        }
        RetentionMetrics.fileMetadataDeleted();
        if (c.contentHash() != null && !c.contentHash().isBlank()) {
            decrementBlobRef(dataSource, c.contentHash());
            var remaining = blobRefCount(dataSource, c.contentHash());
            if (remaining.isPresent() && remaining.get() <= 0) {
                deleteMinioObject(minioClient, minioEnabled, minioBucket, objectName, c.fileId(), workerMessages);
                deleteZeroBlobRow(dataSource, c.contentHash());
            }
        } else {
            deleteMinioObject(minioClient, minioEnabled, minioBucket, objectName, c.fileId(), workerMessages);
        }
        if (auditEnabled) {
            insertAudit(dataSource, c.fileId(), objectName);
        }
        return true;
    }

    private static String resolveObjectName(Candidate c) {
        if (c.storageKey() != null && !c.storageKey().isBlank()) {
            return c.storageKey();
        }
        return c.fileId() + "/" + (c.filename() != null && !c.filename().isBlank() ? c.filename() : "file");
    }

    private static void deleteMinioObject(
        MinioClient minioClient,
        boolean minioEnabled,
        String minioBucket,
        String objectName,
        UUID fileId,
        UserMessageSource workerMessages
    ) {
        if (!minioEnabled || minioClient == null || minioBucket == null || minioBucket.isBlank()) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(minioBucket)
                .object(objectName)
                .build());
            RetentionMetrics.minioObjectDeleted();
        } catch (Exception e) {
            RetentionMetrics.purgeError("minio_delete");
            log.warn(workerMessages.format("worker.retention.file.minio_delete_failed", fileId, objectName, e.getMessage()));
        }
    }

    private static void decrementBlobRef(DataSource dataSource, String contentHash) throws SQLException {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(DECREMENT_BLOB_REF)) {
            ps.setString(1, contentHash);
            ps.executeUpdate();
        }
    }

    private static java.util.Optional<Integer> blobRefCount(DataSource dataSource, String contentHash) throws SQLException {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(SELECT_BLOB_REF)) {
            ps.setString(1, contentHash);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return java.util.Optional.of(rs.getInt(1));
                }
            }
        }
        return java.util.Optional.empty();
    }

    private static void deleteZeroBlobRow(DataSource dataSource, String contentHash) throws SQLException {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(DELETE_BLOB_ZERO)) {
            ps.setString(1, contentHash);
            ps.executeUpdate();
        }
    }

    private static void insertAudit(DataSource dataSource, UUID fileId, String objectName) throws SQLException {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(INSERT_AUDIT)) {
            ps.setString(1, AUDIT_ACTION);
            ps.setString(2, "file");
            ps.setString(3, fileId.toString());
            ps.setString(4, "{\"object_name\":\"" + objectName.replace("\"", "\\\"") + "\"}");
            ps.executeUpdate();
        }
    }

    record Candidate(UUID fileId, String filename, String contentHash, String storageKey) {
    }
}
