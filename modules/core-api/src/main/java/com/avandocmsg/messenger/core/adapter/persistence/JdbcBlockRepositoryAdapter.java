package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.repository.BlockRepository;
import com.avandocmsg.messenger.core.domain.BlockedUser;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.BlockRepositoryPort;

import javax.sql.DataSource;
import java.util.List;

/** JDBC adapter for {@link BlockRepositoryPort}. */
public final class JdbcBlockRepositoryAdapter implements BlockRepositoryPort {
    private final BlockRepository blockRepository;

    public JdbcBlockRepositoryAdapter(DataSource dataSource) {
        this.blockRepository = new BlockRepository(dataSource);
    }

    public JdbcBlockRepositoryAdapter(BlockRepository blockRepository) {
        this.blockRepository = blockRepository;
    }

    @Override
    public boolean exists(UserId blockerId, UserId blockedId) {
        return blockRepository.exists(blockerId.value(), blockedId.value());
    }

    @Override
    public boolean block(UserId blockerId, UserId blockedId) {
        return blockRepository.block(blockerId.value(), blockedId.value());
    }

    @Override
    public boolean unblock(UserId blockerId, UserId blockedId) {
        return blockRepository.unblock(blockerId.value(), blockedId.value());
    }

    @Override
    public List<BlockedUser> listBlockedUsers(UserId blockerId) {
        return blockRepository.listBlockedUsers(blockerId.value()).stream()
            .map(row -> new BlockedUser(
                UserId.of(java.util.UUID.fromString(row.userId())),
                row.username(),
                row.displayName(),
                row.blockedAt()))
            .toList();
    }
}
