package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.core.domain.Message;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;

import java.util.Optional;

public interface MessageRepositoryPort {
    Optional<Message> findById(MessageId id);

    /** Persists a new message row; empty when insert fails. */
    Optional<Message> insert(MessageInsert command);

    /** Updates message body when editor is the sender; false when row missing or not allowed. */
    boolean updateContent(MessageId id, UserId senderId, String content);

    /** Soft-delete message row. */
    boolean softDelete(MessageId id);

    boolean addReaction(MessageId messageId, UserId userId, String reaction);

    boolean removeReaction(MessageId messageId, UserId userId, String reaction);
}
