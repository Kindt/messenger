package com.avandocmsg.messenger.api.mls;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class MlsGroupStateRepository {
    private static final Logger log = LoggerFactory.getLogger(MlsGroupStateRepository.class);
    private final DataSource dataSource;
    private final Clock clock;

    public MlsGroupStateRepository(DataSource dataSource, Clock clock) {
        this.dataSource = dataSource;
        this.clock = clock;
    }

    public boolean save(MlsGroupState state) {
        if (dataSource == null || state == null) {
            return false;
        }
        var sql = """
            INSERT INTO mls_group_state (group_id, chat_id, epoch, tree_data, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (group_id) DO UPDATE SET
              epoch = EXCLUDED.epoch,
              tree_data = EXCLUDED.tree_data,
              updated_at = EXCLUDED.updated_at
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, state.groupId());
            stmt.setObject(2, state.chatId());
            stmt.setLong(3, state.epoch());
            stmt.setBytes(4, state.treeData() != null ? state.treeData() : new byte[0]);
            stmt.setTimestamp(5, Timestamp.from(state.createdAt() != null ? state.createdAt() : clock.instant()));
            stmt.setTimestamp(6, Timestamp.from(state.updatedAt() != null ? state.updatedAt() : clock.instant()));
            stmt.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("save mls_group_state failed groupId={}", state.groupId(), e);
            return false;
        }
    }

    public Optional<MlsGroupState> findByGroupId(UUID groupId) {
        return findOne("group_id = ?", groupId);
    }

    public Optional<MlsGroupState> findByChatId(UUID chatId) {
        return findOne("chat_id = ?", chatId);
    }

    public long countAll() {
        if (dataSource == null) {
            return 0L;
        }
        var sql = "SELECT COUNT(*) AS c FROM mls_group_state";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong("c");
            }
        } catch (Exception e) {
            log.error("countAll mls_group_state failed", e);
        }
        return 0L;
    }

    public boolean deleteByGroupId(UUID groupId) {
        if (dataSource == null) {
            return false;
        }
        var sql = "DELETE FROM mls_group_state WHERE group_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, groupId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("deleteByGroupId failed groupId={}", groupId, e);
            return false;
        }
    }

    private Optional<MlsGroupState> findOne(String whereColumn, UUID id) {
        if (dataSource == null) {
            return Optional.empty();
        }
        var sql = """
            SELECT group_id, chat_id, epoch, tree_data, created_at, updated_at
            FROM mls_group_state WHERE """ + whereColumn;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("findOne mls_group_state failed", e);
        }
        return Optional.empty();
    }

    private MlsGroupState mapRow(java.sql.ResultSet rs) throws Exception {
        return new MlsGroupState(
            rs.getObject("group_id", UUID.class),
            rs.getObject("chat_id", UUID.class),
            rs.getLong("epoch"),
            rs.getBytes("tree_data"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
    }
}
