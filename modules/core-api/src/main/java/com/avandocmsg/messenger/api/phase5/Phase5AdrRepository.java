package com.avandocmsg.messenger.api.phase5;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC scaffold for spec 022 Phase 5 ADR backlog (repo MVP). */
public final class Phase5AdrRepository {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DataSource dataSource;

    public Phase5AdrRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<StickerPackRow> listStickerPacks(UUID orgId) {
        var sql = """
            SELECT id, org_id, name, created_at FROM sticker_packs
            WHERE org_id = ? ORDER BY created_at DESC LIMIT 100
            """;
        var out = new ArrayList<StickerPackRow>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, orgId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(new StickerPackRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("org_id", UUID.class),
                        rs.getString("name"),
                        rs.getTimestamp("created_at").toInstant()));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("listStickerPacks failed", e);
        }
        return out;
    }

    public UUID createStickerPack(UUID orgId, String name) {
        var id = UUID.randomUUID();
        var sql = "INSERT INTO sticker_packs (id, org_id, name) VALUES (?, ?, ?)";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setObject(2, orgId);
            stmt.setString(3, name);
            stmt.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new IllegalStateException("createStickerPack failed", e);
        }
    }

    public List<GifRow> searchGifs(UUID orgId, String query) {
        var sql = """
            SELECT id, org_id, query_key, preview_url, gif_url, created_at FROM gif_catalog_entries
            WHERE (org_id IS NULL OR org_id = ?) AND LOWER(query_key) LIKE ?
            ORDER BY created_at DESC LIMIT 50
            """;
        var out = new ArrayList<GifRow>();
        var pattern = "%" + (query == null ? "" : query.trim().toLowerCase()) + "%";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, orgId);
            stmt.setString(2, pattern);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(new GifRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("org_id", UUID.class),
                        rs.getString("query_key"),
                        rs.getString("preview_url"),
                        rs.getString("gif_url"),
                        rs.getTimestamp("created_at").toInstant()));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("searchGifs failed", e);
        }
        return out;
    }

    public UUID startRecording(UUID conferenceId, UUID chatId, UUID userId) {
        var id = UUID.randomUUID();
        var sql = """
            INSERT INTO call_recordings (id, conference_id, chat_id, started_by, status)
            VALUES (?, ?, ?, ?, 'recording')
            """;
        exec(id, conferenceId, chatId, userId, sql);
        return id;
    }

    public List<RecordingRow> listRecordings(UUID conferenceId) {
        var sql = """
            SELECT id, conference_id, chat_id, started_by, status, storage_key, created_at
            FROM call_recordings WHERE conference_id = ? ORDER BY created_at DESC
            """;
        return mapRecordings(sql, conferenceId);
    }

    public GuestLinkRow createGuestLink(UUID conferenceId, UUID chatId, boolean waitingRoom, Instant expiresAt) {
        var id = UUID.randomUUID();
        var token = UUID.randomUUID().toString().replace("-", "");
        var hash = sha256(token);
        var sql = """
            INSERT INTO conference_guest_links (id, conference_id, chat_id, token_hash, waiting_room, expires_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setObject(2, conferenceId);
            stmt.setObject(3, chatId);
            stmt.setString(4, hash);
            stmt.setBoolean(5, waitingRoom);
            if (expiresAt != null) {
                stmt.setTimestamp(6, Timestamp.from(expiresAt));
            } else {
                stmt.setNull(6, java.sql.Types.TIMESTAMP);
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("createGuestLink failed", e);
        }
        return new GuestLinkRow(id, conferenceId, chatId, token, waitingRoom, expiresAt, Instant.now());
    }

    public UUID createBreakout(UUID parentConferenceId, UUID chatId, String name, String livekitRoom) {
        var id = UUID.randomUUID();
        var sql = """
            INSERT INTO conference_breakout_rooms (id, parent_conference_id, chat_id, name, livekit_room)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setObject(2, parentConferenceId);
            stmt.setObject(3, chatId);
            stmt.setString(4, name);
            stmt.setString(5, livekitRoom);
            stmt.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new IllegalStateException("createBreakout failed", e);
        }
    }

    public List<BreakoutRow> listBreakouts(UUID parentConferenceId) {
        var sql = """
            SELECT id, parent_conference_id, chat_id, name, livekit_room, created_at
            FROM conference_breakout_rooms WHERE parent_conference_id = ? ORDER BY created_at
            """;
        var out = new ArrayList<BreakoutRow>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, parentConferenceId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(new BreakoutRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("parent_conference_id", UUID.class),
                        rs.getObject("chat_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("livekit_room"),
                        rs.getTimestamp("created_at").toInstant()));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("listBreakouts failed", e);
        }
        return out;
    }

    public Optional<WhiteboardRow> getWhiteboard(UUID chatId) {
        var sql = """
            SELECT id, chat_id, created_by, title, snapshot_json, updated_at
            FROM chat_whiteboards WHERE chat_id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapWhiteboard(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("getWhiteboard failed", e);
        }
    }

    public WhiteboardRow upsertWhiteboard(UUID chatId, UUID userId, String title, String snapshotJson) {
        var existing = getWhiteboard(chatId);
        if (existing.isPresent()) {
            var sql = """
                UPDATE chat_whiteboards SET title = ?, snapshot_json = ?, updated_at = CURRENT_TIMESTAMP
                WHERE chat_id = ?
                """;
            try (var conn = dataSource.getConnection();
                 var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, title);
                stmt.setString(2, snapshotJson);
                stmt.setObject(3, chatId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("upsertWhiteboard update failed", e);
            }
            return getWhiteboard(chatId).orElseThrow();
        }
        var id = UUID.randomUUID();
        var sql = """
            INSERT INTO chat_whiteboards (id, chat_id, created_by, title, snapshot_json)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setObject(2, chatId);
            stmt.setObject(3, userId);
            stmt.setString(4, title);
            stmt.setString(5, snapshotJson);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("upsertWhiteboard insert failed", e);
        }
        return getWhiteboard(chatId).orElseThrow();
    }

    public UUID createKanbanTask(UUID chatId, UUID userId, String columnKey, String title, UUID assigneeId) {
        var id = UUID.randomUUID();
        var sql = """
            INSERT INTO chat_kanban_tasks (id, chat_id, column_key, title, assignee_id, created_by)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setObject(2, chatId);
            stmt.setString(3, columnKey != null ? columnKey : "todo");
            stmt.setString(4, title);
            if (assigneeId != null) {
                stmt.setObject(5, assigneeId);
            } else {
                stmt.setNull(5, java.sql.Types.OTHER);
            }
            stmt.setObject(6, userId);
            stmt.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new IllegalStateException("createKanbanTask failed", e);
        }
    }

    public List<KanbanTaskRow> listKanbanTasks(UUID chatId) {
        var sql = """
            SELECT id, chat_id, column_key, title, assignee_id, created_by, sort_order, created_at
            FROM chat_kanban_tasks WHERE chat_id = ? ORDER BY column_key, sort_order, created_at
            """;
        var out = new ArrayList<KanbanTaskRow>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(mapKanbanTask(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("listKanbanTasks failed", e);
        }
        return out;
    }

    public Optional<KanbanTaskRow> getKanbanTask(UUID taskId, UUID chatId) {
        var sql = """
            SELECT id, chat_id, column_key, title, assignee_id, created_by, sort_order, created_at
            FROM chat_kanban_tasks WHERE id = ? AND chat_id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, taskId);
            stmt.setObject(2, chatId);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapKanbanTask(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("getKanbanTask failed", e);
        }
    }

    public Optional<KanbanTaskRow> updateKanbanTask(
        UUID taskId,
        UUID chatId,
        String columnKey,
        Integer sortOrder,
        String title
    ) {
        var sets = new ArrayList<String>();
        if (columnKey != null && !columnKey.isBlank()) {
            sets.add("column_key = ?");
        }
        if (sortOrder != null) {
            sets.add("sort_order = ?");
        }
        if (title != null && !title.isBlank()) {
            sets.add("title = ?");
        }
        if (sets.isEmpty()) {
            return getKanbanTask(taskId, chatId);
        }
        var sql = "UPDATE chat_kanban_tasks SET " + String.join(", ", sets) + " WHERE id = ? AND chat_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            var idx = 1;
            if (columnKey != null && !columnKey.isBlank()) {
                stmt.setString(idx++, columnKey.trim());
            }
            if (sortOrder != null) {
                stmt.setInt(idx++, sortOrder);
            }
            if (title != null && !title.isBlank()) {
                stmt.setString(idx++, title.trim());
            }
            stmt.setObject(idx++, taskId);
            stmt.setObject(idx, chatId);
            if (stmt.executeUpdate() == 0) {
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("updateKanbanTask failed", e);
        }
        return getKanbanTask(taskId, chatId);
    }

    public boolean deleteKanbanTask(UUID taskId, UUID chatId) {
        var sql = "DELETE FROM chat_kanban_tasks WHERE id = ? AND chat_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, taskId);
            stmt.setObject(2, chatId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("deleteKanbanTask failed", e);
        }
    }

    public Optional<GuestLinkLookupRow> findGuestLinkByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        var hash = sha256(token.trim());
        var sql = """
            SELECT id, conference_id, chat_id, waiting_room, expires_at, created_at
            FROM conference_guest_links WHERE token_hash = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, hash);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new GuestLinkLookupRow(
                    rs.getObject("id", UUID.class),
                    rs.getObject("conference_id", UUID.class),
                    rs.getObject("chat_id", UUID.class),
                    rs.getBoolean("waiting_room"),
                    rs.getTimestamp("expires_at") != null
                        ? rs.getTimestamp("expires_at").toInstant()
                        : null,
                    rs.getTimestamp("created_at").toInstant()));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("findGuestLinkByToken failed", e);
        }
    }

    private static KanbanTaskRow mapKanbanTask(java.sql.ResultSet rs) throws SQLException {
        return new KanbanTaskRow(
            rs.getObject("id", UUID.class),
            rs.getObject("chat_id", UUID.class),
            rs.getString("column_key"),
            rs.getString("title"),
            rs.getObject("assignee_id", UUID.class),
            rs.getObject("created_by", UUID.class),
            rs.getInt("sort_order"),
            rs.getTimestamp("created_at").toInstant());
    }

    public Optional<SipGatewayRow> getSipGateway(UUID orgId) {
        var sql = "SELECT org_id, enabled, gateway_uri, h323_enabled, updated_at FROM org_sip_gateway WHERE org_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, orgId);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new SipGatewayRow(
                    rs.getObject("org_id", UUID.class),
                    rs.getBoolean("enabled"),
                    rs.getString("gateway_uri"),
                    rs.getBoolean("h323_enabled"),
                    rs.getTimestamp("updated_at").toInstant()));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("getSipGateway failed", e);
        }
    }

    public SipGatewayRow upsertSipGateway(UUID orgId, boolean enabled, String gatewayUri, boolean h323) {
        var sql = """
            INSERT INTO org_sip_gateway (org_id, enabled, gateway_uri, h323_enabled, updated_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (org_id) DO UPDATE SET
              enabled = EXCLUDED.enabled,
              gateway_uri = EXCLUDED.gateway_uri,
              h323_enabled = EXCLUDED.h323_enabled,
              updated_at = CURRENT_TIMESTAMP
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, orgId);
            stmt.setBoolean(2, enabled);
            stmt.setString(3, gatewayUri);
            stmt.setBoolean(4, h323);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("upsertSipGateway failed", e);
        }
        return getSipGateway(orgId).orElseThrow();
    }

    public List<PasskeyRow> listPasskeys(UUID userId) {
        var sql = """
            SELECT id, user_id, credential_id, public_key, created_at
            FROM user_passkey_credentials WHERE user_id = ? ORDER BY created_at DESC
            """;
        var out = new ArrayList<PasskeyRow>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(new PasskeyRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("credential_id"),
                        rs.getString("public_key"),
                        rs.getTimestamp("created_at").toInstant()));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("listPasskeys failed", e);
        }
        return out;
    }

    public UUID registerPasskeyScaffold(UUID userId, String credentialId, String publicKey) {
        var id = UUID.randomUUID();
        var sql = """
            INSERT INTO user_passkey_credentials (id, user_id, credential_id, public_key)
            VALUES (?, ?, ?, ?)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setObject(2, userId);
            stmt.setString(3, credentialId);
            stmt.setString(4, publicKey);
            stmt.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new IllegalStateException("registerPasskeyScaffold failed", e);
        }
    }

    public CaptionSessionRow startCaptions(UUID conferenceId, UUID chatId, String language) {
        var id = UUID.randomUUID();
        var sql = """
            INSERT INTO live_caption_sessions (id, conference_id, chat_id, status, language)
            VALUES (?, ?, ?, 'active', ?)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setObject(2, conferenceId);
            stmt.setObject(3, chatId);
            stmt.setString(4, language != null ? language : "ru");
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("startCaptions failed", e);
        }
        return getLatestCaptionSession(conferenceId).orElseThrow();
    }

    public Optional<CaptionSessionRow> getLatestCaptionSession(UUID conferenceId) {
        var sql = """
            SELECT id, conference_id, chat_id, status, language, transcript_json, created_at
            FROM live_caption_sessions WHERE conference_id = ? ORDER BY created_at DESC LIMIT 1
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, conferenceId);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapCaption(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("getLatestCaptionSession failed", e);
        }
    }

    public void appendCaptionTranscript(UUID sessionId, String transcriptJson) {
        var sql = "UPDATE live_caption_sessions SET transcript_json = ? WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, transcriptJson);
            stmt.setObject(2, sessionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("appendCaptionTranscript failed", e);
        }
    }

    public void seedDefaultGifs(UUID orgId) {
        if (!searchGifs(orgId, "ok").isEmpty()) {
            return;
        }
        var sql = """
            INSERT INTO gif_catalog_entries (id, org_id, query_key, preview_url, gif_url)
            VALUES (?, ?, ?, ?, ?)
            """;
        var rows = List.of(
            new String[] {"thumbsup", "/static/gif/thumbsup-preview.png", "/static/gif/thumbsup.gif"},
            new String[] {"clap", "/static/gif/clap-preview.png", "/static/gif/clap.gif"}
        );
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            for (var row : rows) {
                stmt.setObject(1, UUID.randomUUID());
                stmt.setObject(2, orgId);
                stmt.setString(3, row[0]);
                stmt.setString(4, row[1]);
                stmt.setString(5, row[2]);
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("seedDefaultGifs failed", e);
        }
    }

    private List<RecordingRow> mapRecordings(String sql, UUID conferenceId) {
        var out = new ArrayList<RecordingRow>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, conferenceId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(new RecordingRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("conference_id", UUID.class),
                        rs.getObject("chat_id", UUID.class),
                        rs.getObject("started_by", UUID.class),
                        rs.getString("status"),
                        rs.getString("storage_key"),
                        rs.getTimestamp("created_at").toInstant()));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("mapRecordings failed", e);
        }
        return out;
    }

    private void exec(UUID id, UUID conferenceId, UUID chatId, UUID userId, String sql) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setObject(2, conferenceId);
            stmt.setObject(3, chatId);
            stmt.setObject(4, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("exec failed", e);
        }
    }

    private static WhiteboardRow mapWhiteboard(java.sql.ResultSet rs) throws SQLException {
        return new WhiteboardRow(
            rs.getObject("id", UUID.class),
            rs.getObject("chat_id", UUID.class),
            rs.getObject("created_by", UUID.class),
            rs.getString("title"),
            rs.getString("snapshot_json"),
            rs.getTimestamp("updated_at").toInstant());
    }

    private static CaptionSessionRow mapCaption(java.sql.ResultSet rs) throws SQLException {
        return new CaptionSessionRow(
            rs.getObject("id", UUID.class),
            rs.getObject("conference_id", UUID.class),
            rs.getObject("chat_id", UUID.class),
            rs.getString("status"),
            rs.getString("language"),
            rs.getString("transcript_json"),
            rs.getTimestamp("created_at").toInstant());
    }

    private static String sha256(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record StickerPackRow(UUID id, UUID orgId, String name, Instant createdAt) {}
    public record GifRow(UUID id, UUID orgId, String queryKey, String previewUrl, String gifUrl, Instant createdAt) {}
    public record RecordingRow(UUID id, UUID conferenceId, UUID chatId, UUID startedBy, String status,
                               String storageKey, Instant createdAt) {}
    public record GuestLinkRow(UUID id, UUID conferenceId, UUID chatId, String guestToken,
                               boolean waitingRoom, Instant expiresAt, Instant createdAt) {}
    public record GuestLinkLookupRow(UUID id, UUID conferenceId, UUID chatId, boolean waitingRoom,
                                     Instant expiresAt, Instant createdAt) {}
    public record BreakoutRow(UUID id, UUID parentConferenceId, UUID chatId, String name,
                              String livekitRoom, Instant createdAt) {}
    public record WhiteboardRow(UUID id, UUID chatId, UUID createdBy, String title,
                                String snapshotJson, Instant updatedAt) {}
    public record KanbanTaskRow(UUID id, UUID chatId, String columnKey, String title, UUID assigneeId,
                                UUID createdBy, int sortOrder, Instant createdAt) {}
    public record SipGatewayRow(UUID orgId, boolean enabled, String gatewayUri, boolean h323Enabled, Instant updatedAt) {}
    public record PasskeyRow(UUID id, UUID userId, String credentialId, String publicKey, Instant createdAt) {}
    public record CaptionSessionRow(UUID id, UUID conferenceId, UUID chatId, String status, String language,
                                    String transcriptJson, Instant createdAt) {}
}
