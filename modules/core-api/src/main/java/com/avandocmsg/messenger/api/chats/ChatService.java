package com.avandocmsg.messenger.api.chats;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.chats.dto.ChatMemberResponse;
import com.avandocmsg.messenger.api.chats.dto.ChatResponse;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.BlockRepositoryPort;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.core.port.ChatReadStatePort;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageQueryPort;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import com.avandocmsg.messenger.common.dto.TypingEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.application.ReadCacheCoordinator;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.ReadCacheKeys;
import com.avandocmsg.messenger.core.port.ReadCacheKind;
import com.avandocmsg.messenger.core.port.ReadCachePort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<ChatResponse>> CHAT_LIST_TYPE = new TypeReference<>() {
    };

    private final ChatPersistencePort chatPersistencePort;
    private final BlockRepositoryPort blockRepositoryPort;
    private final ChatReadStatePort chatReadStatePort;
    private final MessageRepositoryPort messageRepositoryPort;
    private final MessageQueryPort messageQueryPort;
    private final NatsOutboundPort natsOutbound;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;
    private final ReadCachePort readCachePort;
    private final AppConfig appConfig;

    public ChatService(ChatPersistencePort chatPersistencePort, BlockRepositoryPort blockRepositoryPort,
                       ChatReadStatePort chatReadStatePort, MessageRepositoryPort messageRepositoryPort,
                       MessageQueryPort messageQueryPort,
                       NatsOutboundPort natsOutbound, Clock clock, UuidGenerator uuidGenerator,
                       ReadCachePort readCachePort, AppConfig appConfig) {
        this.chatPersistencePort = chatPersistencePort;
        this.blockRepositoryPort = blockRepositoryPort;
        this.chatReadStatePort = chatReadStatePort;
        this.messageRepositoryPort = messageRepositoryPort;
        this.messageQueryPort = messageQueryPort;
        this.natsOutbound = natsOutbound;
        this.clock = clock;
        this.uuidGenerator = uuidGenerator;
        this.readCachePort = readCachePort;
        this.appConfig = appConfig;
    }

    public ChatResponse createGroup(String title, UUID ownerId, List<String> memberIds) {
        if (title == null || title.isBlank()) {
            return null;
        }
        var chatId = uuidGenerator.randomUuid();
        var chat = chatPersistencePort.createGroup(chatId, title, ownerId);
        if (chat == null) {
            return null;
        }
        if (memberIds != null) {
            for (var mid : memberIds) {
                var memberUuid = UUID.fromString(mid);
                if (!memberUuid.equals(ownerId)) {
                    chatPersistencePort.addMember(chatId, memberUuid, "member");
                }
            }
        }
        invalidateChatMutationForMembers(ownerId, memberIds);
        return chatPersistencePort.findById(chatId, ownerId).orElse(null);
    }

    public ChatResponse createChannel(String title, UUID ownerId, List<String> memberIds) {
        if (title == null || title.isBlank()) {
            return null;
        }
        var chatId = uuidGenerator.randomUuid();
        var chat = chatPersistencePort.createChannel(chatId, title, ownerId);
        if (chat == null) {
            return null;
        }
        if (memberIds != null) {
            for (var mid : memberIds) {
                var memberUuid = UUID.fromString(mid);
                if (!memberUuid.equals(ownerId)) {
                    chatPersistencePort.addMember(chatId, memberUuid, "member");
                }
            }
        }
        invalidateChatMutationForMembers(ownerId, memberIds);
        return chatPersistencePort.findById(chatId, ownerId).orElse(null);
    }

    public ChatResponse findOrCreateP2P(UUID user1Id, UUID user2Id) {
        if (user1Id.equals(user2Id)) {
            return null;
        }
        var existing = chatPersistencePort.findP2PChat(user1Id, user2Id);
        if (existing.isPresent()) {
            return chatPersistencePort.findById(existing.get(), user1Id).orElse(null);
        }
        if (blockRepositoryPort.exists(UserId.of(user1Id), UserId.of(user2Id))
            || blockRepositoryPort.exists(UserId.of(user2Id), UserId.of(user1Id))) {
            return null;
        }
        var chatId = uuidGenerator.randomUuid();
        var created = chatPersistencePort.createP2P(chatId, user1Id, user2Id);
        if (created != null) {
            ReadCacheCoordinator.invalidateAfterChatMutation(readCachePort, user1Id, user2Id);
        }
        return created;
    }

    public List<ChatResponse> list(UUID userId) {
        if (readCachePort.enabled()) {
            var key = ReadCacheKeys.chatList(userId);
            var cached = readCachePort.get(key);
            if (cached.isPresent()) {
                try {
                    return JSON.readValue(cached.get(), CHAT_LIST_TYPE);
                } catch (Exception e) {
                    log.debug("chat list cache decode failed for {}: {}", userId, e.toString());
                    readCachePort.invalidate(key);
                }
            }
            var list = chatPersistencePort.listByUser(userId);
            try {
                readCachePort.put(key, JSON.writeValueAsString(list),
                    appConfig.readCacheTtlSeconds(ReadCacheKind.CHAT_LIST));
            } catch (Exception e) {
                log.debug("chat list cache encode failed for {}: {}", userId, e.toString());
            }
            return list;
        }
        return chatPersistencePort.listByUser(userId);
    }

    public ChatResponse getById(UUID chatId, UUID userId) {
        return chatPersistencePort.findById(chatId, userId).orElse(null);
    }

    public boolean updateTitle(UUID chatId, UUID userId, String title) {
        var role = chatPersistencePort.getMemberRole(chatId, userId);
        if (role == null || (!role.equals("owner") && !role.equals("admin"))) {
            log.warn("User {} not authorized to update chat {}", userId, chatId);
            return false;
        }
        return chatPersistencePort.updateTitle(chatId, title);
    }

    public boolean setMuted(UUID chatId, UUID userId, boolean muted) {
        return chatPersistencePort.setMuted(chatId, userId, muted);
    }

    public boolean setArchived(UUID chatId, UUID userId, boolean archived) {
        if (chatPersistencePort.getMemberRole(chatId, userId) == null) {
            return false;
        }
        var ok = chatPersistencePort.setArchived(chatId, userId, archived);
        if (ok) {
            ReadCacheCoordinator.invalidateAfterChatMutation(readCachePort, userId);
        }
        return ok;
    }

    public boolean setFolderTag(UUID chatId, UUID userId, String folderTag) {
        if (chatPersistencePort.getMemberRole(chatId, userId) == null) {
            return false;
        }
        var ok = chatPersistencePort.setFolderTag(chatId, userId, folderTag);
        if (ok) {
            ReadCacheCoordinator.invalidateAfterChatMutation(readCachePort, userId);
        }
        return ok;
    }

    public boolean setPersonalFilter(UUID chatId, UUID userId, boolean active) {
        return chatPersistencePort.setPersonalFilterActive(chatId, userId, active);
    }

    public boolean addMember(UUID chatId, UUID actorId, UUID targetUserId) {
        var role = chatPersistencePort.getMemberRole(chatId, actorId);
        if (role == null || (!role.equals("owner") && !role.equals("admin"))) {
            return false;
        }
        if (blockRepositoryPort.exists(UserId.of(targetUserId), UserId.of(actorId))) {
            return false;
        }
        var ok = chatPersistencePort.addMember(chatId, targetUserId, "member");
        if (ok) {
            ReadCacheCoordinator.invalidateAfterChatMutation(readCachePort, actorId, targetUserId);
        }
        return ok;
    }

    public boolean removeMember(UUID chatId, UUID actorId, UUID targetUserId) {
        var actorRole = chatPersistencePort.getMemberRole(chatId, actorId);
        var targetRole = chatPersistencePort.getMemberRole(chatId, targetUserId);
        if (actorRole == null || targetRole == null) {
            return false;
        }
        if (actorRole.equals("owner")) {
            var ok = chatPersistencePort.removeMember(chatId, targetUserId);
            if (ok) {
                ReadCacheCoordinator.invalidateAfterChatMutation(readCachePort, actorId, targetUserId);
            }
            return ok;
        }
        if (actorRole.equals("admin") && !targetRole.equals("owner")) {
            var ok = chatPersistencePort.removeMember(chatId, targetUserId);
            if (ok) {
                ReadCacheCoordinator.invalidateAfterChatMutation(readCachePort, actorId, targetUserId);
            }
            return ok;
        }
        return false;
    }

    public boolean setRole(UUID chatId, UUID actorId, UUID targetUserId, String newRole) {
        var actorRole = chatPersistencePort.getMemberRole(chatId, actorId);
        var targetRole = chatPersistencePort.getMemberRole(chatId, targetUserId);
        if (actorRole == null || targetRole == null) {
            return false;
        }
        if (!actorRole.equals("owner")) {
            return false;
        }
        if (!newRole.equals("admin") && !newRole.equals("member")) {
            return false;
        }
        return chatPersistencePort.setRole(chatId, targetUserId, newRole);
    }

    /**
     * Members list is visible only to users who are members of the chat (including banned members with a row in {@code chat_members}).
     */
    public Optional<List<ChatMemberResponse>> listMembersForViewer(UUID chatId, UUID viewerId) {
        if (chatPersistencePort.getMemberRole(chatId, viewerId) == null) {
            return Optional.empty();
        }
        return Optional.of(chatPersistencePort.listMembers(chatId));
    }

    public boolean markRead(UUID chatId, UUID userId, UUID upToMessageId) {
        if (chatPersistencePort.getMemberRole(chatId, userId) == null) {
            return false;
        }
        UUID markId = upToMessageId;
        if (markId == null) {
            markId = messageQueryPort.findLatestMessageId(ChatId.of(chatId)).map(MessageId::value).orElse(null);
        }
        if (markId == null) {
            return true;
        }
        var msg = messageRepositoryPort.findById(MessageId.of(markId)).orElse(null);
        if (msg == null || !msg.chatId().value().equals(chatId)) {
            return false;
        }
        var ok = chatReadStatePort.upsertLastRead(userId, chatId, markId);
        if (ok) {
            ReadCacheCoordinator.invalidateChatUnread(readCachePort, userId);
        }
        return ok;
    }

    public int unreadCount(UUID chatId, UUID userId) {
        if (chatPersistencePort.getMemberRole(chatId, userId) == null) {
            return -1;
        }
        if (readCachePort.enabled()) {
            var key = ReadCacheKeys.chatUnread(userId) + ":" + chatId;
            var cached = readCachePort.get(key);
            if (cached.isPresent()) {
                try {
                    return Integer.parseInt(cached.get().trim());
                } catch (NumberFormatException e) {
                    readCachePort.invalidate(key);
                }
            }
            var count = chatReadStatePort.countUnreadFromOthers(userId, chatId);
            readCachePort.put(key, Integer.toString(count),
                appConfig.readCacheTtlSeconds(ReadCacheKind.CHAT_UNREAD));
            return count;
        }
        return chatReadStatePort.countUnreadFromOthers(userId, chatId);
    }

    public void publishTyping(UUID chatId, UUID userId) {
        if (chatPersistencePort.getMemberRole(chatId, userId) == null) {
            return;
        }
        try {
            var ev = new TypingEvent(chatId.toString(), userId.toString(), clock.millis());
            var bytes = JSON.writeValueAsString(ev).getBytes(StandardCharsets.UTF_8);
            natsOutbound.publish(NatsSubjects.MSG_TYPING, bytes);
        } catch (Exception e) {
            log.debug("typing publish failed: {}", e.getMessage());
        }
    }

    private void invalidateChatMutationForMembers(UUID ownerId, List<String> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            ReadCacheCoordinator.invalidateAfterChatMutation(readCachePort, ownerId);
            return;
        }
        var userIds = new java.util.ArrayList<UUID>();
        userIds.add(ownerId);
        for (var mid : memberIds) {
            userIds.add(UUID.fromString(mid));
        }
        ReadCacheCoordinator.invalidateAfterChatMutation(readCachePort, userIds.toArray(new UUID[0]));
    }
}
