package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.users.dto.UpdatePresenceRequest;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.domain.UserProfile;
import com.avandocmsg.messenger.core.port.ReadCacheKeys;
import com.avandocmsg.messenger.core.port.ReadCacheKind;
import com.avandocmsg.messenger.core.port.ReadCachePort;
import com.avandocmsg.messenger.core.port.SavedChatPort;
import com.avandocmsg.messenger.core.port.UserRepositoryPort;

import java.util.Optional;

/** Hexagonal application service for user profile reads and writes (Phase 2c / US2). */
public final class UserApplicationService {
    private final UserRepositoryPort userRepositoryPort;
    private final SavedChatPort savedChatPort;
    private final ReadCachePort readCachePort;
    private final AppConfig appConfig;
    private final UserPresencePublisher presencePublisher;

    public UserApplicationService(UserRepositoryPort userRepositoryPort,
                                  SavedChatPort savedChatPort,
                                  ReadCachePort readCachePort,
                                  AppConfig appConfig,
                                  UserPresencePublisher presencePublisher) {
        this.userRepositoryPort = userRepositoryPort;
        this.savedChatPort = savedChatPort;
        this.readCachePort = readCachePort;
        this.appConfig = appConfig;
        this.presencePublisher = presencePublisher != null ? presencePublisher : new UserPresencePublisher(null);
    }

    public Optional<ChatId> getSavedChatId(UserId userId) {
        return savedChatPort.getSavedChatId(userId);
    }

    public Optional<UserProfile> getProfileForViewer(UserId viewerId, UserId targetId) {
        if (viewerId.equals(targetId) && readCachePort.enabled()) {
            var key = ReadCacheKeys.userProfile(targetId.value());
            var cached = readCachePort.get(key).flatMap(ReadCacheJson::userProfileFromJson);
            if (cached.isPresent()) {
                return cached;
            }
            var loaded = loadProfileForViewer(viewerId, targetId);
            loaded.flatMap(ReadCacheJson::userProfileToJson).ifPresent(json ->
                readCachePort.put(key, json, appConfig.readCacheTtlSeconds(ReadCacheKind.USER_PROFILE)));
            return loaded;
        }
        return loadProfileForViewer(viewerId, targetId);
    }

    public Optional<UserProfile> updateProfile(UserId userId, String displayName, String phone) {
        if (!userRepositoryPort.updateProfile(userId, displayName, phone)) {
            return Optional.empty();
        }
        ReadCacheCoordinator.invalidateUserProfile(readCachePort, userId.value());
        return getProfileForViewer(userId, userId);
    }

    public Optional<UserProfile> updatePresence(UserId userId, String presenceStatus) {
        if (!userRepositoryPort.updatePresence(userId, presenceStatus)) {
            return Optional.empty();
        }
        ReadCacheCoordinator.invalidateUserProfile(readCachePort, userId.value());
        ReadCacheCoordinator.invalidateUserPresence(readCachePort, userId.value());
        var profile = getProfileForViewer(userId, userId).orElse(null);
        if (profile != null) {
            presencePublisher.publish(profile);
        }
        return Optional.ofNullable(profile);
    }

    public Optional<UserProfile> updateUserStatus(UserId userId, UpdatePresenceRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        var clearDnd = request.presenceStatus() != null && !"dnd".equals(request.presenceStatus());
        if (!userRepositoryPort.updateUserStatus(
            userId,
            request.presenceStatus(),
            request.customStatusText(),
            request.dndUntil(),
            clearDnd)) {
            return Optional.empty();
        }
        ReadCacheCoordinator.invalidateUserProfile(readCachePort, userId.value());
        ReadCacheCoordinator.invalidateUserPresence(readCachePort, userId.value());
        var profile = getProfileForViewer(userId, userId).orElse(null);
        if (profile != null) {
            presencePublisher.publish(profile);
        }
        return Optional.ofNullable(profile);
    }

    public Optional<UserProfile> updatePrivacy(UserId userId, boolean disableReadReceipts) {
        if (!userRepositoryPort.updatePrivacy(userId, disableReadReceipts)) {
            return Optional.empty();
        }
        ReadCacheCoordinator.invalidateUserProfile(readCachePort, userId.value());
        return getProfileForViewer(userId, userId);
    }

    public Optional<UserProfile> updateUiLocale(UserId userId, String uiLocale) {
        if (!userRepositoryPort.updateUiLocale(userId, uiLocale)) {
            return Optional.empty();
        }
        ReadCacheCoordinator.invalidateUserProfile(readCachePort, userId.value());
        return getProfileForViewer(userId, userId);
    }

    public void touchHeartbeat(UserId userId) {
        userRepositoryPort.touchHeartbeat(userId);
        ReadCacheCoordinator.invalidateUserPresence(readCachePort, userId.value());
    }

    private Optional<UserProfile> loadProfileForViewer(UserId viewerId, UserId targetId) {
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
            false,
            null,
            profile.customStatusText(),
            null);
    }
}
