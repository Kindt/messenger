package com.avandocmsg.messenger.api.search;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;

import java.util.List;
import java.util.UUID;

public final class CandidateMessageSearchBackend implements MessageSearchBackend {

    private final String profileId;

    CandidateMessageSearchBackend(String profileId) {
        this.profileId = profileId;
    }

    @Override
    public String profileId() {
        return profileId;
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public SearchBackendCapability describe() {
        return new SearchBackendCapability(
            profileId,
            "integration_candidate",
            false,
            List.of("query_contract", "acl_filtering", "reindex_cursor_version", "vendor_certification_required")
        );
    }

    @Override
    public SearchBackendStatus status() {
        return new SearchBackendStatus(profileId, "disabled", "candidate backend is not production-enabled");
    }

    @Override
    public List<MessageResponse> search(UUID userId, List<UUID> chatIds, String query, int limit) {
        throw new UnsupportedOperationException(profileId + " is a candidate search backend and is not production-enabled");
    }
}
