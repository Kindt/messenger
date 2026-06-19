package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.chats.dto.ChatMemberResponse;
import com.avandocmsg.messenger.api.chats.dto.ChatResponse;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcChatJdbcRepository;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for chat JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcChatJdbcRepository}.
 */
public class ChatRepository {
    private final JdbcChatJdbcRepository jdbc;

    public ChatRepository(DataSource dataSource, Clock clock, UuidGenerator uuidGenerator) {
        this(dataSource, null, clock, uuidGenerator, 0);
    }

    public ChatRepository(DataSource dataSource, DataSource readDataSource, Clock clock, UuidGenerator uuidGenerator) {
        this(dataSource, readDataSource, clock, uuidGenerator, 0);
    }

    public ChatRepository(DataSource dataSource, DataSource readDataSource, Clock clock, UuidGenerator uuidGenerator,
                          int queryTimeoutSeconds) {
        this.jdbc = new JdbcChatJdbcRepository(dataSource, readDataSource, clock, uuidGenerator, queryTimeoutSeconds);
    }

    public JdbcChatJdbcRepository jdbcRepository() {
        return jdbc;
    }

    public boolean chatExists(UUID chatId) {
        return jdbc.chatExists(chatId);
    }

    public Optional<UUID> findOrgIdForRetentionOverlay(UUID chatId) {
        return jdbc.findOrgIdForRetentionOverlay(chatId);
    }

    public ChatResponse createGroup(UUID chatId, String title, UUID ownerId) {
        return jdbc.createGroup(chatId, title, ownerId);
    }

    public ChatResponse createChannel(UUID chatId, String title, UUID ownerId) {
        return jdbc.createChannel(chatId, title, ownerId);
    }

    public Optional<String> getChatType(UUID chatId) {
        return jdbc.getChatType(chatId);
    }

    public ChatResponse createP2P(UUID chatId, UUID user1Id, UUID user2Id) {
        return jdbc.createP2P(chatId, user1Id, user2Id);
    }

    public Optional<UUID> findP2PChat(UUID user1Id, UUID user2Id) {
        return jdbc.findP2PChat(user1Id, user2Id);
    }

    public List<ChatResponse> listByUser(UUID userId) {
        return jdbc.listByUser(userId);
    }

    public Optional<UUID> findOtherP2PMember(UUID chatId, UUID userId) {
        return jdbc.findOtherP2PMember(chatId, userId);
    }

    public Optional<ChatResponse> findById(UUID chatId, UUID userId) {
        return jdbc.findById(chatId, userId);
    }

    public boolean updateTitle(UUID chatId, String title) {
        return jdbc.updateTitle(chatId, title);
    }

    public boolean setMuted(UUID chatId, UUID userId, boolean muted) {
        return jdbc.setMuted(chatId, userId, muted);
    }

    public boolean setArchived(UUID chatId, UUID userId, boolean archived) {
        return jdbc.setArchived(chatId, userId, archived);
    }

    public boolean setFolderTag(UUID chatId, UUID userId, String folderTag) {
        return jdbc.setFolderTag(chatId, userId, folderTag);
    }

    public boolean addMember(UUID chatId, UUID userId, String role) {
        return jdbc.addMember(chatId, userId, role);
    }

    public boolean removeMember(UUID chatId, UUID userId) {
        return jdbc.removeMember(chatId, userId);
    }

    public boolean setRole(UUID chatId, UUID userId, String role) {
        return jdbc.setRole(chatId, userId, role);
    }

    public List<ChatMemberResponse> listMembers(UUID chatId) {
        return jdbc.listMembers(chatId);
    }

    public Optional<UUID> findOwnerId(UUID chatId) {
        return jdbc.findOwnerId(chatId);
    }

    public String getMemberRole(UUID chatId, UUID userId) {
        return jdbc.getMemberRole(chatId, userId);
    }

    public boolean isMemberBanned(UUID chatId, UUID userId) {
        return jdbc.isMemberBanned(chatId, userId);
    }

    public boolean setPersonalFilterActive(UUID chatId, UUID userId, boolean active) {
        return jdbc.setPersonalFilterActive(chatId, userId, active);
    }

    public boolean isPersonalFilterActive(UUID chatId, UUID userId) {
        return jdbc.isPersonalFilterActive(chatId, userId);
    }

    public boolean setBanned(UUID chatId, UUID userId, boolean banned) {
        return jdbc.setBanned(chatId, userId, banned);
    }

    public List<UUID> listChatIdsForUser(UUID userId) {
        return jdbc.listChatIdsForUser(userId);
    }
}
