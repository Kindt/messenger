package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.core.domain.Chat;
import com.avandocmsg.messenger.core.domain.ChatId;

import java.util.Optional;

/** Outbound persistence port for {@link Chat} (Phase 2a — not wired to JDBC yet). */
public interface ChatRepositoryPort {
    Optional<Chat> findById(ChatId id);
}
