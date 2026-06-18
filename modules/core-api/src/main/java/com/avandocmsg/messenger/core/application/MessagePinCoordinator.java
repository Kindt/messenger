package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.common.dto.PinChangeEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/** Hexagonal pin/unpin path + NATS fan-out. */
public final class MessagePinCoordinator {
    private static final Logger log = LoggerFactory.getLogger(MessagePinCoordinator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MessageRepository messageRepository;
    private final NatsOutboundPort natsOutbound;

    public MessagePinCoordinator(MessageRepository messageRepository, NatsOutboundPort natsOutbound) {
        this.messageRepository = messageRepository;
        this.natsOutbound = natsOutbound;
    }

    public boolean pin(UUID chatId, UUID msgId, UUID userId) {
        if (!messageRepository.pinMessage(chatId, msgId, userId)) {
            return false;
        }
        publish("pin", chatId, msgId, userId, System.currentTimeMillis());
        return true;
    }

    public boolean unpin(UUID chatId, UUID msgId, UUID userId) {
        if (!messageRepository.unpinMessage(chatId, msgId)) {
            return false;
        }
        publish("unpin", chatId, msgId, userId, null);
        return true;
    }

    private void publish(String action, UUID chatId, UUID msgId, UUID userId, Long pinnedAtEpochMs) {
        if (natsOutbound == null) {
            return;
        }
        try {
            var event = new PinChangeEvent(
                action,
                chatId.toString(),
                msgId.toString(),
                userId.toString(),
                pinnedAtEpochMs);
            natsOutbound.publish(NatsSubjects.MSG_PIN, MAPPER.writeValueAsBytes(event));
        } catch (Exception e) {
            log.warn("Failed to publish pin {} for {}", action, msgId, e);
        }
    }
}
