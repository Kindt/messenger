package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.chats.dto.ChatMemberResponse;
import com.avandocmsg.messenger.api.chats.dto.ChatResponse;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.contacts.dto.ContactResponse;
import com.avandocmsg.messenger.api.users.dto.UserProfile;
import com.avandocmsg.messenger.api.users.dto.UserSearchHit;
import com.avandocmsg.messenger.api.blocks.dto.BlockedUserResponse;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.AvatarHistoryPort;
import com.avandocmsg.messenger.core.port.AvatarAccessPort;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.core.port.FileMetadataPort;
import com.avandocmsg.messenger.core.port.ReadCachePort;
import com.avandocmsg.messenger.core.port.UserRepositoryPort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Avatar set/clear and signed URL enrichment (spec 068). */
public final class AvatarApplicationService {

    private final AppConfig appConfig;
    private final AvatarAccessPort avatarAccessPort;
    private final AvatarUrlBuilder urlBuilder;
    private final UserRepositoryPort userRepositoryPort;
    private final ChatPersistencePort chatPersistencePort;
    private final FileMetadataPort fileMetadataPort;
    private final AvatarUpdatePublisher updatePublisher;
    private final ReadCachePort readCachePort;
    private final AvatarHistoryPort avatarHistoryPort;

    public AvatarApplicationService(AppConfig appConfig,
                                      AvatarAccessPort avatarAccessPort,
                                      AvatarUrlBuilder urlBuilder,
                                      UserRepositoryPort userRepositoryPort,
                                      ChatPersistencePort chatPersistencePort,
                                      FileMetadataPort fileMetadataPort,
                                      AvatarUpdatePublisher updatePublisher,
                                      ReadCachePort readCachePort) {
        this(appConfig, avatarAccessPort, urlBuilder, userRepositoryPort, chatPersistencePort, fileMetadataPort,
            updatePublisher, readCachePort, null);
    }

    public AvatarApplicationService(AppConfig appConfig,
                                      AvatarAccessPort avatarAccessPort,
                                      AvatarUrlBuilder urlBuilder,
                                      UserRepositoryPort userRepositoryPort,
                                      ChatPersistencePort chatPersistencePort,
                                      FileMetadataPort fileMetadataPort,
                                      AvatarUpdatePublisher updatePublisher,
                                      ReadCachePort readCachePort,
                                      AvatarHistoryPort avatarHistoryPort) {
        this.appConfig = appConfig;
        this.avatarAccessPort = avatarAccessPort;
        this.urlBuilder = urlBuilder;
        this.userRepositoryPort = userRepositoryPort;
        this.chatPersistencePort = chatPersistencePort;
        this.fileMetadataPort = fileMetadataPort;
        this.updatePublisher = updatePublisher;
        this.readCachePort = readCachePort;
        this.avatarHistoryPort = avatarHistoryPort;
    }

    public boolean avatarsEnabled() {
        return appConfig.avatarsEnabled();
    }

    public boolean userMayUploadAvatar(UserId userId) {
        return avatarsEnabled() && avatarAccessPort.userMayUploadAvatar(userId);
    }

    public Optional<com.avandocmsg.messenger.core.domain.UserProfile> setUserAvatar(UserId userId, FileId fileId) {
        return setUserAvatar(userId, fileId, false);
    }

    public Optional<com.avandocmsg.messenger.core.domain.UserProfile> setUserAvatarFromImport(
        UserId userId, FileId fileId) {
        return setUserAvatar(userId, fileId, true);
    }

    public Optional<com.avandocmsg.messenger.core.domain.UserProfile> setUserAvatar(
        UserId userId, FileId fileId, boolean ldapImport) {
        if (!avatarsEnabled()) {
            return Optional.empty();
        }
        if (!ldapImport && !avatarAccessPort.userMayUploadAvatar(userId)) {
            return Optional.empty();
        }
        if (fileId == null || !fileOwnedByUser(fileId, userId)) {
            return Optional.empty();
        }
        if (!userRepositoryPort.updateAvatar(userId, fileId.value())) {
            return Optional.empty();
        }
        recordUserAvatarHistory(userId, fileId, userId);
        ReadCacheCoordinator.invalidateUserProfile(readCachePort, userId.value());
        var profile = userRepositoryPort.findById(userId).orElse(null);
        if (profile != null && updatePublisher != null) {
            updatePublisher.publishUserAvatar(profile, userId);
        }
        return Optional.ofNullable(profile);
    }

    public Optional<com.avandocmsg.messenger.core.domain.UserProfile> clearUserAvatar(UserId userId) {
        if (!avatarsEnabled()) {
            return Optional.empty();
        }
        if (!userRepositoryPort.updateAvatar(userId, null)) {
            return Optional.empty();
        }
        ReadCacheCoordinator.invalidateUserProfile(readCachePort, userId.value());
        var profile = userRepositoryPort.findById(userId).orElse(null);
        if (profile != null && updatePublisher != null) {
            updatePublisher.publishUserAvatar(profile, userId);
        }
        return Optional.ofNullable(profile);
    }

