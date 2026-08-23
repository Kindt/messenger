package com.avandocmsg.messenger.api.security;

import com.avandocmsg.messenger.api.config.AppConfig;
import jakarta.ws.rs.core.Response;
import java.util.function.Supplier;

/** Shared timing normalization for audit-timing.ps1 probes (spec 014 / FSTEC ИБ). */
public final class TimingSensitivePaths {

    private TimingSensitivePaths() {
    }

    public static Response respond(AppConfig config, Supplier<Response> action) {
        long minNs = config.timingNormalizationMinNanos();
        if (minNs > 0) {
            return TimingNormalization.runWithMinimumDuration(minNs, action);
        }
        return action.get();
    }

    public static void padNotFound(AppConfig config) {
        TimingNormalization.padNotFoundExtra(config.timingNotFoundExtraNanos());
    }

    /** Extra pad on failed login (Keycloak user-exists vs unknown-user latency gap). */
    public static void padAuthFailure(AppConfig config) {
        padNotFound(config);
        if (config.timingNormalizationMinNanos() > 0) {
            TimingNormalization.padNotFoundExtra(config.timingAuthFailureExtraNanos());
        }
    }
}
