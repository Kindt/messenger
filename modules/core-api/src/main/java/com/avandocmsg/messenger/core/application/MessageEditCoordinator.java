package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.core.domain.Message;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;

/** Hexagonal edit path: persist content change + Solr index NATS event. */
public final class MessageEditCoordinator {

    private final MessageRepositoryPort messageRepositoryPort;
    private final IndexerEventPublisher indexerEventPublisher;

    public MessageEditCoordinator(MessageRepositoryPort messageRepositoryPort, NatsOutboundPort natsOutbound) {
        this(messageRepositoryPort, new IndexerEventPublisher(natsOutbound));
    }

    public MessageEditCoordinator(MessageRepositoryPort messageRepositoryPort, IndexerEventPublisher indexerEventPublisher) {
        this.messageRepositoryPort = messageRepositoryPort;
        this.indexerEventPublisher = indexerEventPublisher;
    }

    public MessageResponse edit(MessageId messageId, UserId editorId, String newContent) {
        if (!messageRepositoryPort.updateContent(messageId, editorId, newContent)) {
            return null;
        }
        return messageRepositoryPort.findById(messageId)
            .map(updated -> {
                publishIndexUpdate(updated);
                return MessageDomainMapper.toResponse(updated);
            })
            .orElse(null);
    }

    private void publishIndexUpdate(Message updated) {
        if (indexerEventPublisher == null) {
            return;
        }
        indexerEventPublisher.publish(MessageWorkerEvent.fromPersistedMessage(
            updated.id().value().toString(),
            updated.chatId().value().toString(),
            updated.senderId().value().toString(),
            null,
            updated.createdAt() != null ? updated.createdAt().toEpochMilli() : null,
            updated.type(),
            updated.content(),
            "update"));
    }
}
