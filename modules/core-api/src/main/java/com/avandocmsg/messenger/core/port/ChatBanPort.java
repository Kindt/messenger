package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.api.chats.bans.dto.ChatBanResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Chat-level ban list ({@code chat_bans}). */
public interface ChatBanPort {
    ChatBanResponse ban(UUID chatId, UUID userId, UUID bannedBy, String reason);

    Optional<ChatBanResponse> findById(UUID id);

    List<ChatBanResponse> findByChatId(UUID chatId);

    boolean unban(UUID chatId, UUID userId);

    boolean isBanned(UUID chatId, UUID userId);
}
