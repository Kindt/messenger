package com.avandocmsg.messenger.api.chats;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.api.chats.dto.ReadReceiptResponse;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.metrics.ReadReceiptMetrics;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.port.AuditPort;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.core.port.ChatReadStatePort;
import com.avandocmsg.messenger.core.port.MessageReadReceiptPort;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import com.avandocmsg.messenger.core.port.UserLookupPort;
import com.avandocmsg.messenger.common.dto.ReadReceiptEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.adapter.cache.NoOpReadCacheAdapter;
import com.avandocmsg.messenger.core.application.ReadCacheCoordinator;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.ReadCachePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ReadReceiptService {
    private static final Logger log = LoggerFactory.getLogger(ReadReceiptService.class);
    private static final ObjectMapper JSON = MessengerJson.mapper();

    private final MessageReadReceiptPort readReceiptPort;
    private final ChatPersistencePort chatPersistencePort;
    private final MessageRepositoryPort messageRepositoryPort;
    private final ChatReadStatePort chatReadStatePort;
    private final UserLookupPort userLookupPort;
    private final AuditPort auditPort;
    private final NatsOutboundPort natsOutbound;
    private final AppConfig appConfig;
    private final Clock clock;
    private final ReadCachePort readCache;

    public ReadReceiptService(MessageReadReceiptPort readReceiptPort,
                              ChatPersistencePort chatPersistencePort,
                              MessageRepositoryPort messageRepositoryPort,
                              ChatReadStatePort chatReadStatePort,
                              UserLookupPort userLookupPort,
                              AuditPort auditPort,
                              NatsOutboundPort natsOutbound,
                              AppConfig appConfig,
                              Clock clock) {
        this(readReceiptPort, chatPersistencePort, messageRepositoryPort, chatReadStatePort, userLookupPort,
            auditPort, natsOutbound, appConfig, clock, NoOpReadCacheAdapter.INSTANCE);
    }

    public ReadReceiptService(MessageReadReceiptPort readReceiptPort,
                              ChatPersistencePort chatPersistencePort,
                              MessageRepositoryPort messageRepositoryPort,
                              ChatReadStatePort chatReadStatePort,
                              UserLookupPort userLookupPort,
                              AuditPort auditPort,
                              NatsOutboundPort natsOutbound,
                              AppConfig appConfig,
                              Clock clock,
                              ReadCachePort readCache) {
        this.readReceiptPort = readReceiptPort;
        this.chatPersistencePort = chatPersistencePort;
        this.messageRepositoryPort = messageRepositoryPort;
        this.chatReadStatePort = chatReadStatePort;
        this.userLookupPort = userLookupPort;
        this.auditPort = auditPort;
        this.natsOutbound = natsOutbound;
        this.appConfig = appConfig;
        this.clock = clock;
        this.readCache = readCache;
    }

    public enum MarkResult {
        OK,
        NOT_MEMBER,
        MESSAGE_NOT_FOUND,
        BATCH_TOO_LARGE
    }

    public MarkResult markMessageRead(UUID chatId, UUID userId, UUID messageId) {
        if (chatPersistencePort.getMemberRole(chatId, userId) == null) {
            return MarkResult.NOT_MEMBER;
        }
        var msg = messageRepositoryPort.findById(MessageId.of(messageId)).orElse(null);
        if (msg == null || !msg.chatId().value().equals(chatId)) {
            return MarkResult.MESSAGE_NOT_FOUND;
        }
        if (userLookupPort.isReadReceiptsDisabled(userId)) {
            chatReadStatePort.upsertLastRead(userId, chatId, messageId);
            ReadCacheCoordinator.invalidateChatUnread(readCache, userId);
            return MarkResult.OK;
        }
        var now = clock.instant();
        var inserted = readReceiptPort.insert(messageId, userId, now);
        chatReadStatePort.upsertLastRead(userId, chatId, messageId);
        if (inserted) {
            ReadReceiptMetrics.insertRecorded();
            auditPort.record(userId, "message.read", "message", messageId.toString(), null);
            publishReceipt(ReadReceiptEvent.single(chatId.toString(), messageId.toString(), userId.toString(),
                now.toEpochMilli()));
        }
        ReadCacheCoordinator.invalidateChatUnread(readCache, userId);
        return MarkResult.OK;
    }

    public MarkResult markBatchRead(UUID chatId, UUID userId, List<UUID> messageIds) {
        if (chatPersistencePort.getMemberRole(chatId, userId) == null) {
            return MarkResult.NOT_MEMBER;
        }
        if (messageIds == null || messageIds.isEmpty()) {
            return MarkResult.OK;
        }
        if (messageIds.size() > appConfig.readReceiptBatchMax()) {
            return MarkResult.BATCH_TOO_LARGE;
        }
        var validated = new ArrayList<UUID>();
        for (var messageId : messageIds) {
            var msg = messageRepositoryPort.findById(MessageId.of(messageId)).orElse(null);
            if (msg == null || !msg.chatId().value().equals(chatId)) {
                return MarkResult.MESSAGE_NOT_FOUND;
            }
            validated.add(messageId);
        }
        if (userLookupPort.isReadReceiptsDisabled(userId)) {
            upsertAggregateRead(chatId, userId, validated);
            ReadCacheCoordinator.invalidateChatUnread(readCache, userId);
            return MarkResult.OK;
        }
        var now = clock.instant();
        var inserted = readReceiptPort.insertBatch(validated, userId, now);
        upsertAggregateRead(chatId, userId, validated);
        ReadReceiptMetrics.batchRecorded(validated.size());
        if (inserted > 0) {
            auditPort.record(userId, "message.read.batch", "chat", chatId.toString(),
                "{\"count\":" + inserted + "}");
            var ids = validated.stream().map(UUID::toString).toList();
            publishReceipt(ReadReceiptEvent.batch(chatId.toString(), userId.toString(), now.toEpochMilli(), ids));
        }
        ReadCacheCoordinator.invalidateChatUnread(readCache, userId);
        return MarkResult.OK;
    }

    public Optional<ReadReceiptResponse> listForMessage(UUID chatId, UUID viewerId, UUID messageId, int offset, int limit) {
        if (chatPersistencePort.getMemberRole(chatId, viewerId) == null) {
            return Optional.empty();
        }
        var msg = messageRepositoryPort.findById(MessageId.of(messageId)).orElse(null);
        if (msg == null || !msg.chatId().value().equals(chatId)) {
            return Optional.empty();
        }
        var rows = readReceiptPort.findByMessageId(messageId, offset, limit);
        return Optional.of(new ReadReceiptResponse(messageId.toString(), rows));
    }

    public long totalRows() {
        return readReceiptPort.countAll();
    }

    private void upsertAggregateRead(UUID chatId, UUID userId, List<UUID> messageIds) {
        messageIds.stream()
            .map(id -> messageRepositoryPort.findById(MessageId.of(id)).orElse(null))
            .filter(m -> m != null)
            .max(Comparator.comparing(m -> m.createdAt()))
            .ifPresent(latest -> chatReadStatePort.upsertLastRead(userId, chatId, latest.id().value()));
    }

    private void publishReceipt(ReadReceiptEvent event) {
        try {
            var bytes = JSON.writeValueAsBytes(event);
            natsOutbound.publish(NatsSubjects.MSG_READ_RECEIPT, bytes);
        } catch (Exception e) {
            log.debug("read receipt publish failed: {}", e.getMessage());
        }
    }
}
