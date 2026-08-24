package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.core.domain.BlockedUser;
import com.avandocmsg.messenger.core.domain.UserId;

import java.util.List;

/** Outbound persistence for user block relationships. */
public interface BlockRepositoryPort {

    /** Test-only / legacy ctor wiring — production must inject {@link com.avandocmsg.messenger.core.adapter.persistence.JdbcBlockRepositoryAdapter}. */
    BlockRepositoryPort NOOP = new BlockRepositoryPort() {
        @Override
        public boolean exists(UserId blockerId, UserId blockedId) {
            return false;
        }

        @Override
        public boolean block(UserId blockerId, UserId blockedId) {
            return false;
        }

        @Override
        public boolean unblock(UserId blockerId, UserId blockedId) {
            return false;
        }

        @Override
        public java.util.List<BlockedUser> listBlockedUsers(UserId blockerId) {
            return java.util.List.of();
        }
    };

    boolean exists(UserId blockerId, UserId blockedId);

    boolean block(UserId blockerId, UserId blockedId);

    boolean unblock(UserId blockerId, UserId blockedId);

    List<BlockedUser> listBlockedUsers(UserId blockerId);
}
