package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.common.jdbc.JdbcConnectionSupport;
import com.avandocmsg.messenger.core.port.SavedChatPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

/** JDBC adapter for {@link SavedChatPort}. */
public final class JdbcSavedChatAdapter implements SavedChatPort {
    private static final Logger log = LoggerFactory.getLogger(JdbcSavedChatAdapter.class);

    private static final String SELECT_SAVED_CHAT = """
        SELECT c.id FROM chats c
        INNER JOIN chat_members cm ON cm.chat_id = c.id
        WHERE c.type = 'saved' AND cm.user_id = ?
        LIMIT 1
        """;

    private final DataSource dataSource;
    private final UuidGenerator uuidGenerator;

    public JdbcSavedChatAdapter(DataSource dataSource, UuidGenerator uuidGenerator) {
        this.dataSource = dataSource;
        this.uuidGenerator = uuidGenerator;
    }

    @Override
    public Optional<ChatId> getSavedChatId(UserId userId) {
        if (dataSource == null) {
            return Optional.empty();
        }
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(SELECT_SAVED_CHAT)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, userId.value());
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(ChatId.of(rs.getObject("id", UUID.class)));
                }
            }
        } catch (Exception e) {
            log.error("getSavedChatId failed for user {}", userId, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<ChatId> ensureSavedVaultChat(UserId userId) {
        var existing = getSavedChatId(userId);
        if (existing.isPresent()) {
            return existing;
        }
        if (dataSource == null || uuidGenerator == null) {
            return Optional.empty();
        }
        try (var conn = dataSource.getConnection()) {
            JdbcConnectionSupport.beginTransaction(conn);
            try {
                var chatId = uuidGenerator.randomUuid();
                try (var stmt = conn.prepareStatement(
                    "INSERT INTO chats (id, title, type, owner_id, created_at, updated_at) VALUES (?, ?, 'saved', ?, now(), now())")) {
                    JdbcQuerySupport.applyDefaultTimeout(stmt);
                    stmt.setObject(1, chatId);
                    stmt.setString(2, "Saved Messages");
                    stmt.setObject(3, userId.value());
                    stmt.executeUpdate();
                }
                try (var stmt = conn.prepareStatement(
                    "INSERT INTO chat_members (chat_id, user_id, role, joined_at) VALUES (?, ?, ?, now())")) {
                    JdbcQuerySupport.applyDefaultTimeout(stmt);
                    stmt.setObject(1, chatId);
                    stmt.setObject(2, userId.value());
                    stmt.setString(3, "owner");
                    stmt.executeUpdate();
                }
                conn.commit();
                log.info("Created saved vault chat {} for user {}", chatId, userId);
                return Optional.of(ChatId.of(chatId));
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            log.error("ensureSavedVaultChat failed for {}", userId, e);
            return Optional.empty();
        }
    }
}
