package com.avandocmsg.messenger.common.export;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Env parsing for export completeness policy (worker + core-api). */
public final class ExportCompletenessConfig {

    private ExportCompletenessConfig() {
    }

    public static Set<String> requiredFieldsFromEnv(String raw) {
        if (raw == null || raw.isBlank()) {
            return ExportCompleteness.defaultRequiredFields();
        }
        var set = new LinkedHashSet<String>();
        Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> s.toLowerCase(Locale.ROOT))
            .forEach(set::add);
        return Set.copyOf(set);
    }

    public static boolean strictFromEnv(String raw) {
        return raw != null && Boolean.parseBoolean(raw.trim());
    }
}
