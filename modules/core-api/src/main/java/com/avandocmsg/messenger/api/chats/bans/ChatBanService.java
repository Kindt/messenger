package com.avandocmsg.messenger.api.chats.bans;

import com.avandocmsg.messenger.api.chats.bans.dto.ChatBanResponse;
import com.avandocmsg.messenger.api.repository.ChatBanRepository;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ChatBanService {
    private static final Logger log = LoggerFactory.getLogger(ChatBanService.class);

    private final ChatBanRepository chatBanRepository;
    private final ChatRepository chatRepository;

    public ChatBanService(ChatBanRepository chatBanRepository, ChatRepository chatRepository) {
        this.chatBanRepository = chatBanRepository;
        this.chatRepository = chatRepository;
    }

    public ChatBanResponse banUser(UUID chatId, UUID actorId, UUID targetUserId, String reason) {
        if (actorId.equals(targetUserId)) {
            log.warn("User {} attempted to ban themselves from chat {}", actorId, chatId);
            return null;
        }
        var actorRole = chatRepository.getMemberRole(chatId, actorId);
        if (actorRole == null || (!actorRole.equals("owner") && !actorRole.equals("admin"))) {
            log.warn("User {} not authorized to ban in chat {}", actorId, chatId);
            return null;
        }
        var targetRole = chatRepository.getMemberRole(chatId, targetUserId);
        if (targetRole == null) {
            log.warn("User {} is not a member of chat {}", targetUserId, chatId);
            return null;
        }
        if (targetRole.equals("owner")) {
            log.warn("Cannot ban chat owner {} from chat {}", targetUserId, chatId);
            return null;
        }
        var ban = chatBanRepository.ban(chatId, targetUserId, actorId, reason);
        if (ban != null) {
            chatRepository.setBanned(chatId, targetUserId, true);
        }
        return ban;
    }

    public boolean unbanUser(UUID chatId, UUID actorId, UUID targetUserId) {
        var actorRole = chatRepository.getMemberRole(chatId, actorId);
        if (actorRole == null || (!actorRole.equals("owner") && !actorRole.equals("admin"))) {
            log.warn("User {} not authorized to unban in chat {}", actorId, chatId);
            return false;
        }
        var ok = chatBanRepository.unban(chatId, targetUserId);
        if (ok) {
            chatRepository.setBanned(chatId, targetUserId, false);
        }
        return ok;
    }

    /** Same privilege as ban/unban: owner or admin of the chat. */
    public Optional<List<ChatBanResponse>> listBansForViewer(UUID chatId, UUID viewerId) {
        var role = chatRepository.getMemberRole(chatId, viewerId);
        if (role == null || (!role.equals("owner") && !role.equals("admin"))) {
            return Optional.empty();
        }
        return Optional.of(chatBanRepository.findByChatId(chatId));
    }

    public boolean isBanned(UUID chatId, UUID userId) {
        return chatBanRepository.isBanned(chatId, userId);
    }
}
