package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.SavedChatPort;
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

    public JdbcSavedChatAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<ChatId> getSavedChatId(UserId userId) {
        if (dataSource == null) {
            return Optional.empty();
        }
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(SELECT_SAVED_CHAT)) {
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
}
