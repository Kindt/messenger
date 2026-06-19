package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.core.domain.BlockedUser;
import com.avandocmsg.messenger.core.domain.UserId;

import java.util.List;

/** Outbound persistence for user block relationships. */
public interface BlockRepositoryPort {
    boolean exists(UserId blockerId, UserId blockedId);

    boolean block(UserId blockerId, UserId blockedId);

    boolean unblock(UserId blockerId, UserId blockedId);

    List<BlockedUser> listBlockedUsers(UserId blockerId);
}
