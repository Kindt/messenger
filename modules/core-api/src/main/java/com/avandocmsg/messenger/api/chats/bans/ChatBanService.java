package com.avandocmsg.messenger.api.chats.bans;

import com.avandocmsg.messenger.api.chats.bans.dto.ChatBanResponse;
import com.avandocmsg.messenger.core.port.ChatBanPort;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ChatBanService {
    private static final Logger log = LoggerFactory.getLogger(ChatBanService.class);
    private static final String ROLE_OWNER = "owner";
    private static final String ROLE_ADMIN = "admin";

    private final ChatBanPort chatBanPort;
    private final ChatPersistencePort chatPersistencePort;

    public ChatBanService(ChatBanPort chatBanPort, ChatPersistencePort chatPersistencePort) {
        this.chatBanPort = chatBanPort;
        this.chatPersistencePort = chatPersistencePort;
    }

    public ChatBanResponse banUser(UUID chatId, UUID actorId, UUID targetUserId, String reason) {
        if (actorId.equals(targetUserId)) {
            log.warn("User {} attempted to ban themselves from chat {}", actorId, chatId);
            return null;
        }
        var actorRole = chatPersistencePort.getMemberRole(chatId, actorId);
        if (actorRole == null || (!actorRole.equals(ROLE_OWNER) && !actorRole.equals(ROLE_ADMIN))) {
            log.warn("User {} not authorized to ban in chat {}", actorId, chatId);
            return null;
        }
        var targetRole = chatPersistencePort.getMemberRole(chatId, targetUserId);
        if (targetRole == null) {
            log.warn("User {} is not a member of chat {}", targetUserId, chatId);
            return null;
        }
        if (targetRole.equals(ROLE_OWNER)) {
            log.warn("Cannot ban chat owner {} from chat {}", targetUserId, chatId);
            return null;
        }
        var ban = chatBanPort.ban(chatId, targetUserId, actorId, reason);
        if (ban != null) {
            chatPersistencePort.setBanned(chatId, targetUserId, true);
        }
        return ban;
    }

    public boolean unbanUser(UUID chatId, UUID actorId, UUID targetUserId) {
        var actorRole = chatPersistencePort.getMemberRole(chatId, actorId);
        if (actorRole == null || (!actorRole.equals(ROLE_OWNER) && !actorRole.equals(ROLE_ADMIN))) {
            log.warn("User {} not authorized to unban in chat {}", actorId, chatId);
            return false;
        }
        var ok = chatBanPort.unban(chatId, targetUserId);
        if (ok) {
            chatPersistencePort.setBanned(chatId, targetUserId, false);
        }
        return ok;
    }

    /** Same privilege as ban/unban: owner or admin of the chat. */
    public Optional<List<ChatBanResponse>> listBansForViewer(UUID chatId, UUID viewerId) {
        var role = chatPersistencePort.getMemberRole(chatId, viewerId);
        if (role == null || (!role.equals(ROLE_OWNER) && !role.equals(ROLE_ADMIN))) {
            return Optional.empty();
        }
        return Optional.of(chatBanPort.findByChatId(chatId));
    }

    public boolean isBanned(UUID chatId, UUID userId) {
        return chatBanPort.isBanned(chatId, userId);
    }
}
