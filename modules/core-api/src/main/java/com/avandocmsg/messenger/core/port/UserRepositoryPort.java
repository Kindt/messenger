package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.domain.UserProfile;

import java.util.Optional;

/** Port for user profile reads and writes (Phase 2c / US2). */
public interface UserRepositoryPort {
    Optional<UserProfile> findById(UserId id);

    boolean updateProfile(UserId id, String displayName, String phone);

    boolean updatePresence(UserId id, String presenceStatus);

    boolean updateUserStatus(UserId id, String presenceStatus, String customStatusText,
                             java.time.Instant dndUntil, boolean clearDndUntil);

    boolean updatePrivacy(UserId id, boolean disableReadReceipts);

    boolean updateUiLocale(UserId id, String uiLocale);

    boolean touchHeartbeat(UserId id);

    /** After Keycloak login: upsert local user row from JWT claims. */
    void upsertFromKeycloak(UserId id, String username, String displayName);

    /** After Keycloak registration: insert local user row (Phase 2c write-path). */
    boolean createLocalUser(UserId id, String username, String displayName);
}
