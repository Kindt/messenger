package com.avandocmsg.messenger.api.search;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;

import java.util.List;
import java.util.UUID;

public interface MessageSearchBackend {

    String profileId();

    boolean enabled();

    List<MessageResponse> search(UUID userId, List<UUID> chatIds, String query, int limit) throws Exception;
}
