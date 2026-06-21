package com.avandocmsg.messenger.api.search;

import java.util.List;

public record SearchBackendCapability(
    String profileId,
    String lifecycleStatus,
    boolean productionEnabled,
    List<String> requiredChecks
) {
    public SearchBackendCapability {
        requiredChecks = requiredChecks == null ? List.of() : List.copyOf(requiredChecks);
    }
}
