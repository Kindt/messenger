package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.UserId;

import java.util.Optional;

/** Port for saved-vault chat id lookup and provisioning (US3 — «Хранилище»). */
public interface SavedChatPort {
    Optional<ChatId> getSavedChatId(UserId userId);

    /** Creates saved-vault chat when missing; returns id on success. */
    Optional<ChatId> ensureSavedVaultChat(UserId userId);
}
