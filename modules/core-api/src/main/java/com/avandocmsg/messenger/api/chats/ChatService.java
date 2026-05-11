package com.avandocmsg.messenger.api.chats;

import com.avandocmsg.messenger.api.chats.dto.ChatMemberResponse;
import com.avandocmsg.messenger.api.chats.dto.ChatResponse;
import com.avandocmsg.messenger.api.repository.BlockRepository;
import com.avandocmsg.messenger.api.repository.ChatReadRepository;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.common.dto.TypingEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
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

    private final ChatRepository chatRepository;
    private final BlockRepository blockRepository;
    private final ChatReadRepository chatReadRepository;
    private final MessageRepository messageRepository;
    private final NatsOutboundPort natsOutbound;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;

    public ChatService(ChatRepository chatRepository, BlockRepository blockRepository,
                       ChatReadRepository chatReadRepository, MessageRepository messageRepository,
                       NatsOutboundPort natsOutbound, Clock clock, UuidGenerator uuidGenerator) {
        this.chatRepository = chatRepository;
        this.blockRepository = blockRepository;
        this.chatReadRepository = chatReadRepository;
        this.messageRepository = messageRepository;
        this.natsOutbound = natsOutbound;
        this.clock = clock;
        this.uuidGenerator = uuidGenerator;
    }

    public ChatResponse createGroup(String title, UUID ownerId, List<String> memberIds) {
        if (title == null || title.isBlank()) {
            return null;
        }
        var chatId = uuidGenerator.randomUuid();
        var chat = chatRepository.createGroup(chatId, title, ownerId);
        if (chat == null) {
            return null;
        }
        if (memberIds != null) {
            for (var mid : memberIds) {
                var memberUuid = UUID.fromString(mid);
                if (!memberUuid.equals(ownerId)) {
                    chatRepository.addMember(chatId, memberUuid, "member");
                }
            }
        }
        return chatRepository.findById(chatId, ownerId).orElse(null);
    }

    public ChatResponse findOrCreateP2P(UUID user1Id, UUID user2Id) {
        if (user1Id.equals(user2Id)) {
            return null;
        }
        var existing = chatRepository.findP2PChat(user1Id, user2Id);
        if (existing.isPresent()) {
            return chatRepository.findById(existing.get(), user1Id).orElse(null);
        }
        if (blockRepository.exists(user1Id, user2Id) || blockRepository.exists(user2Id, user1Id)) {
            return null;
        }
        var chatId = uuidGenerator.randomUuid();
        return chatRepository.createP2P(chatId, user1Id, user2Id);
    }

    public List<ChatResponse> list(UUID userId) {
        return chatRepository.listByUser(userId);
    }

    public ChatResponse getById(UUID chatId, UUID userId) {
        return chatRepository.findById(chatId, userId).orElse(null);
    }

    public boolean updateTitle(UUID chatId, UUID userId, String title) {
        var role = chatRepository.getMemberRole(chatId, userId);
        if (role == null || (!role.equals("owner") && !role.equals("admin"))) {
            log.warn("User {} not authorized to update chat {}", userId, chatId);
            return false;
        }
        return chatRepository.updateTitle(chatId, title);
    }

    public boolean setMuted(UUID chatId, UUID userId, boolean muted) {
        return chatRepository.setMuted(chatId, userId, muted);
    }

    public boolean setPersonalFilter(UUID chatId, UUID userId, boolean active) {
        return chatRepository.setPersonalFilterActive(chatId, userId, active);
    }

    public boolean addMember(UUID chatId, UUID actorId, UUID targetUserId) {
        var role = chatRepository.getMemberRole(chatId, actorId);
        if (role == null || (!role.equals("owner") && !role.equals("admin"))) {
            return false;
        }
        if (blockRepository.exists(targetUserId, actorId)) {
            return false;
        }
        return chatRepository.addMember(chatId, targetUserId, "member");
    }

    public boolean removeMember(UUID chatId, UUID actorId, UUID targetUserId) {
        var actorRole = chatRepository.getMemberRole(chatId, actorId);
        var targetRole = chatRepository.getMemberRole(chatId, targetUserId);
        if (actorRole == null || targetRole == null) {
            return false;
        }
        if (actorRole.equals("owner")) {
            return chatRepository.removeMember(chatId, targetUserId);
        }
        if (actorRole.equals("admin") && !targetRole.equals("owner")) {
            return chatRepository.removeMember(chatId, targetUserId);
        }
        return false;
    }

    public boolean setRole(UUID chatId, UUID actorId, UUID targetUserId, String newRole) {
        var actorRole = chatRepository.getMemberRole(chatId, actorId);
        var targetRole = chatRepository.getMemberRole(chatId, targetUserId);
        if (actorRole == null || targetRole == null) {
            return false;
        }
        if (!actorRole.equals("owner")) {
            return false;
        }
        if (!newRole.equals("admin") && !newRole.equals("member")) {
            return false;
        }
        return chatRepository.setRole(chatId, targetUserId, newRole);
    }

    /**
     * Members list is visible only to users who are members of the chat (including banned members with a row in {@code chat_members}).
     */
    public Optional<List<ChatMemberResponse>> listMembersForViewer(UUID chatId, UUID viewerId) {
        if (chatRepository.getMemberRole(chatId, viewerId) == null) {
            return Optional.empty();
        }
        return Optional.of(chatRepository.listMembers(chatId));
    }

    public boolean markRead(UUID chatId, UUID userId, UUID upToMessageId) {
        if (chatRepository.getMemberRole(chatId, userId) == null) {
            return false;
        }
        UUID markId = upToMessageId;
        if (markId == null) {
            markId = messageRepository.findLatestMessageId(chatId).orElse(null);
        }
        if (markId == null) {
            return true;
        }
        var msg = messageRepository.findById(markId).orElse(null);
        if (msg == null || !msg.chatId().equals(chatId.toString())) {
            return false;
        }
        return chatReadRepository.upsertLastRead(userId, chatId, markId);
    }

    public int unreadCount(UUID chatId, UUID userId) {
        if (chatRepository.getMemberRole(chatId, userId) == null) {
            return -1;
        }
        return chatReadRepository.countUnreadFromOthers(userId, chatId);
    }

    public void publishTyping(UUID chatId, UUID userId) {
        if (chatRepository.getMemberRole(chatId, userId) == null) {
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
}
