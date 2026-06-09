package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.domain.UserProfile;

import java.util.Optional;

/** Port for user profile reads (Phase 2c). */
public interface UserRepositoryPort {
    Optional<UserProfile> findById(UserId id);
}
