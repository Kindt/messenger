package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.core.port.MessageMentionRepositoryPort;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Attaches mention summaries to {@link MessageResponse} list reads (hex adapter layer). */
final class MessageMentionEnrichment {
    private MessageMentionEnrichment() {
    }

    static void attachMentions(MessageMentionRepositoryPort mentionRepositoryPort, List<MessageResponse> messages) {
        if (mentionRepositoryPort == null || messages == null || messages.isEmpty()) {
            return;
        }
        var ids = new ArrayList<UUID>(messages.size());
        for (var m : messages) {
            try {
                ids.add(UUID.fromString(m.id()));
            } catch (IllegalArgumentException ignored) {
                // skip
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        var summaries = mentionRepositoryPort.findSummariesByMessageIds(ids);
        for (int i = 0; i < messages.size(); i++) {
            var m = messages.get(i);
            try {
                var summary = summaries.get(UUID.fromString(m.id()));
                if (summary == null) {
                    continue;
                }
                messages.set(i, new MessageResponse(
                    m.id(), m.chatId(), m.senderId(), m.type(), m.content(), m.replyToMsgId(),
                    m.deleted(), m.createdAt(), m.editedAt(), m.visibilityTtlSeconds(), m.attachmentFileId(),
                    m.threadId(), m.threadReplyCount(),
                    summary.userIds().isEmpty() ? null : summary.userIds(),
                    summary.mentionAll() ? true : null,
                    m.durationMs(),
                    m.linkPreview(),
                    m.replyPreview()));
            } catch (IllegalArgumentException ignored) {
                // skip
            }
        }
    }
}
