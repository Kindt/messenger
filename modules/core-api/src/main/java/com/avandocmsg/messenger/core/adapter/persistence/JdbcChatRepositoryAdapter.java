package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.domain.Chat;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.ChatType;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.ChatRepositoryPort;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

/** JDBC adapter for {@link ChatRepositoryPort} (reads {@code chats} without membership filter). */
public final class JdbcChatRepositoryAdapter implements ChatRepositoryPort {
    private final DataSource dataSource;

    public JdbcChatRepositoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Chat> findById(ChatId id) {
        if (dataSource == null) {
            return Optional.empty();
        }
        var sql = """
            SELECT id, title, type, created_at FROM chats WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id.value());
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    @Override
    public boolean isMember(ChatId chatId, UserId userId) {
        return memberRole(chatId, userId).isPresent();
    }

    @Override
    public Optional<String> memberRole(ChatId chatId, UserId userId) {
        if (dataSource == null || chatId == null || userId == null) {
            return Optional.empty();
        }
        var sql = "SELECT role FROM chat_members WHERE chat_id = ? AND user_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId.value());
            stmt.setObject(2, userId.value());
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("role"));
                }
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    @Override
    public boolean isMemberBanned(ChatId chatId, UserId userId) {
        if (dataSource == null || chatId == null || userId == null) {
            return false;
        }
        var sql = "SELECT banned FROM chat_members WHERE chat_id = ? AND user_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId.value());
            stmt.setObject(2, userId.value());
            try (var rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean("banned");
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Optional<UserId> findOtherP2pMember(ChatId chatId, UserId userId) {
        if (dataSource == null || chatId == null || userId == null) {
            return Optional.empty();
        }
        var sql = """
            SELECT cm.user_id
            FROM chat_members cm
            INNER JOIN chats c ON c.id = cm.chat_id AND c.type = 'p2p'
            WHERE cm.chat_id = ? AND cm.user_id <> ?
            LIMIT 1
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId.value());
            stmt.setObject(2, userId.value());
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(UserId.of(rs.getObject("user_id", UUID.class)));
                }
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static Chat mapRow(java.sql.ResultSet rs) throws Exception {
        var typeRaw = rs.getString("type");
        var type = mapChatType(typeRaw);
        return new Chat(
            ChatId.of(rs.getObject("id", UUID.class)),
            rs.getString("title"),
            type,
            rs.getTimestamp("created_at").toInstant());
    }

    private static ChatType mapChatType(String typeRaw) {
        if (typeRaw == null) {
            return ChatType.GROUP;
        }
        if ("p2p".equalsIgnoreCase(typeRaw)) {
            return ChatType.P2P;
        }
        if ("saved".equalsIgnoreCase(typeRaw)) {
            return ChatType.SAVED;
        }
        if ("channel".equalsIgnoreCase(typeRaw)) {
            return ChatType.CHANNEL;
        }
        return ChatType.GROUP;
    }
}
