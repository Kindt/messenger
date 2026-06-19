package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.api.messages.dto.MessageVersionResponse;
import com.avandocmsg.messenger.api.messages.dto.PinnedMessageResponse;
import com.avandocmsg.messenger.api.messages.dto.ReactionResponse;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read/query surface for message lists, reactions, pins, and shared-file ACL.
 * Implemented by {@link com.avandocmsg.messenger.core.adapter.persistence.JdbcMessageRepositoryAdapter}.
 */
public interface MessageQueryPort {
    List<MessageResponse> findByChatId(UUID chatId, int limit, UUID before, UUID filterUserId, UUID threadId);

    List<MessageVersionResponse> findVersions(UUID msgId);

    List<ReactionResponse> getReactions(UUID messageId);

    List<PinnedMessageResponse> getPinnedMessages(UUID chatId);

    boolean viewerMayAccessFileViaSharedNonE2eeMessage(UUID fileId, UUID viewerId);

    Optional<FileMessageRef> findLatestMessageRefForViewer(UUID fileId, UUID viewerId);

    Optional<MessageId> findLatestMessageId(ChatId chatId);

    List<MessageResponse> searchPlaintextForUser(UserId userId, List<UUID> chatIds, String queryText, int limit);

    List<MessageResponse> loadMessagesForSearchResults(UserId userId, List<String> messageIdsInOrder, int limit);
}
