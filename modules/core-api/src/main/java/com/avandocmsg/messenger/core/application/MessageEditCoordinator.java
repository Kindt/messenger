package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.domain.Message;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Hexagonal edit path: persist content change + Solr index NATS event. */
public final class MessageEditCoordinator {
    private static final Logger log = LoggerFactory.getLogger(MessageEditCoordinator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MessageRepositoryPort messageRepositoryPort;
    private final NatsOutboundPort natsOutbound;

    public MessageEditCoordinator(MessageRepositoryPort messageRepositoryPort, NatsOutboundPort natsOutbound) {
        this.messageRepositoryPort = messageRepositoryPort;
        this.natsOutbound = natsOutbound;
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
        if (natsOutbound == null) {
            return;
        }
        try {
            var event = MessageWorkerEvent.fromPersistedMessage(
                updated.id().value().toString(),
                updated.chatId().value().toString(),
                updated.senderId().value().toString(),
                null,
                updated.createdAt() != null ? updated.createdAt().toEpochMilli() : null,
                updated.type(),
                updated.content(),
                "update");
            natsOutbound.publish(NatsSubjects.MSG_EVENT_INDEX, MAPPER.writeValueAsBytes(event));
        } catch (Exception e) {
            log.warn("Failed to publish index update for message {}", updated.id(), e);
        }
    }
}
