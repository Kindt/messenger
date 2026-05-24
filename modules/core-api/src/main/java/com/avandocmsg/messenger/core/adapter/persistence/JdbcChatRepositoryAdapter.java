package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.domain.Chat;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.ChatType;
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

    private static Chat mapRow(java.sql.ResultSet rs) throws Exception {
        var typeRaw = rs.getString("type");
        var type = "p2p".equalsIgnoreCase(typeRaw) ? ChatType.P2P
            : "saved".equalsIgnoreCase(typeRaw) ? ChatType.SAVED : ChatType.GROUP;
        return new Chat(
            ChatId.of(rs.getObject("id", UUID.class)),
            rs.getString("title"),
            type,
            rs.getTimestamp("created_at").toInstant());
    }
}
