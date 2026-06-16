package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.live.dto.LiveSessionResponse;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class LiveSessionRepository {
    private static final Logger log = LoggerFactory.getLogger(LiveSessionRepository.class);

    private final DataSource dataSource;
    private final AppConfig appConfig;
    private final UuidGenerator uuidGenerator;

    public LiveSessionRepository(DataSource dataSource, AppConfig appConfig, UuidGenerator uuidGenerator) {
        this.dataSource = dataSource;
        this.appConfig = appConfig;
        this.uuidGenerator = uuidGenerator;
    }

    public String newRoomName() {
        return appConfig.livestreamRoomPrefix() + uuidGenerator.randomUuid().toString().replace("-", "");
    }

    public Optional<LiveSessionResponse> insert(UUID chatId, UUID createdBy, String title, String roomName) {
        var sql = """
            INSERT INTO live_sessions (chat_id, created_by, title, room_name, max_viewers)
            VALUES (?, ?, ?, ?, ?)
            RETURNING id, chat_id, title, status, mode, room_name, max_viewers, viewer_count, created_at, ended_at
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setObject(2, createdBy);
            stmt.setString(3, title != null ? title : "");
            stmt.setString(4, roomName);
            stmt.setInt(5, appConfig.livestreamMaxWebrtcViewers());
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("insert live session failed", e);
        }
        return Optional.empty();
    }

    public Optional<LiveSessionResponse> findById(UUID sessionId) {
        var sql = """
            SELECT id, chat_id, title, status, mode, room_name, max_viewers, viewer_count, created_at, ended_at
            FROM live_sessions WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, sessionId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(enrich(mapRow(rs)));
                }
            }
        } catch (Exception e) {
            log.error("find live session {}", sessionId, e);
        }
        return Optional.empty();
    }

    public List<LiveSessionResponse> listForChat(UUID chatId, boolean activeOnly) {
        var sql = """
            SELECT id, chat_id, title, status, mode, room_name, max_viewers, viewer_count, created_at, ended_at
            FROM live_sessions WHERE chat_id = ?
            """ + (activeOnly ? " AND status = 'active' " : "") + " ORDER BY created_at DESC";
        var list = new ArrayList<LiveSessionResponse>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(enrich(mapRow(rs)));
                }
            }
        } catch (Exception e) {
            log.error("list live sessions chat {}", chatId, e);
        }
        return list;
    }

    public int countActiveViewers(UUID sessionId) {
        var sql = """
            SELECT COUNT(*) FROM live_session_viewers
            WHERE session_id = ? AND left_at IS NULL
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, sessionId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            log.error("count viewers {}", sessionId, e);
        }
        return 0;
    }

    public Optional<String> viewerRole(UUID sessionId, UUID userId) {
        var sql = """
            SELECT role FROM live_session_viewers
            WHERE session_id = ? AND user_id = ? AND left_at IS NULL
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, sessionId);
            stmt.setObject(2, userId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("role"));
                }
            }
        } catch (Exception e) {
            log.error("viewerRole", e);
        }
        return Optional.empty();
    }

    public boolean join(UUID sessionId, UUID userId, String role) {
        var upsert = """
            INSERT INTO live_session_viewers (session_id, user_id, role, joined_at, left_at)
            VALUES (?, ?, ?, now(), NULL)
            ON CONFLICT (session_id, user_id) DO UPDATE SET
                role = EXCLUDED.role,
                joined_at = now(),
                left_at = NULL
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(upsert)) {
            stmt.setObject(1, sessionId);
            stmt.setObject(2, userId);
            stmt.setString(3, role);
            if (stmt.executeUpdate() <= 0) {
                return false;
            }
            syncViewerCount(conn, sessionId);
            return true;
        } catch (Exception e) {
            log.error("join live session", e);
            return false;
        }
    }

    public boolean leave(UUID sessionId, UUID userId) {
        var sql = """
            UPDATE live_session_viewers SET left_at = now()
            WHERE session_id = ? AND user_id = ? AND left_at IS NULL
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, sessionId);
            stmt.setObject(2, userId);
            if (stmt.executeUpdate() <= 0) {
                return false;
            }
            syncViewerCount(conn, sessionId);
            return true;
        } catch (Exception e) {
            log.error("leave live session", e);
            return false;
        }
    }

    public Optional<UUID> findCreatorId(UUID sessionId) {
        var sql = "SELECT created_by FROM live_sessions WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, sessionId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getObject("created_by", UUID.class));
                }
            }
        } catch (Exception e) {
            log.error("findCreatorId live", e);
        }
        return Optional.empty();
    }

    public boolean endSession(UUID sessionId) {
        var sql = "UPDATE live_sessions SET status = 'ended', ended_at = now() WHERE id = ? AND status = 'active'";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, sessionId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("end live session", e);
            return false;
        }
    }

    private void syncViewerCount(java.sql.Connection conn, UUID sessionId) throws Exception {
        var count = countActiveViewers(sessionId);
        try (var stmt = conn.prepareStatement("UPDATE live_sessions SET viewer_count = ? WHERE id = ?")) {
            stmt.setInt(1, count);
            stmt.setObject(2, sessionId);
            stmt.executeUpdate();
        }
    }

    private LiveSessionResponse mapRow(ResultSet rs) throws Exception {
        var ended = rs.getTimestamp("ended_at");
        return new LiveSessionResponse(
            rs.getObject("id", UUID.class).toString(),
            rs.getObject("chat_id", UUID.class).toString(),
            rs.getString("title"),
            rs.getString("status"),
            rs.getString("mode"),
            rs.getString("room_name"),
            "livekit",
            appConfig.livekitUrl(),
            rs.getInt("viewer_count"),
            rs.getInt("max_viewers"),
            rs.getTimestamp("created_at").toInstant(),
            ended != null ? ended.toInstant() : null
        );
    }

    private LiveSessionResponse enrich(LiveSessionResponse base) {
        var count = countActiveViewers(UUID.fromString(base.liveSessionId()));
        if (count == base.viewerCount()) {
            return base;
        }
        return new LiveSessionResponse(
            base.liveSessionId(),
            base.chatId(),
            base.title(),
            base.status(),
            base.mode(),
            base.roomName(),
            base.provider(),
            base.livekitUrl(),
            count,
            base.maxViewers(),
            base.createdAt(),
            base.endedAt()
        );
    }
}
