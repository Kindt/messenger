package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.MessageMentionRepository;
import com.avandocmsg.messenger.common.dto.MentionEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Persist mentions and publish {@link NatsSubjects#MSG_MENTION} per target user. */
public final class MessageMentionCoordinator {
    private static final Logger log = LoggerFactory.getLogger(MessageMentionCoordinator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatRepository chatRepository;
    private final MessageMentionRepository mentionRepository;
    private final NatsOutboundPort natsOutbound;

    public MessageMentionCoordinator(
        ChatRepository chatRepository,
        MessageMentionRepository mentionRepository,
        NatsOutboundPort natsOutbound
    ) {
        this.chatRepository = chatRepository;
        this.mentionRepository = mentionRepository;
        this.natsOutbound = natsOutbound;
    }

    public void afterMessageSent(UUID chatId, UUID messageId, UUID senderId, String plaintextContent, long createdAtEpochMs) {
        if (mentionRepository == null || plaintextContent == null || plaintextContent.isBlank()) {
            return;
        }
        var parsed = MessageMentionParser.parse(plaintextContent);
        if (!parsed.mentionAll() && parsed.userIds().isEmpty()) {
            return;
        }
        var targets = resolveTargets(chatId, senderId, parsed);
        if (targets.isEmpty()) {
            return;
        }
        var rows = new ArrayList<MessageMentionRepository.MentionRow>();
        for (var userId : targets) {
            rows.add(new MessageMentionRepository.MentionRow(
                userId,
                parsed.mentionAll() ? "all" : "user"));
        }
        mentionRepository.insertMentions(messageId, rows);
        publishMentionEvents(chatId, messageId, senderId, targets, parsed.mentionAll(), createdAtEpochMs);
    }

    private Set<UUID> resolveTargets(UUID chatId, UUID senderId, MessageMentionParser.ParsedMentions parsed) {
        Set<UUID> targets = new HashSet<>();
        if (parsed.mentionAll()) {
            for (var member : chatRepository.listMembers(chatId)) {
                try {
                    var memberId = UUID.fromString(member.userId());
                    if (!memberId.equals(senderId)) {
                        targets.add(memberId);
                    }
                } catch (IllegalArgumentException ignored) {
                    // skip malformed member id
                }
            }
            return targets;
        }
        for (var userId : parsed.userIds()) {
            if (userId.equals(senderId)) {
                continue;
            }
            if (chatRepository.getMemberRole(chatId, userId) != null) {
                targets.add(userId);
            }
        }
        return targets;
    }

    private void publishMentionEvents(
        UUID chatId,
        UUID messageId,
        UUID senderId,
        Set<UUID> targets,
        boolean mentionAll,
        long createdAtEpochMs
    ) {
        if (natsOutbound == null) {
            return;
        }
        for (var target : targets) {
            try {
                var event = new MentionEvent(
                    messageId.toString(),
                    chatId.toString(),
                    senderId.toString(),
                    target.toString(),
                    mentionAll,
                    createdAtEpochMs);
                natsOutbound.publish(NatsSubjects.MSG_MENTION, MAPPER.writeValueAsBytes(event));
            } catch (Exception e) {
                log.warn("Failed to publish mention for message {} user {}", messageId, target, e);
            }
        }
    }
}
