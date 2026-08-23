package com.avandocmsg.messenger.desktop.sdk.secure;

import java.util.Objects;

/** Decorator: audit token lifecycle events. */
public final class AuditingTokenStore implements SecureTokenStore {

    private final SecureTokenStore delegate;
    private final SecurityAuditLog audit;

    public AuditingTokenStore(SecureTokenStore delegate, SecurityAuditLog audit) {
        this.delegate = Objects.requireNonNull(delegate);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public void put(String key, String value) {
        delegate.put(key, value);
        audit.record("token.put", maskKey(key));
    }

    @Override
    public String get(String key) {
        return delegate.get(key);
    }

    @Override
    public void remove(String key) {
        delegate.remove(key);
        audit.record("token.remove", maskKey(key));
    }

    @Override
    public void clear() {
        delegate.clear();
        audit.record("token.clear", "all");
    }

    private static String maskKey(String key) {
        if (key == null || key.length() < 8) {
            return "***";
        }
        return key.substring(0, 4) + "…" + key.substring(key.length() - 3);
    }
}
