package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.domain.UserProfile;
import com.avandocmsg.messenger.core.port.UserRepositoryPort;

import java.util.Optional;

/** Hexagonal application service for user profile reads (Phase 2c). */
public final class UserApplicationService {
    private final UserRepositoryPort userRepositoryPort;

    public UserApplicationService(UserRepositoryPort userRepositoryPort) {
        this.userRepositoryPort = userRepositoryPort;
    }

    public Optional<UserProfile> getProfileForViewer(UserId viewerId, UserId targetId) {
        return userRepositoryPort.findById(targetId)
            .flatMap(profile -> toViewerProfile(profile, viewerId));
    }

    private static Optional<UserProfile> toViewerProfile(UserProfile profile, UserId viewerId) {
        if (profile.hidden() && !profile.id().equals(viewerId)) {
            return Optional.empty();
        }
        if (profile.id().equals(viewerId)) {
            return Optional.of(profile);
        }
        return Optional.of(toPublicProfile(profile));
    }

    private static UserProfile toPublicProfile(UserProfile profile) {
        return new UserProfile(
            profile.id(),
            profile.username(),
            profile.displayName(),
            null,
            profile.hidden(),
            profile.createdAt(),
            profile.presenceStatus(),
            profile.lastSeenAt(),
            null,
            false);
    }
}
