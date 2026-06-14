package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.domain.UserProfile;

import java.util.Optional;

/** Port for user profile reads and writes (Phase 2c / US2). */
public interface UserRepositoryPort {
    Optional<UserProfile> findById(UserId id);

    boolean updateProfile(UserId id, String displayName, String phone);

    boolean updatePresence(UserId id, String presenceStatus);

    boolean updatePrivacy(UserId id, boolean disableReadReceipts);

    boolean updateUiLocale(UserId id, String uiLocale);

    boolean touchHeartbeat(UserId id);
}
