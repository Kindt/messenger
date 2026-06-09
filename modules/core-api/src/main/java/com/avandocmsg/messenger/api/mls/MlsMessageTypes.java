package com.avandocmsg.messenger.api.mls;

/** Canonical E2EE message types and scheme identifiers (RFC 9420 phase-1 wire). */
public final class MlsMessageTypes {

    public static final String TYPE_WELCOME = "e2ee-mls-welcome";
    public static final String TYPE_COMMIT = "e2ee-mls-commit";
    public static final String SCHEME_MLS = "mls";
    public static final String SCHEME_LEGACY = "legacy";

    private MlsMessageTypes() {
    }
}
