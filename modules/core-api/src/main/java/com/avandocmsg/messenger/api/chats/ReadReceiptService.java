package com.avandocmsg.messenger.api.chats;

import com.avandocmsg.messenger.api.chats.dto.ReadReceiptResponse;
import com.avandocmsg.messenger.api.chats.dto.ReadReceiptUserInfo;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.metrics.ReadReceiptMetrics;
import com.avandocmsg.messenger.api.repository.AuditRepository;
import com.avandocmsg.messenger.api.repository.ChatReadRepository;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.MessageReadReceiptRepository;
import com.avandocmsg.messenger.api.repository.UserRepository;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import com.avandocmsg.messenger.common.dto.ReadReceiptEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.adapter.cache.NoOpReadCacheAdapter;
import com.avandocmsg.messenger.core.application.ReadCacheCoordinator;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.ReadCachePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ReadReceiptService {
    private static final Logger log = LoggerFactory.getLogger(ReadReceiptService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final MessageReadReceiptRepository readReceiptRepository;
    private final ChatRepository chatRepository;
    private final MessageRepositoryPort messageRepositoryPort;
    private final ChatReadRepository chatReadRepository;
    private final UserRepository userRepository;
    private final AuditRepository auditRepository;
    private final NatsOutboundPort natsOutbound;
    private final AppConfig appConfig;
    private final Clock clock;
    private final ReadCachePort readCache;

    public ReadReceiptService(MessageReadReceiptRepository readReceiptRepository,
                              ChatRepository chatRepository,
                              MessageRepositoryPort messageRepositoryPort,
                              ChatReadRepository chatReadRepository,
                              UserRepository userRepository,
                              AuditRepository auditRepository,
                              NatsOutboundPort natsOutbound,
                              AppConfig appConfig,
                              Clock clock) {
        this(readReceiptRepository, chatRepository, messageRepositoryPort, chatReadRepository, userRepository,
            auditRepository, natsOutbound, appConfig, clock, NoOpReadCacheAdapter.INSTANCE);
    }

    public ReadReceiptService(MessageReadReceiptRepository readReceiptRepository,
                              ChatRepository chatRepository,
                              MessageRepositoryPort messageRepositoryPort,
                              ChatReadRepository chatReadRepository,
                              UserRepository userRepository,
                              AuditRepository auditRepository,
                              NatsOutboundPort natsOutbound,
                              AppConfig appConfig,
                              Clock clock,
                              ReadCachePort readCache) {
        this.readReceiptRepository = readReceiptRepository;
        this.chatRepository = chatRepository;
        this.messageRepositoryPort = messageRepositoryPort;
        this.chatReadRepository = chatReadRepository;
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
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
        if (chatRepository.getMemberRole(chatId, userId) == null) {
            return MarkResult.NOT_MEMBER;
        }
        var msg = messageRepositoryPort.findById(MessageId.of(messageId)).orElse(null);
        if (msg == null || !msg.chatId().value().equals(chatId)) {
            return MarkResult.MESSAGE_NOT_FOUND;
        }
        if (userRepository.isReadReceiptsDisabled(userId)) {
            chatReadRepository.upsertLastRead(userId, chatId, messageId);
            ReadCacheCoordinator.invalidateChatUnread(readCache, userId);
            return MarkResult.OK;
        }
        var now = clock.instant();
        var inserted = readReceiptRepository.insert(messageId, userId, now);
        chatReadRepository.upsertLastRead(userId, chatId, messageId);
        if (inserted) {
            ReadReceiptMetrics.insertRecorded();
            auditRepository.record(userId, "message.read", "message", messageId.toString(), null);
            publishReceipt(ReadReceiptEvent.single(chatId.toString(), messageId.toString(), userId.toString(),
                now.toEpochMilli()));
        }
        ReadCacheCoordinator.invalidateChatUnread(readCache, userId);
        return MarkResult.OK;
    }

    public MarkResult markBatchRead(UUID chatId, UUID userId, List<UUID> messageIds) {
        if (chatRepository.getMemberRole(chatId, userId) == null) {
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
        if (userRepository.isReadReceiptsDisabled(userId)) {
            upsertAggregateRead(chatId, userId, validated);
            ReadCacheCoordinator.invalidateChatUnread(readCache, userId);
            return MarkResult.OK;
        }
        var now = clock.instant();
        var inserted = readReceiptRepository.insertBatch(validated, userId, now);
        upsertAggregateRead(chatId, userId, validated);
        ReadReceiptMetrics.batchRecorded(validated.size());
        if (inserted > 0) {
            auditRepository.record(userId, "message.read.batch", "chat", chatId.toString(),
                "{\"count\":" + inserted + "}");
            var ids = validated.stream().map(UUID::toString).toList();
            publishReceipt(ReadReceiptEvent.batch(chatId.toString(), userId.toString(), now.toEpochMilli(), ids));
        }
        ReadCacheCoordinator.invalidateChatUnread(readCache, userId);
        return MarkResult.OK;
    }

    public Optional<ReadReceiptResponse> listForMessage(UUID chatId, UUID viewerId, UUID messageId, int offset, int limit) {
        if (chatRepository.getMemberRole(chatId, viewerId) == null) {
            return Optional.empty();
        }
        var msg = messageRepositoryPort.findById(MessageId.of(messageId)).orElse(null);
        if (msg == null || !msg.chatId().value().equals(chatId)) {
            return Optional.empty();
        }
        var rows = readReceiptRepository.findByMessageId(messageId, offset, limit);
        return Optional.of(new ReadReceiptResponse(messageId.toString(), rows));
    }

    public long totalRows() {
        return readReceiptRepository.countAll();
    }

    private void upsertAggregateRead(UUID chatId, UUID userId, List<UUID> messageIds) {
        messageIds.stream()
            .map(id -> messageRepositoryPort.findById(MessageId.of(id)).orElse(null))
            .filter(m -> m != null)
            .max(Comparator.comparing(m -> m.createdAt()))
            .ifPresent(latest -> chatReadRepository.upsertLastRead(userId, chatId, latest.id().value()));
    }

    private void publishReceipt(ReadReceiptEvent event) {
        try {
            var bytes = JSON.writeValueAsString(event).getBytes(StandardCharsets.UTF_8);
            natsOutbound.publish(NatsSubjects.MSG_READ_RECEIPT, bytes);
        } catch (Exception e) {
            log.debug("read receipt publish failed: {}", e.getMessage());
        }
    }
}
