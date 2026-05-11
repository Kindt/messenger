package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.chats.bans.dto.ChatBanResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.avandocmsg.messenger.core.port.UuidGenerator;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ChatBanRepository {
    private static final Logger log = LoggerFactory.getLogger(ChatBanRepository.class);
    private final DataSource dataSource;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;

    public ChatBanRepository(DataSource dataSource, Clock clock, UuidGenerator uuidGenerator) {
        this.dataSource = dataSource;
        this.clock = clock;
        this.uuidGenerator = uuidGenerator;
    }

    public ChatBanResponse ban(UUID chatId, UUID userId, UUID bannedBy, String reason) {
        var sql = "INSERT INTO chat_bans (id, chat_id, user_id, banned_by, reason, created_at) VALUES (?, ?, ?, ?, ?, now())";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            var id = uuidGenerator.randomUuid();
            stmt.setObject(1, id);
            stmt.setObject(2, chatId);
            stmt.setObject(3, userId);
            stmt.setObject(4, bannedBy);
            stmt.setString(5, reason != null ? reason : "");
            stmt.executeUpdate();
            return new ChatBanResponse(id.toString(), chatId.toString(), userId.toString(),
                bannedBy.toString(), reason, clock.instant());
        } catch (Exception e) {
            log.error("Failed to ban user {} from chat {}", userId, chatId, e);
            return null;
        }
    }

    public Optional<ChatBanResponse> findById(UUID id) {
        var sql = "SELECT id, chat_id, user_id, banned_by, reason, created_at FROM chat_bans WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapBan(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find ban {}", id, e);
        }
        return Optional.empty();
    }

    public List<ChatBanResponse> findByChatId(UUID chatId) {
        var sql = "SELECT id, chat_id, user_id, banned_by, reason, created_at FROM chat_bans WHERE chat_id = ? ORDER BY created_at DESC";
        var result = new ArrayList<ChatBanResponse>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapBan(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to list bans for chat {}", chatId, e);
        }
        return result;
    }

    public boolean unban(UUID chatId, UUID userId) {
        var sql = "DELETE FROM chat_bans WHERE chat_id = ? AND user_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setObject(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to unban user {} from chat {}", userId, chatId, e);
            return false;
        }
    }

    public boolean isBanned(UUID chatId, UUID userId) {
        var sql = "SELECT 1 FROM chat_bans WHERE chat_id = ? AND user_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setObject(2, userId);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.error("Failed to check ban status", e);
            return false;
        }
    }

    private ChatBanResponse mapBan(java.sql.ResultSet rs) throws Exception {
        return new ChatBanResponse(
            rs.getObject("id", UUID.class).toString(),
            rs.getObject("chat_id", UUID.class).toString(),
            rs.getObject("user_id", UUID.class).toString(),
            rs.getObject("banned_by", UUID.class).toString(),
            rs.getString("reason"),
            rs.getTimestamp("created_at").toInstant()
        );
    }
}
