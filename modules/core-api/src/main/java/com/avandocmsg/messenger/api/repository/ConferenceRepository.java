package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.conference.dto.ConferenceResponse;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ConferenceRepository {
    private static final Logger log = LoggerFactory.getLogger(ConferenceRepository.class);
    private final DataSource dataSource;
    private final AppConfig appConfig;
    private final UuidGenerator uuidGenerator;

    public ConferenceRepository(DataSource dataSource, AppConfig appConfig, UuidGenerator uuidGenerator) {
        this.dataSource = dataSource;
        this.appConfig = appConfig;
        this.uuidGenerator = uuidGenerator;
    }

    /** Уникальное имя комнаты для Jitsi и других клиентов. */
    public String newRoomSlug() {
        return appConfig.conferenceRoomPrefix() + uuidGenerator.randomUuid().toString().replace("-", "");
    }

    public Optional<ConferenceResponse> insert(UUID chatId, UUID createdBy, String title, String roomSlug) {
        var sql = """
            INSERT INTO conferences (chat_id, created_by, title, room_slug)
            VALUES (?, ?, ?, ?)
            RETURNING id, chat_id, title, status, room_slug, created_at, ended_at
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setObject(2, createdBy);
            stmt.setString(3, title != null ? title : "");
            stmt.setString(4, roomSlug);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("insert conference failed", e);
        }
        return Optional.empty();
    }

    public Optional<ConferenceResponse> findById(UUID conferenceId) {
        var sql = """
            SELECT id, chat_id, title, status, room_slug, created_at, ended_at
            FROM conferences WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, conferenceId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("find conference {}", conferenceId, e);
        }
        return Optional.empty();
    }

    public List<ConferenceResponse> listForChat(UUID chatId, boolean activeOnly) {
        var sql = """
            SELECT id, chat_id, title, status, room_slug, created_at, ended_at
            FROM conferences WHERE chat_id = ?
            """ + (activeOnly ? " AND status = 'active' " : "") + " ORDER BY created_at DESC";
        var list = new ArrayList<ConferenceResponse>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("list conferences chat {}", chatId, e);
        }
        return list;
    }

    public boolean join(UUID conferenceId, UUID userId) {
        var sql = """
            INSERT INTO conference_participants (conference_id, user_id, joined_at)
            VALUES (?, ?, now())
            ON CONFLICT (conference_id, user_id) DO UPDATE SET left_at = NULL, joined_at = now()
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, conferenceId);
            stmt.setObject(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("join conference", e);
            return false;
        }
    }

    public boolean leave(UUID conferenceId, UUID userId) {
        var sql = """
            UPDATE conference_participants SET left_at = now()
            WHERE conference_id = ? AND user_id = ? AND left_at IS NULL
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, conferenceId);
            stmt.setObject(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("leave conference", e);
            return false;
        }
    }

    public Optional<UUID> findCreatorId(UUID conferenceId) {
        var sql = "SELECT created_by FROM conferences WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, conferenceId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getObject("created_by", UUID.class));
                }
            }
        } catch (Exception e) {
            log.error("findCreatorId", e);
        }
        return Optional.empty();
    }

    public boolean endConference(UUID conferenceId) {
        var sql = "UPDATE conferences SET status = 'ended', ended_at = now() WHERE id = ? AND status = 'active'";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, conferenceId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("end conference", e);
            return false;
        }
    }

    private ConferenceResponse mapRow(java.sql.ResultSet rs) throws Exception {
        var id = rs.getObject("id", UUID.class).toString();
        var chatId = rs.getObject("chat_id", UUID.class).toString();
        var slug = rs.getString("room_slug");
        var ended = rs.getTimestamp("ended_at");
        return new ConferenceResponse(
            id,
            chatId,
            rs.getString("title"),
            rs.getString("status"),
            slug,
            buildJoinUrl(slug),
            "jitsi",
            rs.getTimestamp("created_at").toInstant(),
            ended != null ? ended.toInstant() : null
        );
    }

    private String buildJoinUrl(String roomSlug) {
        var base = appConfig.jitsiMeetBaseUrl().replaceAll("/$", "");
        return base + "/" + roomSlug;
    }
}
