package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.port.ChatPersistencePort;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC adapter for {@link ChatPersistencePort}. */
public final class JdbcChatPersistenceAdapter implements ChatPersistencePort {

    private final JdbcChatJdbcRepository jdbc;

    public JdbcChatPersistenceAdapter(JdbcChatJdbcRepository jdbc) {
        this.jdbc = jdbc;
    }

    public JdbcChatPersistenceAdapter(DataSource dataSource, DataSource readDataSource, Clock clock,
                                      com.avandocmsg.messenger.core.port.UuidGenerator uuidGenerator,
                                      int queryTimeoutSeconds) {
        this.jdbc = new JdbcChatJdbcRepository(dataSource, readDataSource, clock, uuidGenerator, queryTimeoutSeconds);
    }

    @Override
    public boolean chatExists(UUID chatId) {
        return jdbc.chatExists(chatId);
    }

    @Override
    public Optional<UUID> findOrgIdForRetentionOverlay(UUID chatId) {
        return jdbc.findOrgIdForRetentionOverlay(chatId);
    }

    @Override
    public com.avandocmsg.messenger.api.chats.dto.ChatResponse createGroup(UUID chatId, String title, UUID ownerId) {
        return jdbc.createGroup(chatId, title, ownerId);
    }

    @Override
    public com.avandocmsg.messenger.api.chats.dto.ChatResponse createChannel(UUID chatId, String title, UUID ownerId) {
        return jdbc.createChannel(chatId, title, ownerId);
    }

    @Override
    public Optional<String> getChatType(UUID chatId) {
        return jdbc.getChatType(chatId);
    }

    @Override
    public com.avandocmsg.messenger.api.chats.dto.ChatResponse createP2P(UUID chatId, UUID user1Id, UUID user2Id) {
        return jdbc.createP2P(chatId, user1Id, user2Id);
    }

    @Override
    public Optional<UUID> findP2PChat(UUID user1Id, UUID user2Id) {
        return jdbc.findP2PChat(user1Id, user2Id);
    }

    @Override
    public List<com.avandocmsg.messenger.api.chats.dto.ChatResponse> listByUser(UUID userId) {
        return jdbc.listByUser(userId);
    }

    @Override
    public Optional<UUID> findOtherP2PMember(UUID chatId, UUID userId) {
        return jdbc.findOtherP2PMember(chatId, userId);
    }

    @Override
    public Optional<com.avandocmsg.messenger.api.chats.dto.ChatResponse> findById(UUID chatId, UUID userId) {
        return jdbc.findById(chatId, userId);
    }

    @Override
    public boolean updateTitle(UUID chatId, String title) {
        return jdbc.updateTitle(chatId, title);
    }

    @Override
    public boolean setMuted(UUID chatId, UUID userId, boolean muted) {
        return jdbc.setMuted(chatId, userId, muted);
    }

    @Override
    public boolean setArchived(UUID chatId, UUID userId, boolean archived) {
        return jdbc.setArchived(chatId, userId, archived);
    }

    @Override
    public boolean setFolderTag(UUID chatId, UUID userId, String folderTag) {
        return jdbc.setFolderTag(chatId, userId, folderTag);
    }

    @Override
    public boolean addMember(UUID chatId, UUID userId, String role) {
        return jdbc.addMember(chatId, userId, role);
    }

    @Override
    public boolean removeMember(UUID chatId, UUID userId) {
        return jdbc.removeMember(chatId, userId);
    }

    @Override
    public boolean setRole(UUID chatId, UUID userId, String role) {
        return jdbc.setRole(chatId, userId, role);
    }

    @Override
    public List<com.avandocmsg.messenger.api.chats.dto.ChatMemberResponse> listMembers(UUID chatId) {
        return jdbc.listMembers(chatId);
    }

    @Override
    public Optional<UUID> findOwnerId(UUID chatId) {
        return jdbc.findOwnerId(chatId);
    }

    @Override
    public String getMemberRole(UUID chatId, UUID userId) {
        return jdbc.getMemberRole(chatId, userId);
    }

    @Override
    public boolean isMemberBanned(UUID chatId, UUID userId) {
        return jdbc.isMemberBanned(chatId, userId);
    }

    @Override
    public boolean setPersonalFilterActive(UUID chatId, UUID userId, boolean active) {
        return jdbc.setPersonalFilterActive(chatId, userId, active);
    }

    @Override
    public boolean isPersonalFilterActive(UUID chatId, UUID userId) {
        return jdbc.isPersonalFilterActive(chatId, userId);
    }

    @Override
    public boolean setBanned(UUID chatId, UUID userId, boolean banned) {
        return jdbc.setBanned(chatId, userId, banned);
    }

    @Override
    public List<UUID> listChatIdsForUser(UUID userId) {
        return jdbc.listChatIdsForUser(userId);
    }
}