    public Optional<com.avandocmsg.messenger.core.domain.UserProfile> updateUserAvatarHidden(
        UserId userId, boolean avatarHidden) {
        if (!avatarsEnabled()) {
            return Optional.empty();
        }
        if (!userRepositoryPort.updateAvatarHidden(userId, avatarHidden)) {
            return Optional.empty();
        }
        ReadCacheCoordinator.invalidateUserProfile(readCachePort, userId.value());
        var profile = userRepositoryPort.findById(userId).orElse(null);
        if (profile != null && updatePublisher != null) {
            updatePublisher.publishUserAvatar(profile, userId);
        }
        return Optional.ofNullable(profile);
    }

    public Optional<ChatResponse> setChatAvatar(UserId actorId, ChatId chatId, FileId fileId) {
        if (!avatarsEnabled() || fileId == null || !fileOwnedByUser(fileId, actorId)) {
            return Optional.empty();
        }
        if (!chatPersistencePort.updateAvatar(chatId.value(), fileId.value())) {
            return Optional.empty();
        }
        recordChatAvatarHistory(chatId, fileId, actorId);
        ReadCacheCoordinator.invalidateAfterChatMutation(readCachePort, actorId.value());
        if (updatePublisher != null) {
            updatePublisher.publishChatAvatar(chatId, fileId, actorId);
        }
        return chatPersistencePort.findById(chatId.value(), actorId.value());
    }

    public Optional<ChatResponse> clearChatAvatar(UserId actorId, ChatId chatId) {
        if (!avatarsEnabled()) {
            return Optional.empty();
        }
        if (!chatPersistencePort.updateAvatar(chatId.value(), null)) {
            return Optional.empty();
        }
        ReadCacheCoordinator.invalidateAfterChatMutation(readCachePort, actorId.value());
        if (updatePublisher != null) {
            updatePublisher.publishChatAvatar(chatId, null, actorId);
        }
        return chatPersistencePort.findById(chatId.value(), actorId.value());
    }

    public UserProfile enrichUserProfile(UserProfile base, UserId viewerId, FileId avatarFileId) {
        if (!avatarsEnabled() || base == null) {
            return base;
        }
        var fileIdStr = avatarFileId != null ? avatarFileId.value().toString() : null;
        var url = mintUserAvatarUrl(viewerId, avatarFileId);
        return withUserAvatarFields(base, fileIdStr, url);
    }

    public UserSearchHit enrichUserSearchHit(UserSearchHit hit, UserId viewerId, FileId avatarFileId) {
        if (!avatarsEnabled() || hit == null) {
            return hit;
        }
        return new UserSearchHit(hit.userId(), hit.username(), hit.displayName(),
            mintUserAvatarUrl(viewerId, avatarFileId));
    }

    public ContactResponse enrichContactResponse(ContactResponse base, UserId viewerId, FileId avatarFileId) {
        if (!avatarsEnabled() || base == null) {
            return base;
        }
        return new ContactResponse(base.id(), base.username(), base.displayName(), base.phone(), base.addedAt(),
            mintUserAvatarUrl(viewerId, avatarFileId));
    }

    public BlockedUserResponse enrichBlockedUserResponse(BlockedUserResponse base, UserId viewerId,
                                                         FileId avatarFileId) {
        if (!avatarsEnabled() || base == null) {
            return base;
        }
        return new BlockedUserResponse(base.userId(), base.username(), base.displayName(), base.blockedAt(),
            mintUserAvatarUrl(viewerId, avatarFileId));
    }

    public ChatResponse enrichChatResponse(ChatResponse chat, UserId viewerId) {
        if (!avatarsEnabled() || chat == null) {
            return chat;
        }
        var chatAvatarId = parseUuid(chat.avatarFileId());
        var chatUrl = mintChatAvatarUrl(viewerId, chatAvatarId);
        var displayUrl = resolveDisplayAvatarUrl(chat, viewerId, chatAvatarId, chatUrl);
        return withChatAvatarFields(chat, chat.avatarFileId(), chatUrl, displayUrl);
    }

    public List<ChatResponse> enrichChatResponses(List<ChatResponse> chats, UserId viewerId) {
        if (!avatarsEnabled() || chats == null) {
            return chats;
        }
        return chats.stream().map(c -> enrichChatResponse(c, viewerId)).toList();
    }

    public ChatMemberResponse enrichChatMember(ChatMemberResponse member, UserId viewerId, FileId avatarFileId) {
        if (!avatarsEnabled() || member == null) {
            return member;
        }
        return new ChatMemberResponse(
            member.userId(), member.username(), member.displayName(), member.role(),
            member.muted(), member.banned(), member.joinedAt(),
            mintUserAvatarUrl(viewerId, avatarFileId));
    }

