package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.api.chats.dto.ChatMemberResponse;
import com.avandocmsg.messenger.api.chats.dto.ChatResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound persistence for chat CRUD and membership (API DTO surface). */
public interface ChatPersistencePort {
    boolean chatExists(UUID chatId);

    Optional<UUID> findOrgIdForRetentionOverlay(UUID chatId);

    ChatResponse createGroup(UUID chatId, String title, UUID ownerId);

    ChatResponse createChannel(UUID chatId, String title, UUID ownerId);

    Optional<String> getChatType(UUID chatId);

    ChatResponse createP2P(UUID chatId, UUID user1Id, UUID user2Id);

    Optional<UUID> findP2PChat(UUID user1Id, UUID user2Id);

    List<ChatResponse> listByUser(UUID userId);

    Optional<UUID> findOtherP2PMember(UUID chatId, UUID userId);

    Optional<ChatResponse> findById(UUID chatId, UUID userId);

    boolean updateTitle(UUID chatId, String title);

    boolean setMuted(UUID chatId, UUID userId, boolean muted);

    boolean setArchived(UUID chatId, UUID userId, boolean archived);

    boolean setFolderTag(UUID chatId, UUID userId, String folderTag);

    boolean addMember(UUID chatId, UUID userId, String role);

    boolean removeMember(UUID chatId, UUID userId);

    boolean setRole(UUID chatId, UUID userId, String role);

    List<ChatMemberResponse> listMembers(UUID chatId);

    Optional<UUID> findOwnerId(UUID chatId);

    String getMemberRole(UUID chatId, UUID userId);

    boolean isMemberBanned(UUID chatId, UUID userId);

    boolean setPersonalFilterActive(UUID chatId, UUID userId, boolean active);

    boolean isPersonalFilterActive(UUID chatId, UUID userId);

    boolean setBanned(UUID chatId, UUID userId, boolean banned);

    List<UUID> listChatIdsForUser(UUID userId);
}
