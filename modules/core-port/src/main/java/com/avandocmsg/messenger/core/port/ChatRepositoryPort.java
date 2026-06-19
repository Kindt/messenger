package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.core.domain.Chat;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.UserId;

import java.util.List;
import java.util.Optional;

/** Outbound persistence port for {@link Chat} (Phase 2a). */
public interface ChatRepositoryPort {
    Optional<Chat> findById(ChatId id);

    boolean isMember(ChatId chatId, UserId userId);

  /** {@code chat_members.role} or empty when not a member. */
    Optional<String> memberRole(ChatId chatId, UserId userId);

    boolean isMemberBanned(ChatId chatId, UserId userId);

    /** Other participant in a P2P chat; empty when not P2P or solo. */
    Optional<UserId> findOtherP2pMember(ChatId chatId, UserId userId);

    /** All member user ids for unread-cache invalidation and fan-out. */
    List<UserId> listMemberUserIds(ChatId chatId);
}