    public List<ChatMemberResponse> enrichChatMembers(List<ChatMemberResponse> members, UserId viewerId) {
        if (!avatarsEnabled() || members == null) {
            return members;
        }
        return members.stream().map(member -> {
            var memberId = parseUuid(member.userId());
            if (memberId == null) {
                return member;
            }
            var avatarFileId = userRepositoryPort.findById(UserId.of(memberId))
                .map(com.avandocmsg.messenger.core.domain.UserProfile::avatarFileId)
                .orElse(null);
            return enrichChatMember(member, viewerId, avatarFileId);
        }).toList();
    }

    public String mintOrgLogoUrl(UserId viewerId, FileId logoFileId) {
        if (logoFileId == null || viewerId == null) {
            return null;
        }
        if (!avatarAccessPort.viewerMayAccessAsAvatar(viewerId, logoFileId)) {
            return null;
        }
        return urlBuilder.resizeUrl(viewerId, logoFileId);
    }

    public String mintUserAvatarUrl(UserId viewerId, FileId avatarFileId) {
        if (avatarFileId == null || viewerId == null) {
            return null;
        }
        if (!avatarAccessPort.viewerMayAccessAsAvatar(viewerId, avatarFileId)) {
            return null;
        }
        return urlBuilder.resizeUrl(viewerId, avatarFileId);
    }

    public boolean verifyAvatarTokenAccess(AvatarAccessTokenService tokenService, String token, FileId fileId,
                                           int width, int height) {
        var parsed = tokenService.verify(token).orElse(null);
        if (parsed == null) {
            return false;
        }
        if (!parsed.fileId().equals(fileId.value())) {
            return false;
        }
        if (parsed.width() != AvatarAccessTokenService.clampDimension(width)
            || parsed.height() != AvatarAccessTokenService.clampDimension(height)) {
            return false;
        }
        return avatarAccessPort.viewerMayAccessAsAvatar(UserId.of(parsed.viewerId()), fileId);
    }

    private String mintChatAvatarUrl(UserId viewerId, UUID chatAvatarId) {
        if (chatAvatarId == null || viewerId == null) {
            return null;
        }
        var fileId = FileId.of(chatAvatarId);
        if (!avatarAccessPort.viewerMayAccessAsAvatar(viewerId, fileId)) {
            return null;
        }
        return urlBuilder.resizeUrl(viewerId, fileId);
    }

    private String resolveDisplayAvatarUrl(ChatResponse chat, UserId viewerId, UUID chatAvatarId, String chatUrl) {
        if ("p2p".equals(chat.type())) {
            var chatUuid = parseUuid(chat.id());
            if (chatUuid == null) {
                return chatUrl;
            }
            var peerId = chatPersistencePort.findOtherP2PMember(chatUuid, viewerId.value()).orElse(null);
            if (peerId == null) {
                return chatUrl;
            }
            return userRepositoryPort.findById(UserId.of(peerId))
                .map(p -> mintUserAvatarUrl(viewerId, p.avatarFileId()))
                .orElse(null);
        }
        return chatUrl != null ? chatUrl : mintChatAvatarUrl(viewerId, chatAvatarId);
    }

    private void recordUserAvatarHistory(UserId entityId, FileId fileId, UserId setBy) {
        if (avatarHistoryPort != null && fileId != null) {
            avatarHistoryPort.recordUserAvatar(entityId, fileId, setBy);
        }
    }

    private void recordChatAvatarHistory(ChatId chatId, FileId fileId, UserId setBy) {
        if (avatarHistoryPort != null && fileId != null) {
            avatarHistoryPort.recordChatAvatar(chatId, fileId, setBy);
        }
    }

    private boolean fileOwnedByUser(FileId fileId, UserId userId) {
        return fileMetadataPort.findById(fileId)
            .map(f -> f.uploadedBy().equals(userId))
            .orElse(false);
    }

    private static UserProfile withUserAvatarFields(UserProfile base, String fileIdStr, String url) {
        return new UserProfile(
            base.id(), base.username(), base.displayName(), base.phone(), base.email(), base.externalId(),
            base.hidden(), base.createdAt(), base.presenceStatus(), base.lastSeenAt(), base.orgId(),
            base.privacyDisableReadReceipts(), base.uiLocale(), base.customStatusText(), base.dndUntil(),
            base.avatarHidden(), fileIdStr, url);
    }

    private static ChatResponse withChatAvatarFields(ChatResponse chat, String avatarFileId, String avatarUrl,
                                                     String displayAvatarUrl) {
        return new ChatResponse(
            chat.id(), chat.title(), chat.type(), chat.ownerId(), chat.memberCount(), chat.muted(),
            chat.personalFilterActive(), chat.ttlSeconds(), chat.createdAt(), chat.archived(), chat.folderTag(),
            chat.channelPostPolicy(), avatarFileId, avatarUrl, displayAvatarUrl);
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
