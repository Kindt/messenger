package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC adapter for {@link ChatPersistencePort}. */
public final class JdbcChatPersistenceAdapter implements ChatPersistencePort {
    private final JdbcChatJdbcRepository jdbc;
    private final ChatRepository testFacade;

    public JdbcChatPersistenceAdapter(JdbcChatJdbcRepository jdbc) {
        this.jdbc = jdbc;
        this.testFacade = null;
    }

    /** Wraps a test {@link ChatRepository} stub; production uses {@link #JdbcChatPersistenceAdapter(DataSource, ...)}. */
    public JdbcChatPersistenceAdapter(ChatRepository delegate) {
        this.jdbc = null;
        this.testFacade = delegate;
    }

    public JdbcChatPersistenceAdapter(DataSource dataSource, DataSource readDataSource, Clock clock,
                                      com.avandocmsg.messenger.core.port.UuidGenerator uuidGenerator,
                                      int queryTimeoutSeconds) {
        this.jdbc = new JdbcChatJdbcRepository(dataSource, readDataSource, clock, uuidGenerator, queryTimeoutSeconds);
        this.testFacade = null;
    }

    @Override
    public boolean chatExists(UUID chatId) {
        return usesTestFacade() ? testFacade.chatExists(chatId) : jdbc.chatExists(chatId);
    }

    @Override
    public Optional<UUID> findOrgIdForRetentionOverlay(UUID chatId) {
        return usesTestFacade() ? testFacade.findOrgIdForRetentionOverlay(chatId) : jdbc.findOrgIdForRetentionOverlay(chatId);
    }

    @Override
    public com.avandocmsg.messenger.api.chats.dto.ChatResponse createGroup(UUID chatId, String title, UUID ownerId) {
        return usesTestFacade() ? testFacade.createGroup(chatId, title, ownerId) : jdbc.createGroup(chatId, title, ownerId);
    }

    @Override
    public com.avandocmsg.messenger.api.chats.dto.ChatResponse createChannel(UUID chatId, String title, UUID ownerId) {
        return usesTestFacade() ? testFacade.createChannel(chatId, title, ownerId) : jdbc.createChannel(chatId, title, ownerId);
    }

    @Override
    public Optional<String> getChatType(UUID chatId) {
        return usesTestFacade() ? testFacade.getChatType(chatId) : jdbc.getChatType(chatId);
    }

    @Override
    public com.avandocmsg.messenger.api.chats.dto.ChatResponse createP2P(UUID chatId, UUID user1Id, UUID user2Id) {
        return usesTestFacade() ? testFacade.createP2P(chatId, user1Id, user2Id) : jdbc.createP2P(chatId, user1Id, user2Id);
    }

    @Override
    public Optional<UUID> findP2PChat(UUID user1Id, UUID user2Id) {
        return usesTestFacade() ? testFacade.findP2PChat(user1Id, user2Id) : jdbc.findP2PChat(user1Id, user2Id);
    }

    @Override
    public List<com.avandocmsg.messenger.api.chats.dto.ChatResponse> listByUser(UUID userId) {
        return usesTestFacade() ? testFacade.listByUser(userId) : jdbc.listByUser(userId);
    }

    @Override
    public Optional<UUID> findOtherP2PMember(UUID chatId, UUID userId) {
        return usesTestFacade() ? testFacade.findOtherP2PMember(chatId, userId) : jdbc.findOtherP2PMember(chatId, userId);
    }

    @Override
    public Optional<com.avandocmsg.messenger.api.chats.dto.ChatResponse> findById(UUID chatId, UUID userId) {
        return usesTestFacade() ? testFacade.findById(chatId, userId) : jdbc.findById(chatId, userId);
    }

    @Override
    public boolean updateTitle(UUID chatId, String title) {
        return usesTestFacade() ? testFacade.updateTitle(chatId, title) : jdbc.updateTitle(chatId, title);
    }

    @Override
    public boolean setMuted(UUID chatId, UUID userId, boolean muted) {
        return usesTestFacade() ? testFacade.setMuted(chatId, userId, muted) : jdbc.setMuted(chatId, userId, muted);
    }

    @Override
    public boolean setArchived(UUID chatId, UUID userId, boolean archived) {
        return usesTestFacade() ? testFacade.setArchived(chatId, userId, archived) : jdbc.setArchived(chatId, userId, archived);
    }

    @Override
    public boolean setFolderTag(UUID chatId, UUID userId, String folderTag) {
        return usesTestFacade() ? testFacade.setFolderTag(chatId, userId, folderTag) : jdbc.setFolderTag(chatId, userId, folderTag);
    }

    @Override
    public boolean addMember(UUID chatId, UUID userId, String role) {
        return usesTestFacade() ? testFacade.addMember(chatId, userId, role) : jdbc.addMember(chatId, userId, role);
    }

    @Override
    public boolean removeMember(UUID chatId, UUID userId) {
        return usesTestFacade() ? testFacade.removeMember(chatId, userId) : jdbc.removeMember(chatId, userId);
    }

    @Override
    public boolean setRole(UUID chatId, UUID userId, String role) {
        return usesTestFacade() ? testFacade.setRole(chatId, userId, role) : jdbc.setRole(chatId, userId, role);
    }

    @Override
    public List<com.avandocmsg.messenger.api.chats.dto.ChatMemberResponse> listMembers(UUID chatId) {
        return usesTestFacade() ? testFacade.listMembers(chatId) : jdbc.listMembers(chatId);
    }

    @Override
    public Optional<UUID> findOwnerId(UUID chatId) {
        return usesTestFacade() ? testFacade.findOwnerId(chatId) : jdbc.findOwnerId(chatId);
    }

    @Override
    public String getMemberRole(UUID chatId, UUID userId) {
        return usesTestFacade() ? testFacade.getMemberRole(chatId, userId) : jdbc.getMemberRole(chatId, userId);
    }

    @Override
    public boolean isMemberBanned(UUID chatId, UUID userId) {
        return usesTestFacade() ? testFacade.isMemberBanned(chatId, userId) : jdbc.isMemberBanned(chatId, userId);
    }

    @Override
    public boolean setPersonalFilterActive(UUID chatId, UUID userId, boolean active) {
        return usesTestFacade() ? testFacade.setPersonalFilterActive(chatId, userId, active) : jdbc.setPersonalFilterActive(chatId, userId, active);
    }

    @Override
    public boolean isPersonalFilterActive(UUID chatId, UUID userId) {
        return usesTestFacade() ? testFacade.isPersonalFilterActive(chatId, userId) : jdbc.isPersonalFilterActive(chatId, userId);
    }

    @Override
    public boolean setBanned(UUID chatId, UUID userId, boolean banned) {
        return usesTestFacade() ? testFacade.setBanned(chatId, userId, banned) : jdbc.setBanned(chatId, userId, banned);
    }

    @Override
    public List<UUID> listChatIdsForUser(UUID userId) {
        return usesTestFacade() ? testFacade.listChatIdsForUser(userId) : jdbc.listChatIdsForUser(userId);
    }

    private boolean usesTestFacade() {
        return testFacade != null;
    }
}
