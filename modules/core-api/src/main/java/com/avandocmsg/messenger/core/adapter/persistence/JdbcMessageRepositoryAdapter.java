package com.avandocmsg.messenger.core.adapter.persistence;



import com.avandocmsg.messenger.api.messages.dto.MessageResponse;

import com.avandocmsg.messenger.api.messages.dto.MessageVersionResponse;

import com.avandocmsg.messenger.api.messages.dto.PinnedMessageResponse;

import com.avandocmsg.messenger.api.messages.dto.ReactionResponse;

import com.avandocmsg.messenger.api.repository.MessageRepository;

import com.avandocmsg.messenger.core.domain.ChatId;

import com.avandocmsg.messenger.core.domain.Message;

import com.avandocmsg.messenger.core.domain.MessageId;

import com.avandocmsg.messenger.core.domain.UserId;

import com.avandocmsg.messenger.core.port.FileMessageRef;

import com.avandocmsg.messenger.core.port.MessageInsert;

import com.avandocmsg.messenger.core.port.MessageMentionRepositoryPort;

import com.avandocmsg.messenger.core.port.MessageQueryPort;

import com.avandocmsg.messenger.core.port.MessageRepositoryPort;



import javax.sql.DataSource;

import java.time.Clock;

import java.util.List;

import java.util.Optional;

import java.util.UUID;



/** JDBC adapter for {@link MessageRepositoryPort} and {@link MessageQueryPort}. */

public final class JdbcMessageRepositoryAdapter implements MessageRepositoryPort, MessageQueryPort {

    private final JdbcMessageReadRepository readRepository;

    private final JdbcMessageWriteRepository writeRepository;

    private final MessageMentionRepositoryPort mentionRepositoryPort;



    public JdbcMessageRepositoryAdapter(DataSource dataSource) {

        this(dataSource, null, Clock.systemUTC(), 0, null);

    }



    public JdbcMessageRepositoryAdapter(

        DataSource dataSource,

        DataSource readDataSource,

        Clock clock,

        int queryTimeoutSeconds,

        MessageMentionRepositoryPort mentionRepositoryPort

    ) {

        var write = new JdbcMessageWriteRepository(dataSource, readDataSource, clock, queryTimeoutSeconds);

        var read = new JdbcMessageReadRepository(dataSource, readDataSource, queryTimeoutSeconds);

        this.readRepository = read;

        this.writeRepository = write;

        this.mentionRepositoryPort = mentionRepositoryPort != null

            ? mentionRepositoryPort

            : new JdbcMessageMentionRepositoryAdapter(dataSource);

    }



    /** Legacy wiring via {@link MessageRepository} façade (tests). */

    public JdbcMessageRepositoryAdapter(MessageRepository messageRepository) {

        this(messageRepository, null, messageRepository.jdbcWriteRepository());

    }



    public JdbcMessageRepositoryAdapter(

        MessageRepository messageRepository,

        MessageMentionRepositoryPort mentionRepositoryPort

    ) {

        this(messageRepository, mentionRepositoryPort, messageRepository.jdbcWriteRepository());

    }



    public JdbcMessageRepositoryAdapter(

        MessageRepository messageRepository,

        MessageMentionRepositoryPort mentionRepositoryPort,

        JdbcMessageWriteRepository writeRepository

    ) {

        this.readRepository = messageRepository.jdbcReadRepository();
        this.writeRepository = writeRepository != null ? writeRepository : messageRepository.jdbcWriteRepository();
        this.mentionRepositoryPort = mentionRepositoryPort != null
            ? mentionRepositoryPort
            : new JdbcMessageMentionRepositoryAdapter(this.writeRepository.dataSource());

    }



    public JdbcMessageRepositoryAdapter(

        JdbcMessageReadRepository readRepository,

        JdbcMessageWriteRepository writeRepository,

        MessageMentionRepositoryPort mentionRepositoryPort

    ) {

        this.readRepository = readRepository;

        this.writeRepository = writeRepository;

        this.mentionRepositoryPort = mentionRepositoryPort;

    }



    @Override

    public Optional<Message> findById(MessageId id) {

        return readRepository.findById(id.value()).map(this::fromResponse);

    }



    @Override

