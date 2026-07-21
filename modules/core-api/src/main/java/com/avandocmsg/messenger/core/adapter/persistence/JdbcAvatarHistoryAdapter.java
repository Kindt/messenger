package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.AvatarHistoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/** JDBC adapter for {@link avatar_history} inserts. */
public final class JdbcAvatarHistoryAdapter implements AvatarHistoryPort {
    private static final Logger log = LoggerFactory.getLogger(JdbcAvatarHistoryAdapter.class);

    private static final String INSERT = """
        INSERT INTO avatar_history (entity_type, entity_id, file_id, set_by_user_id)
        VALUES (?, ?, ?, ?)
        """;

    private final DataSource dataSource;

    public JdbcAvatarHistoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void recordUserAvatar(UserId entityId, FileId fileId, UserId setByUserId) {
        insert("user", entityId.value(), fileId.value(), setByUserId != null ? setByUserId.value() : null);
    }

    @Override
    public void recordChatAvatar(ChatId entityId, FileId fileId, UserId setByUserId) {
        insert("chat", entityId.value(), fileId.value(), setByUserId != null ? setByUserId.value() : null);
    }

    private void insert(String entityType, java.util.UUID entityId, java.util.UUID fileId, java.util.UUID setBy) {
        if (entityId == null || fileId == null) {
            return;
        }
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(INSERT)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, entityType);
            stmt.setObject(2, entityId);
            stmt.setObject(3, fileId);
            if (setBy != null) {
                stmt.setObject(4, setBy);
            } else {
                stmt.setObject(4, null);
            }
            stmt.executeUpdate();
        } catch (Exception e) {
            log.error("avatar history insert failed for {} {}", entityType, entityId, e);
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }
}
