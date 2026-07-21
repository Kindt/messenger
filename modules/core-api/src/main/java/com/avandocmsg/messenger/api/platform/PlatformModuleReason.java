package com.avandocmsg.messenger.api.platform;

public enum PlatformModuleReason {
    INSTALL("install"),
    NOT_SELECTED("not_selected"),
    INSTALL_REQUESTED("install_requested"),
    MIGRATION_RUNNING("migration_running"),
    MIGRATION_FAILED("migration_failed"),
    SCHEMA_MISSING("schema_missing"),
    SCHEMA_CONTRACT_FAILED("schema_contract_failed"),
    DEPENDENCY_MISSING("dependency_missing"),
    BACKEND_UNAVAILABLE("backend_unavailable"),
    WORKER_UNAVAILABLE("worker_unavailable"),
    ADMIN_OVERRIDE("admin_override"),
    INCIDENT("incident"),
    HEALTH_STALE("health_stale"),
    SECRETS_MISSING("secrets_missing"),
    EOL("eol"),
    CORE_UNAVAILABLE("core_unavailable");

    private final String code;

    PlatformModuleReason(String code) {
        this.code = code;
    }

    /** Wire / DB value (lowercase snake). */
    public String code() {
        return code;
    }

    public static PlatformModuleReason fromCode(String code) {
        for (var value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown PlatformModuleReason: " + code);
    }
}
