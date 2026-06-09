package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.core.domain.Message;
import com.avandocmsg.messenger.core.domain.MessageId;

import java.util.Optional;

public interface MessageRepositoryPort {
    Optional<Message> findById(MessageId id);
}
