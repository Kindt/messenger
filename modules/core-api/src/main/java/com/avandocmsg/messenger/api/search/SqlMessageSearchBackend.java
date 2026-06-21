package com.avandocmsg.messenger.api.search;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageQueryPort;

import java.util.List;
import java.util.UUID;

public final class SqlMessageSearchBackend implements MessageSearchBackend {

    private final MessageQueryPort messageQueryPort;

    public SqlMessageSearchBackend(MessageQueryPort messageQueryPort) {
        this.messageQueryPort = messageQueryPort;
    }

    @Override
    public String profileId() {
        return "sql-search";
    }

    @Override
    public boolean enabled() {
        return messageQueryPort != null;
    }

    @Override
    public List<MessageResponse> search(UUID userId, List<UUID> chatIds, String query, int limit) {
        return messageQueryPort.searchPlaintextForUser(UserId.of(userId), chatIds, query, limit);
    }
}
