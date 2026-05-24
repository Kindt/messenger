package com.avandocmsg.messenger.worker.retention;

import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
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
        SELECT fm.id, fm.filename
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
        boolean dryRun
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
            log.info("File retention dry-run: candidates={}", candidates.size());
            return 0;
        }
        int deleted = 0;
        for (var c : candidates) {
            if (deleteOne(dataSource, minioClient, minioEnabled, minioBucket, c, auditEnabled)) {
                deleted++;
            }
        }
        if (deleted > 0) {
            log.info("File retention pass: deleted={}", deleted);
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
                        rs.getString("filename")));
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
        boolean auditEnabled
    ) throws Exception {
        var objectName = c.fileId() + "/" + (c.filename() != null && !c.filename().isBlank() ? c.filename() : "file");
        if (minioEnabled && minioClient != null && minioBucket != null && !minioBucket.isBlank()) {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioBucket)
                    .object(objectName)
                    .build());
                RetentionMetrics.minioObjectDeleted();
            } catch (Exception e) {
                RetentionMetrics.purgeError("minio_delete");
                log.warn("MinIO delete failed fileId={} key={}: {}", c.fileId(), objectName, e.getMessage());
                return false;
            }
        }
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
        if (auditEnabled) {
            insertAudit(dataSource, c.fileId(), objectName);
        }
        return true;
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

    record Candidate(UUID fileId, String filename) {
    }
}
