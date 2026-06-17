package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.common.dto.MessageChangeEvent;
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

/** Hexagonal soft-delete path + index/change NATS events. */
public final class MessageDeleteCoordinator {
    private static final Logger log = LoggerFactory.getLogger(MessageDeleteCoordinator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MessageRepositoryPort messageRepositoryPort;
    private final NatsOutboundPort natsOutbound;

    public MessageDeleteCoordinator(MessageRepositoryPort messageRepositoryPort, NatsOutboundPort natsOutbound) {
        this.messageRepositoryPort = messageRepositoryPort;
        this.natsOutbound = natsOutbound;
    }

    public boolean delete(MessageId messageId, UserId deleterId) {
        var existing = messageRepositoryPort.findById(messageId).orElse(null);
        if (existing == null || existing.deleted() || !existing.senderId().equals(deleterId)) {
            return false;
        }
        if (!messageRepositoryPort.softDelete(messageId)) {
            return false;
        }
        publishIndexDelete(messageId);
        publishChange(existing);
        return true;
    }

    private void publishIndexDelete(MessageId messageId) {
        if (natsOutbound == null) {
            return;
        }
        try {
            natsOutbound.publish(
                NatsSubjects.MSG_EVENT_INDEX,
                MAPPER.writeValueAsBytes(MessageWorkerEvent.forIndexDelete(messageId.value().toString())));
        } catch (Exception e) {
            log.warn("Failed to publish index delete for {}", messageId, e);
        }
    }

    private void publishChange(Message msg) {
        if (natsOutbound == null) {
            return;
        }
        try {
            var event = new MessageChangeEvent(
                "delete",
                msg.id().value().toString(),
                msg.chatId().value().toString(),
                msg.senderId().value().toString(),
                msg.type(),
                null,
                msg.createdAt() != null ? msg.createdAt().toEpochMilli() : null,
                null);
            natsOutbound.publish(NatsSubjects.MSG_CHANGE, MAPPER.writeValueAsBytes(event));
        } catch (Exception e) {
            log.warn("Failed to publish message change for {}", msg.id(), e);
        }
    }
}
