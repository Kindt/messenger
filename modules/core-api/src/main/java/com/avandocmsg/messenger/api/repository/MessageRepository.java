package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcMessageReadRepository;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcMessageWriteRepository;
import com.avandocmsg.messenger.core.adapter.persistence.MessageJdbcSql;
import com.avandocmsg.messenger.core.port.MessageMentionRepositoryPort;
import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.api.messages.dto.MessageVersionResponse;
import com.avandocmsg.messenger.api.messages.dto.PinnedMessageResponse;
import com.avandocmsg.messenger.api.messages.dto.ReactionResponse;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy façade for message JDBC (tests and gradual migration).
 * SQL lives in {@link JdbcMessageReadRepository} / {@link JdbcMessageWriteRepository}.
 */
public class MessageRepository {
    /** @deprecated use {@link MessageJdbcSql#MSG_VISIBILITY_TTL_VISIBLE} */
    @Deprecated
    public static final String SQL_MSG_VISIBILITY_TTL_VISIBLE = MessageJdbcSql.MSG_VISIBILITY_TTL_VISIBLE;

    private final JdbcMessageReadRepository readRepository;
    private final JdbcMessageWriteRepository writeRepository;

    public MessageRepository(DataSource dataSource, Clock clock) {
        this(dataSource, null, clock, 0);
    }

    public MessageRepository(DataSource dataSource, DataSource readDataSource, Clock clock) {
        this(dataSource, readDataSource, clock, 0);
    }

    public MessageRepository(DataSource dataSource, DataSource readDataSource, Clock clock, int queryTimeoutSeconds) {
        this(dataSource, readDataSource, clock, queryTimeoutSeconds,
            new JdbcMessageWriteRepository(dataSource, readDataSource, clock, queryTimeoutSeconds));
    }

    public MessageRepository(
        DataSource dataSource,
        DataSource readDataSource,
        Clock clock,
        int queryTimeoutSeconds,
        JdbcMessageWriteRepository writeRepository
    ) {
        this.writeRepository = writeRepository;
        this.readRepository = new JdbcMessageReadRepository(dataSource, readDataSource, queryTimeoutSeconds);
    }

    public void setMentionRepositoryPort(MessageMentionRepositoryPort mentionRepositoryPort) {
        readRepository.setMentionRepositoryPort(mentionRepositoryPort);
    }

    public JdbcMessageWriteRepository jdbcWriteRepository() {
        return writeRepository;
    }

    public JdbcMessageReadRepository jdbcReadRepository() {
        return readRepository;
    }

    public MessageResponse insert(UUID id, UUID chatId, UUID senderId, String type, String content,
                                  UUID replyToMsgId, String clientMsgId, Integer visibilityTtlSeconds) {
        return insert(id, chatId, senderId, type, content, replyToMsgId, null, clientMsgId, visibilityTtlSeconds, null);
    }

    public MessageResponse insert(UUID id, UUID chatId, UUID senderId, String type, String content,
                                  UUID replyToMsgId, String clientMsgId, Integer visibilityTtlSeconds,
                                  UUID attachmentFileId) {
        return insert(id, chatId, senderId, type, content, replyToMsgId, null, clientMsgId,
            visibilityTtlSeconds, attachmentFileId);
    }

    public MessageResponse insert(UUID id, UUID chatId, UUID senderId, String type, String content,
                                  UUID replyToMsgId, UUID threadId, String clientMsgId,
                                  Integer visibilityTtlSeconds, UUID attachmentFileId) {
        return insert(id, chatId, senderId, type, content, replyToMsgId, threadId, clientMsgId,
            visibilityTtlSeconds, attachmentFileId, null);
    }

    public MessageResponse insert(UUID id, UUID chatId, UUID senderId, String type, String content,
                                  UUID replyToMsgId, UUID threadId, String clientMsgId,
                                  Integer visibilityTtlSeconds, UUID attachmentFileId, Integer voiceDurationMs) {
        return writeRepository.insert(id, chatId, senderId, type, content, replyToMsgId, threadId, clientMsgId,
            visibilityTtlSeconds, attachmentFileId, voiceDurationMs);
    }

    public boolean existsClientMsgId(UUID chatId, UUID senderId, String clientMsgId) {
        return writeRepository.existsClientMsgId(chatId, senderId, clientMsgId);
    }

    public Optional<UUID> findLatestMessageId(UUID chatId) {
        return readRepository.findLatestMessageId(chatId);
    }

    public Optional<MessageResponse> findById(UUID id) {
        return readRepository.findById(id);
    }

    public List<MessageResponse> findByChatId(UUID chatId, int limit, UUID before) {
        return findByChatId(chatId, limit, before, null, null);
    }

    public List<MessageResponse> findByChatId(UUID chatId, int limit, UUID before, UUID filterUserId) {
        return findByChatId(chatId, limit, before, filterUserId, null);
    }

    public List<MessageResponse> findByChatId(UUID chatId, int limit, UUID before, UUID filterUserId, UUID threadId) {
        return readRepository.findByChatId(chatId, limit, before, filterUserId, threadId);
    }

    public boolean updateContent(UUID msgId, UUID editedBy, String newContent) {
        return writeRepository.updateContent(msgId, editedBy, newContent);
    }

    public boolean delete(UUID msgId) {
        return writeRepository.softDelete(msgId);
    }

    public List<MessageVersionResponse> findVersions(UUID msgId) {
        return readRepository.findVersions(msgId);
    }

    public boolean addReaction(UUID messageId, UUID userId, String reaction) {
        return writeRepository.addReaction(messageId, userId, reaction);
    }

    public boolean removeReaction(UUID messageId, UUID userId, String reaction) {
        return writeRepository.removeReaction(messageId, userId, reaction);
    }

    public List<ReactionResponse> getReactions(UUID messageId) {
        return readRepository.getReactions(messageId);
    }

    public boolean pinMessage(UUID chatId, UUID messageId, UUID pinnedBy) {
        return writeRepository.pinMessage(chatId, messageId, pinnedBy);
    }

    public boolean unpinMessage(UUID chatId, UUID messageId) {
        return writeRepository.unpinMessage(chatId, messageId);
    }

    public List<PinnedMessageResponse> getPinnedMessages(UUID chatId) {
        return readRepository.getPinnedMessages(chatId);
    }

    public record FileMessageRef(UUID messageId, UUID chatId) {}

    public Optional<FileMessageRef> findLatestMessageRefForViewer(UUID fileId, UUID viewerId) {
        return readRepository.findLatestMessageRefForViewer(fileId, viewerId)
            .map(ref -> new FileMessageRef(ref.messageId(), ref.chatId()));
    }

    public boolean viewerMayAccessFileViaSharedNonE2eeMessage(UUID fileId, UUID viewerId) {
        return readRepository.viewerMayAccessFileViaSharedNonE2eeMessage(fileId, viewerId);
    }

    public List<MessageResponse> searchPlaintextForUser(UUID userId, List<UUID> chatIds, String queryText, int limit) {
        return readRepository.searchPlaintextForUser(userId, chatIds, queryText, limit);
    }

    public List<MessageResponse> loadMessagesForSearchResults(UUID userId, List<String> orderedIds, int limit) {
        return readRepository.loadMessagesForSearchResults(userId, orderedIds, limit);
    }
}
