package com.avandocmsg.messenger.api.platform;

public enum PlatformModuleReason {
    install,
    not_selected,
    install_requested,
    migration_running,
    migration_failed,
    schema_missing,
    schema_contract_failed,
    dependency_missing,
    backend_unavailable,
    worker_unavailable,
    admin_override,
    incident,
    health_stale,
    secrets_missing,
    eol,
    core_unavailable
}
