package com.avandocmsg.messenger.api.search;

public record SearchBackendBinding(
    MessageSearchBackend primary,
    MessageSearchBackend fallback
) {
    public SearchBackendBinding {
        if (primary != null && !primary.describe().productionEnabled()) {
            throw new IllegalArgumentException(
                "Search backend " + primary.profileId() + " cannot be configured as primary"
            );
        }
    }

    public boolean primaryEnabled() {
        return primary != null && primary.enabled();
    }

    public boolean fallbackEnabled() {
        return fallback != null && fallback.enabled();
    }
}
