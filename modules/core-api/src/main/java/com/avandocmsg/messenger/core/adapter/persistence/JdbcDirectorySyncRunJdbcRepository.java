package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.directory.DirectorySyncRunRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class JdbcDirectorySyncRunJdbcRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcDirectorySyncRunJdbcRepository.class);

    private final DataSource dataSource;

    public JdbcDirectorySyncRunJdbcRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public DirectorySyncRunRow startRun(UUID orgId) {
        var id = UUID.randomUUID();
        var sql = """
            INSERT INTO directory_sync_runs (id, org_id, status, users_upserted, started_at)
            VALUES (?, ?, 'running', 0, now())
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setObject(2, orgId);
            stmt.executeUpdate();
            return new DirectorySyncRunRow(id, orgId, "running", 0, null, Instant.now(), null);
        } catch (Exception e) {
            log.error("startRun failed orgId={}", orgId, e);
            return new DirectorySyncRunRow(id, orgId, "error", 0, e.getMessage(), Instant.now(), Instant.now());
        }
    }

    public void finishRun(UUID runId, String status, int usersUpserted, String error) {
        var sql = """
            UPDATE directory_sync_runs
            SET status = ?, users_upserted = ?, error = ?, finished_at = now()
            WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, usersUpserted);
            stmt.setString(3, error);
            stmt.setObject(4, runId);
            stmt.executeUpdate();
        } catch (Exception e) {
            log.error("finishRun failed runId={}", runId, e);
        }
    }

    public Optional<DirectorySyncRunRow> findLatestByOrg(UUID orgId) {
        var sql = """
            SELECT id, org_id, status, users_upserted, error, started_at, finished_at
            FROM directory_sync_runs
            WHERE org_id = ?
            ORDER BY started_at DESC
            LIMIT 1
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, orgId);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        } catch (Exception e) {
            log.error("findLatestByOrg failed orgId={}", orgId, e);
            return Optional.empty();
        }
    }

    private static DirectorySyncRunRow mapRow(java.sql.ResultSet rs) throws Exception {
        var finishedTs = rs.getTimestamp("finished_at");
        return new DirectorySyncRunRow(
            rs.getObject("id", UUID.class),
            rs.getObject("org_id", UUID.class),
            rs.getString("status"),
            rs.getInt("users_upserted"),
            rs.getString("error"),
            rs.getTimestamp("started_at").toInstant(),
            finishedTs != null ? finishedTs.toInstant() : null);
    }
}
