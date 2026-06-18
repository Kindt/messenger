package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.Message;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageInsert;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/** JDBC adapter for {@link MessageRepositoryPort} (read + insert with visibility TTL). */
public final class JdbcMessageRepositoryAdapter implements MessageRepositoryPort {
    private final MessageRepository messageRepository;

    public JdbcMessageRepositoryAdapter(DataSource dataSource) {
        this(new MessageRepository(dataSource, Clock.systemUTC()));
    }

    public JdbcMessageRepositoryAdapter(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Override
    public Optional<Message> findById(MessageId id) {
        return messageRepository.findById(id.value()).map(this::fromResponse);
    }

    @Override
    public Optional<Message> insert(MessageInsert command) {
        var response = messageRepository.insert(
            command.id().value(),
            command.chatId().value(),
            command.senderId().value(),
            command.type(),
            command.content(),
            command.replyToMsgId(),
            command.threadId(),
            command.clientMsgId(),
            command.visibilityTtlSeconds(),
            command.attachmentFileId());
        if (response == null) {
            return Optional.empty();
        }
        return Optional.of(fromResponse(response));
    }

    @Override
    public boolean updateContent(MessageId id, UserId senderId, String content) {
        return messageRepository.updateContent(id.value(), senderId.value(), content);
    }

    @Override
    public boolean softDelete(MessageId id) {
        return messageRepository.delete(id.value());
    }

    @Override
    public boolean addReaction(MessageId messageId, UserId userId, String reaction) {
        return messageRepository.addReaction(messageId.value(), userId.value(), reaction);
    }

    @Override
    public boolean removeReaction(MessageId messageId, UserId userId, String reaction) {
        return messageRepository.removeReaction(messageId.value(), userId.value(), reaction);
    }

    private Message fromResponse(com.avandocmsg.messenger.api.messages.dto.MessageResponse response) {
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
