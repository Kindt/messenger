package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.common.dto.ReactionChangeEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/** Hexagonal reaction add/remove + NATS fan-out. */
public final class MessageReactionCoordinator {
    private static final Logger log = LoggerFactory.getLogger(MessageReactionCoordinator.class);
    private static final ObjectMapper MAPPER = MessengerJson.mapper();

    private final MessageRepositoryPort messageRepositoryPort;
    private final NatsOutboundPort natsOutbound;

    public MessageReactionCoordinator(MessageRepositoryPort messageRepositoryPort, NatsOutboundPort natsOutbound) {
        this.messageRepositoryPort = messageRepositoryPort;
        this.natsOutbound = natsOutbound;
    }

    public boolean addReaction(UUID chatId, MessageId messageId, UserId userId, String reaction) {
        if (reaction == null || reaction.isBlank()) {
            return false;
        }
        if (!messageRepositoryPort.addReaction(messageId, userId, reaction)) {
            return false;
        }
        publish("add", chatId, messageId, userId, reaction);
        return true;
    }

    public boolean removeReaction(UUID chatId, MessageId messageId, UserId userId, String reaction) {
        if (!messageRepositoryPort.removeReaction(messageId, userId, reaction)) {
            return false;
        }
        publish("remove", chatId, messageId, userId, reaction);
        return true;
    }

    private void publish(String action, UUID chatId, MessageId messageId, UserId userId, String reaction) {
        if (natsOutbound == null) {
            return;
        }
        try {
            var event = new ReactionChangeEvent(
                action,
                messageId.value().toString(),
                chatId.toString(),
                userId.value().toString(),
                reaction);
            natsOutbound.publish(NatsSubjects.MSG_REACTION, MAPPER.writeValueAsBytes(event));
        } catch (Exception e) {
            log.warn("Failed to publish reaction {} for {}", action, messageId, e);
        }
    }
}
