package com.avandocmsg.messenger.api.search;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;

import java.util.List;
import java.util.UUID;

public interface MessageSearchBackend {

    String profileId();

    boolean enabled();

    default SearchBackendCapability describe() {
        return new SearchBackendCapability(
            profileId(),
            "supported_bundled",
            true,
            List.of("query_contract", "acl_filtering", "no_silent_fallback")
        );
    }

    default SearchBackendStatus status() {
        return new SearchBackendStatus(profileId(), enabled() ? "enabled" : "disabled", null);
    }

    List<MessageResponse> search(UUID userId, List<UUID> chatIds, String query, int limit) throws Exception;
}
