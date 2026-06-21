package com.avandocmsg.messenger.testsupport;

import com.avandocmsg.messenger.api.users.dto.UserProfile;
import com.avandocmsg.messenger.api.users.dto.UserSearchHit;
import com.avandocmsg.messenger.core.port.UserLookupPort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** No-op {@link UserLookupPort} for unit tests. */
public class EmptyUserLookupPort implements UserLookupPort {

    @Override
    public Optional<UserProfile> findById(UUID id) {
        return Optional.empty();
    }

    @Override
    public Optional<UserProfile> findByUsername(String username) {
        return Optional.empty();
    }

    @Override
    public Optional<UserProfile> findByExternalId(String externalId) {
        return Optional.empty();
    }

    @Override
    public boolean isReadReceiptsDisabled(UUID id) {
        return false;
    }

    @Override
    public List<UserSearchHit> searchForViewer(UUID viewerId, String query, int limit) {
        return List.of();
    }
}
