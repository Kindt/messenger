package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.domain.UserProfile;
import com.avandocmsg.messenger.core.port.SavedChatPort;
import com.avandocmsg.messenger.core.port.UserRepositoryPort;

import java.util.Optional;

/** Hexagonal application service for user profile reads and writes (Phase 2c / US2). */
public final class UserApplicationService {
    private final UserRepositoryPort userRepositoryPort;
    private final SavedChatPort savedChatPort;

    public UserApplicationService(UserRepositoryPort userRepositoryPort, SavedChatPort savedChatPort) {
        this.userRepositoryPort = userRepositoryPort;
        this.savedChatPort = savedChatPort;
    }

    public Optional<ChatId> getSavedChatId(UserId userId) {
        return savedChatPort.getSavedChatId(userId);
    }

    public Optional<UserProfile> getProfileForViewer(UserId viewerId, UserId targetId) {
        return userRepositoryPort.findById(targetId)
            .flatMap(profile -> toViewerProfile(profile, viewerId));
    }

    public Optional<UserProfile> updateProfile(UserId userId, String displayName, String phone) {
        if (!userRepositoryPort.updateProfile(userId, displayName, phone)) {
            return Optional.empty();
        }
        return getProfileForViewer(userId, userId);
    }

    public Optional<UserProfile> updatePresence(UserId userId, String presenceStatus) {
        if (!userRepositoryPort.updatePresence(userId, presenceStatus)) {
            return Optional.empty();
        }
        return getProfileForViewer(userId, userId);
    }

    public Optional<UserProfile> updatePrivacy(UserId userId, boolean disableReadReceipts) {
        if (!userRepositoryPort.updatePrivacy(userId, disableReadReceipts)) {
            return Optional.empty();
        }
        return getProfileForViewer(userId, userId);
    }

    public void touchHeartbeat(UserId userId) {
        userRepositoryPort.touchHeartbeat(userId);
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
