package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Delegates {@link ChatPersistencePort} to legacy {@link ChatRepository}. */
public final class JdbcChatPersistenceAdapter implements ChatPersistencePort {
    private final ChatRepository delegate;

    public JdbcChatPersistenceAdapter(ChatRepository delegate) {
        this.delegate = delegate;
    }

    public JdbcChatPersistenceAdapter(DataSource dataSource, DataSource readDataSource, Clock clock,
                                      com.avandocmsg.messenger.core.port.UuidGenerator uuidGenerator,
                                      int queryTimeoutSeconds) {
        this.delegate = new ChatRepository(dataSource, readDataSource, clock, uuidGenerator, queryTimeoutSeconds);
    }

    @Override
    public boolean chatExists(UUID chatId) {
        return delegate.chatExists(chatId);
    }

    @Override
    public Optional<UUID> findOrgIdForRetentionOverlay(UUID chatId) {
        return delegate.findOrgIdForRetentionOverlay(chatId);
    }

    @Override
    public com.avandocmsg.messenger.api.chats.dto.ChatResponse createGroup(UUID chatId, String title, UUID ownerId) {
        return delegate.createGroup(chatId, title, ownerId);
    }

    @Override
    public com.avandocmsg.messenger.api.chats.dto.ChatResponse createChannel(UUID chatId, String title, UUID ownerId) {
        return delegate.createChannel(chatId, title, ownerId);
    }

    @Override
    public Optional<String> getChatType(UUID chatId) {
        return delegate.getChatType(chatId);
    }

    @Override
    public com.avandocmsg.messenger.api.chats.dto.ChatResponse createP2P(UUID chatId, UUID user1Id, UUID user2Id) {
        return delegate.createP2P(chatId, user1Id, user2Id);
    }

    @Override
    public Optional<UUID> findP2PChat(UUID user1Id, UUID user2Id) {
        return delegate.findP2PChat(user1Id, user2Id);
    }

    @Override
    public List<com.avandocmsg.messenger.api.chats.dto.ChatResponse> listByUser(UUID userId) {
        return delegate.listByUser(userId);
    }

    @Override
    public Optional<UUID> findOtherP2PMember(UUID chatId, UUID userId) {
        return delegate.findOtherP2PMember(chatId, userId);
    }

    @Override
    public Optional<com.avandocmsg.messenger.api.chats.dto.ChatResponse> findById(UUID chatId, UUID userId) {
        return delegate.findById(chatId, userId);
    }

    @Override
    public boolean updateTitle(UUID chatId, String title) {
        return delegate.updateTitle(chatId, title);
    }

    @Override
    public boolean setMuted(UUID chatId, UUID userId, boolean muted) {
        return delegate.setMuted(chatId, userId, muted);
    }

    @Override
    public boolean setArchived(UUID chatId, UUID userId, boolean archived) {
        return delegate.setArchived(chatId, userId, archived);
    }

    @Override
    public boolean setFolderTag(UUID chatId, UUID userId, String folderTag) {
        return delegate.setFolderTag(chatId, userId, folderTag);
    }

    @Override
    public boolean addMember(UUID chatId, UUID userId, String role) {
        return delegate.addMember(chatId, userId, role);
    }

    @Override
    public boolean removeMember(UUID chatId, UUID userId) {
        return delegate.removeMember(chatId, userId);
    }

    @Override
    public boolean setRole(UUID chatId, UUID userId, String role) {
        return delegate.setRole(chatId, userId, role);
    }

    @Override
    public List<com.avandocmsg.messenger.api.chats.dto.ChatMemberResponse> listMembers(UUID chatId) {
        return delegate.listMembers(chatId);
    }

    @Override
    public Optional<UUID> findOwnerId(UUID chatId) {
        return delegate.findOwnerId(chatId);
    }

    @Override
    public String getMemberRole(UUID chatId, UUID userId) {
        return delegate.getMemberRole(chatId, userId);
    }

    @Override
    public boolean isMemberBanned(UUID chatId, UUID userId) {
        return delegate.isMemberBanned(chatId, userId);
    }

    @Override
    public boolean setPersonalFilterActive(UUID chatId, UUID userId, boolean active) {
        return delegate.setPersonalFilterActive(chatId, userId, active);
    }

    @Override
    public boolean isPersonalFilterActive(UUID chatId, UUID userId) {
        return delegate.isPersonalFilterActive(chatId, userId);
    }

    @Override
    public boolean setBanned(UUID chatId, UUID userId, boolean banned) {
        return delegate.setBanned(chatId, userId, banned);
    }

    @Override
    public List<UUID> listChatIdsForUser(UUID userId) {
        return delegate.listChatIdsForUser(userId);
    }
}
