package com.avandocmsg.messenger.core.domain;

import java.util.UUID;

/** Public link resolved from token hash. */
public record ResolvedPublicLink(UUID fileId, char linkKind, UUID createdBy, String passwordHash) {}
