package com.avandocmsg.messenger.testsupport;

import com.avandocmsg.messenger.api.chats.dto.ChatMemberResponse;
import com.avandocmsg.messenger.api.chats.dto.ChatResponse;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** No-op {@link ChatPersistencePort} for unit tests; override only methods under test. */
public class EmptyChatPersistencePort implements ChatPersistencePort {

    @Override
    public boolean chatExists(UUID chatId) {
        return false;
    }

    @Override
    public Optional<UUID> findOrgIdForRetentionOverlay(UUID chatId) {
        return Optional.empty();
    }

    @Override
    public ChatResponse createGroup(UUID chatId, String title, UUID ownerId) {
        return null;
    }

    @Override
    public ChatResponse createChannel(UUID chatId, String title, UUID ownerId) {
        return null;
    }

    @Override
    public Optional<String> getChatType(UUID chatId) {
        return Optional.empty();
    }

    @Override
    public ChatResponse createP2P(UUID chatId, UUID user1Id, UUID user2Id) {
        return null;
    }

    @Override
    public Optional<UUID> findP2PChat(UUID user1Id, UUID user2Id) {
        return Optional.empty();
    }

    @Override
    public List<ChatResponse> listByUser(UUID userId) {
        return List.of();
    }

    @Override
    public Optional<UUID> findOtherP2PMember(UUID chatId, UUID userId) {
        return Optional.empty();
    }

    @Override
    public Optional<ChatResponse> findById(UUID chatId, UUID userId) {
        return Optional.empty();
    }

    @Override
    public boolean updateTitle(UUID chatId, String title) {
        return false;
    }

    @Override
    public boolean setMuted(UUID chatId, UUID userId, boolean muted) {
        return false;
    }

    @Override
    public boolean setArchived(UUID chatId, UUID userId, boolean archived) {
        return false;
    }

    @Override
    public boolean setFolderTag(UUID chatId, UUID userId, String folderTag) {
        return false;
    }

    @Override
    public boolean addMember(UUID chatId, UUID userId, String role) {
        return false;
    }

    @Override
    public boolean removeMember(UUID chatId, UUID userId) {
        return false;
    }

    @Override
    public boolean setRole(UUID chatId, UUID userId, String role) {
        return false;
    }

    @Override
    public List<ChatMemberResponse> listMembers(UUID chatId) {
        return List.of();
    }

    @Override
    public Optional<UUID> findOwnerId(UUID chatId) {
        return Optional.empty();
    }

    @Override
    public String getMemberRole(UUID chatId, UUID userId) {
        return null;
    }

    @Override
    public boolean isMemberBanned(UUID chatId, UUID userId) {
        return false;
    }

    @Override
    public boolean setPersonalFilterActive(UUID chatId, UUID userId, boolean active) {
        return false;
    }

    @Override
    public boolean isPersonalFilterActive(UUID chatId, UUID userId) {
        return false;
    }

    @Override
    public boolean setBanned(UUID chatId, UUID userId, boolean banned) {
        return false;
    }

    @Override
    public List<UUID> listChatIdsForUser(UUID userId) {
        return List.of();
    }
}
