package com.avandocmsg.messenger.api.platform.stack;

import java.util.List;
import java.util.Map;

public record ExternalStackProbeResult(
    boolean healthy,
    String degradedReason,
    List<String> warnings,
    Map<String, String> metadata
) {
    public ExternalStackProbeResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ExternalStackProbeResult ok() {
        return new ExternalStackProbeResult(true, null, List.of(), Map.of());
    }

    public static ExternalStackProbeResult ok(Map<String, String> metadata, String... warnings) {
        return new ExternalStackProbeResult(true, null, List.of(warnings), metadata);
    }

    public static ExternalStackProbeResult degraded(String reason, String... warnings) {
        return new ExternalStackProbeResult(false, reason, List.of(warnings), Map.of());
    }
}
