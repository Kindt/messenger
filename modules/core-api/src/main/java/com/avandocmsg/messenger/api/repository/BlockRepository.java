package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.blocks.dto.BlockedUserResponse;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcBlockRepositoryAdapter;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.BlockRepositoryPort;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

/**
 * Legacy façade for block JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcBlockRepositoryAdapter}.
 */
public class BlockRepository {
    private final BlockRepositoryPort port;

    public BlockRepository(DataSource dataSource) {
        this.port = new JdbcBlockRepositoryAdapter(dataSource);
    }

    BlockRepository(BlockRepositoryPort port) {
        this.port = port;
    }

    public boolean exists(UUID blockerId, UUID blockedId) {
        return port.exists(UserId.of(blockerId), UserId.of(blockedId));
    }

    public boolean block(UUID blockerId, UUID blockedId) {
        return port.block(UserId.of(blockerId), UserId.of(blockedId));
    }

    public boolean unblock(UUID blockerId, UUID blockedId) {
        return port.unblock(UserId.of(blockerId), UserId.of(blockedId));
    }

    public List<BlockedUserResponse> listBlockedUsers(UUID blockerId) {
        return port.listBlockedUsers(UserId.of(blockerId)).stream()
            .map(u -> new BlockedUserResponse(
                u.userId().value().toString(),
                u.username(),
                u.displayName(),
                u.blockedAt()))
            .toList();
    }
}
