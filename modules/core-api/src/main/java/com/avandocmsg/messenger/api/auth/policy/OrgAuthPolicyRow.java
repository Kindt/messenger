package com.avandocmsg.messenger.api.auth.policy;


import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrgAuthPolicyRow(
    UUID orgId,
    boolean allowLocalPassword,
    boolean allowSelfRegistration,
    List<AuthProviderEntry> providers,
    String lastApplyStatus,
    String lastApplyError,
    Instant updatedAt,
    UUID updatedBy
) {}
