package com.avandocmsg.messenger.api.meshcall;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC for mesh call sessions and recordings. */
public final class MeshCallRecordingRepository {

    private final DataSource dataSource;

    public MeshCallRecordingRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public UUID createSession(UUID chatId, UUID userId, String mediaMode) {
        var id = UUID.randomUUID();
        var sql = """
            INSERT INTO mesh_call_sessions (id, chat_id, started_by, media_mode, status, recording_mode)
            VALUES (?, ?, ?, ?, 'active', 'mesh')
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setObject(2, chatId);
            stmt.setObject(3, userId);
            stmt.setString(4, mediaMode == null || mediaMode.isBlank() ? "audio" : mediaMode);
            stmt.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new IllegalStateException("createSession failed", e);
        }
    }

    public void attachSessionComposite(UUID sessionId, UUID chatId, String livekitRoom, String egressId, String mode) {
        var sql = """
            UPDATE mesh_call_sessions
            SET livekit_room = ?, egress_id = ?, recording_mode = ?
            WHERE id = ? AND chat_id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, livekitRoom);
            stmt.setString(2, egressId);
            stmt.setString(3, mode);
            stmt.setObject(4, sessionId);
            stmt.setObject(5, chatId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("attachSessionComposite failed", e);
        }
    }

    public void attachRecordingEgress(UUID recordingId, UUID sessionId, UUID chatId, String egressId, String storageKey) {
        var sql = """
            UPDATE mesh_call_recordings SET egress_id = ?, storage_key = ?
            WHERE id = ? AND session_id = ? AND chat_id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, egressId);
            stmt.setString(2, storageKey);
            stmt.setObject(3, recordingId);
            stmt.setObject(4, sessionId);
            stmt.setObject(5, chatId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("attachRecordingEgress failed", e);
        }
    }

    public Optional<SessionRow> findSession(UUID sessionId, UUID chatId) {
        var sql = """
            SELECT id, chat_id, started_by, media_mode, status, started_at, ended_at,
                   livekit_room, egress_id, recording_mode
            FROM mesh_call_sessions WHERE id = ? AND chat_id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, sessionId);
            stmt.setObject(2, chatId);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapSession(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("findSession failed", e);
        }
    }

    public boolean endSession(UUID sessionId, UUID chatId) {
        var sql = """
            UPDATE mesh_call_sessions SET status = 'ended', ended_at = now()
            WHERE id = ? AND chat_id = ? AND status = 'active'
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, sessionId);
            stmt.setObject(2, chatId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("endSession failed", e);
        }
    }

    public UUID createRecording(UUID sessionId, UUID chatId, UUID userId, String kind) {
        var id = UUID.randomUUID();
        var sql = """
            INSERT INTO mesh_call_recordings (id, session_id, chat_id, recorded_by, kind, status)
            VALUES (?, ?, ?, ?, ?, 'recording')
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setObject(2, sessionId);
            stmt.setObject(3, chatId);
            stmt.setObject(4, userId);
            stmt.setString(5, kind);
            stmt.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new IllegalStateException("createRecording failed", e);
        }
    }

    public Optional<RecordingRow> findRecording(UUID recordingId, UUID sessionId, UUID chatId) {
        var sql = """
            SELECT id, session_id, chat_id, recorded_by, kind, status, file_id, started_at, ended_at, duration_ms,
                   egress_id, storage_key
            FROM mesh_call_recordings
            WHERE id = ? AND session_id = ? AND chat_id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, recordingId);
            stmt.setObject(2, sessionId);
            stmt.setObject(3, chatId);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRecording(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("findRecording failed", e);
        }
    }

    public boolean completeRecording(UUID recordingId, UUID sessionId, UUID chatId, UUID fileId, long durationMs) {
        var sql = """
            UPDATE mesh_call_recordings
            SET status = 'completed', file_id = ?, ended_at = now(), duration_ms = ?
            WHERE id = ? AND session_id = ? AND chat_id = ? AND status = 'recording'
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, fileId);
            stmt.setLong(2, durationMs);
            stmt.setObject(3, recordingId);
            stmt.setObject(4, sessionId);
            stmt.setObject(5, chatId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("completeRecording failed", e);
        }
    }

    public boolean failRecording(UUID recordingId, UUID sessionId, UUID chatId) {
        var sql = """
            UPDATE mesh_call_recordings SET status = 'failed', ended_at = now()
            WHERE id = ? AND session_id = ? AND chat_id = ? AND status = 'recording'
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, recordingId);
            stmt.setObject(2, sessionId);
            stmt.setObject(3, chatId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("failRecording failed", e);
        }
    }

    public Optional<RecordingRow> findRecordingByEgress(UUID sessionId, UUID chatId, String egressId) {
        var sql = """
            SELECT id, session_id, chat_id, recorded_by, kind, status, file_id, started_at, ended_at, duration_ms,
                   egress_id, storage_key
            FROM mesh_call_recordings
            WHERE session_id = ? AND chat_id = ? AND egress_id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, sessionId);
            stmt.setObject(2, chatId);
            stmt.setString(3, egressId);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRecording(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("findRecordingByEgress failed", e);
        }
    }

    public List<RecordingRow> listRecordings(UUID sessionId, UUID chatId, UUID viewerId, boolean includeAudit) {
        var sql = new StringBuilder("""
            SELECT id, session_id, chat_id, recorded_by, kind, status, file_id, started_at, ended_at, duration_ms,
                   egress_id, storage_key
            FROM mesh_call_recordings
            WHERE session_id = ? AND chat_id = ?
            """);
        if (!includeAudit) {
            sql.append(" AND kind = 'user' AND recorded_by = ?");
        } else {
            sql.append(" AND kind IN ('user', 'composite', 'audit')");
        }
        sql.append(" ORDER BY started_at DESC");
        var out = new ArrayList<RecordingRow>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql.toString())) {
            stmt.setObject(1, sessionId);
            stmt.setObject(2, chatId);
            if (!includeAudit) {
                stmt.setObject(3, viewerId);
            }
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(mapRecording(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("listRecordings failed", e);
        }
        return out;
    }

    private static SessionRow mapSession(java.sql.ResultSet rs) throws SQLException {
        return new SessionRow(
            rs.getObject("id", UUID.class),
            rs.getObject("chat_id", UUID.class),
            rs.getObject("started_by", UUID.class),
            rs.getString("media_mode"),
            rs.getString("status"),
            rs.getTimestamp("started_at").toInstant(),
            rs.getTimestamp("ended_at") != null ? rs.getTimestamp("ended_at").toInstant() : null,
            rs.getString("livekit_room"),
            rs.getString("egress_id"),
            rs.getString("recording_mode")
        );
    }

    private static RecordingRow mapRecording(java.sql.ResultSet rs) throws SQLException {
        return new RecordingRow(
            rs.getObject("id", UUID.class),
            rs.getObject("session_id", UUID.class),
            rs.getObject("chat_id", UUID.class),
            rs.getObject("recorded_by", UUID.class),
            rs.getString("kind"),
            rs.getString("status"),
            rs.getObject("file_id", UUID.class),
            rs.getTimestamp("started_at").toInstant(),
            rs.getTimestamp("ended_at") != null ? rs.getTimestamp("ended_at").toInstant() : null,
            rs.getObject("duration_ms") != null ? rs.getLong("duration_ms") : null,
            rs.getString("egress_id"),
            rs.getString("storage_key")
        );
    }

    public record SessionRow(
        UUID id,
        UUID chatId,
        UUID startedBy,
        String mediaMode,
        String status,
        Instant startedAt,
        Instant endedAt,
        String livekitRoom,
        String egressId,
        String recordingMode
    ) {}

    public record RecordingRow(
        UUID id,
        UUID sessionId,
        UUID chatId,
        UUID recordedBy,
        String kind,
        String status,
        UUID fileId,
        Instant startedAt,
        Instant endedAt,
        Long durationMs,
        String egressId,
        String storageKey
    ) {}
}
