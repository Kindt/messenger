package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.api.users.dto.UserProfile;
import com.avandocmsg.messenger.api.users.dto.UserSearchHit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** API-layer user reads not covered by {@link UserRepositoryPort}. */
public interface UserLookupPort {
    Optional<UserProfile> findById(UUID id);

    Optional<UserProfile> findByUsername(String username);

    boolean isReadReceiptsDisabled(UUID id);

    List<UserSearchHit> searchForViewer(UUID viewerId, String query, int limit);
}
