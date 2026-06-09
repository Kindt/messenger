package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.UserId;

import java.util.Optional;

/** Port for saved-vault chat id lookup (US3 — «Хранилище»). */
public interface SavedChatPort {
    Optional<ChatId> getSavedChatId(UserId userId);
}
