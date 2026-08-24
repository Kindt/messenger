package com.avandocmsg.messenger.api.security;

import com.avandocmsg.messenger.api.config.AppConfig;
import jakarta.ws.rs.core.Response;
import java.util.function.Supplier;

/** Shared timing normalization for audit-timing.ps1 probes (spec 014 / FSTEC ИБ). */
public final class TimingSensitivePaths {

    private TimingSensitivePaths() {
    }

    public static Response respond(AppConfig config, Supplier<Response> action) {
        return withMinimumDuration(config.timingNormalizationMinNanos(), config, action, false);
    }

    /** Login probe needs higher floor (Keycloak exist-user vs unknown-user gap on QEMU). */
    public static Response respondLogin(AppConfig config, Supplier<Response> action) {
        return withMinimumDuration(config.timingLoginMinNanos(), config, action, true);
    }

    /** GET /users/me vs missing user id (exist fast path vs 404 lookup gap on QEMU). */
    public static Response respondUser(AppConfig config, Supplier<Response> action) {
        return withMinimumDuration(config.timingUserLookupMinNanos(), config, action, true);
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

    private static Response withMinimumDuration(
            long minNs,
            AppConfig config,
            Supplier<Response> action,
            boolean fallbackToRespond) {
        if (minNs > 0) {
            return TimingNormalization.runWithMinimumDuration(minNs, action);
        }
        return fallbackToRespond ? respond(config, action) : action.get();
    }
}