    public Optional<Message> insert(MessageInsert command) {

        var response = writeRepository.insert(

            command.id().value(),

            command.chatId().value(),

            command.senderId().value(),

            command.type(),

            command.content(),

            command.replyToMsgId(),

            command.threadId(),

            command.clientMsgId(),

            command.visibilityTtlSeconds(),

            command.attachmentFileId(),

            command.voiceDurationMs());

        if (response == null) {

            return Optional.empty();

        }

        return Optional.of(fromResponse(response));

    }



    @Override

    public boolean existsClientMsgId(ChatId chatId, UserId senderId, String clientMsgId) {

        return writeRepository.existsClientMsgId(chatId.value(), senderId.value(), clientMsgId);

    }



    @Override

    public boolean updateContent(MessageId id, UserId senderId, String content) {

        return writeRepository.updateContent(id.value(), senderId.value(), content);

    }



    @Override

    public boolean softDelete(MessageId id) {

        return writeRepository.softDelete(id.value());

    }



    @Override

    public boolean addReaction(MessageId messageId, UserId userId, String reaction) {

        return writeRepository.addReaction(messageId.value(), userId.value(), reaction);

    }



    @Override

    public boolean removeReaction(MessageId messageId, UserId userId, String reaction) {

        return writeRepository.removeReaction(messageId.value(), userId.value(), reaction);

    }



    @Override

    public boolean pinMessage(ChatId chatId, MessageId messageId, UserId pinnedBy) {

        return writeRepository.pinMessage(chatId.value(), messageId.value(), pinnedBy.value());

    }



    @Override

    public boolean unpinMessage(ChatId chatId, MessageId messageId) {

        return writeRepository.unpinMessage(chatId.value(), messageId.value());

    }



    @Override

    public List<MessageResponse> findByChatId(UUID chatId, int limit, UUID before, UUID filterUserId, UUID threadId) {

        var result = readRepository.findByChatId(chatId, limit, before, filterUserId, threadId);

        MessageMentionEnrichment.attachMentions(mentionRepositoryPort, result);

        return result;

    }



    @Override

    public List<MessageVersionResponse> findVersions(UUID msgId) {

        return readRepository.findVersions(msgId);

    }



    @Override

    public List<ReactionResponse> getReactions(UUID messageId) {

        return readRepository.getReactions(messageId);

    }



    @Override

    public List<PinnedMessageResponse> getPinnedMessages(UUID chatId) {

        return readRepository.getPinnedMessages(chatId);

    }



    @Override

    public boolean viewerMayAccessFileViaSharedNonE2eeMessage(UUID fileId, UUID viewerId) {

        return readRepository.viewerMayAccessFileViaSharedNonE2eeMessage(fileId, viewerId);

    }



    @Override

    public Optional<FileMessageRef> findLatestMessageRefForViewer(UUID fileId, UUID viewerId) {

        return readRepository.findLatestMessageRefForViewer(fileId, viewerId);

    }



    @Override

    public Optional<MessageId> findLatestMessageId(ChatId chatId) {

        return readRepository.findLatestMessageId(chatId.value()).map(MessageId::of);

    }



    @Override

    public List<MessageResponse> searchPlaintextForUser(UserId userId, List<UUID> chatIds, String queryText, int limit) {

        var result = readRepository.searchPlaintextForUser(userId.value(), chatIds, queryText, limit);

        MessageMentionEnrichment.attachMentions(mentionRepositoryPort, result);

        return result;

    }



    @Override

    public List<MessageResponse> loadMessagesForSearchResults(

        UserId userId, List<String> messageIdsInOrder, int limit) {

        var result = readRepository.loadMessagesForSearchResults(userId.value(), messageIdsInOrder, limit);

        MessageMentionEnrichment.attachMentions(mentionRepositoryPort, result);

        return result;

    }



    private Message fromResponse(MessageResponse response) {

        return new Message(

            MessageId.of(UUID.fromString(response.id())),

            ChatId.of(UUID.fromString(response.chatId())),

            UserId.of(UUID.fromString(response.senderId())),

            response.type(),

            response.content(),

            response.replyToMsgId(),

            response.threadId(),

            response.deleted(),

            response.createdAt(),

            response.editedAt(),

            response.visibilityTtlSeconds(),

            response.attachmentFileId());

    }

}


