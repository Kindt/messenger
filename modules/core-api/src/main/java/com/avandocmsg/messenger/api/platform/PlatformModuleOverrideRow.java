package com.avandocmsg.messenger.api.platform;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record PlatformModuleOverrideRow(
    String moduleId,
    boolean disabled,
    String overrideReason,
    boolean forceEnabled,
    Instant updatedAt,
    UUID updatedBy
) {
    public Optional<PlatformModuleReason> reasonEnum() {
        if (overrideReason == null || overrideReason.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(PlatformModuleReason.fromCode(overrideReason));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
