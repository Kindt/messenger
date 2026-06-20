package com.avandocmsg.messenger.api.platform;

public enum PlatformModuleReason {
    install,
    admin_override,
    incident,
    health_stale,
    secrets_missing,
    eol,
    core_unavailable
}
