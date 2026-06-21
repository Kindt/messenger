package com.avandocmsg.messenger.api.search;

public record SearchBackendBinding(
    MessageSearchBackend primary,
    MessageSearchBackend fallback
) {
    public boolean primaryEnabled() {
        return primary != null && primary.enabled();
    }

    public boolean fallbackEnabled() {
        return fallback != null && fallback.enabled();
    }
}
