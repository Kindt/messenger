package com.avandocmsg.messenger.api.plugins;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Plans integrations-guest compose actions (spec 014 lifecycle scaffold). */
public final class PluginIntegrationsComposeService {

    private static final Set<String> ALLOWED_SERVICES = Set.of(
        "connector-runtime",
        "mock-apis",
        "onec-bridge",
        "exchange-bridge",
        "echo-php",
        "echo-go",
        "bitrix24-crm-bot"
    );

    private static final Set<String> ALLOWED_ACTIONS = Set.of("up", "down", "build");

    public record Plan(String status, String recommendedCommand, String note) {}

    public Plan plan(String action, List<String> services) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action required");
        }
        var act = action.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_ACTIONS.contains(act)) {
            throw new IllegalArgumentException("unsupported action: " + action);
        }
        if (services == null || services.isEmpty()) {
            throw new IllegalArgumentException("services required");
        }
        var normalized = services.stream()
            .filter(s -> s != null && !s.isBlank())
            .map(s -> s.trim().toLowerCase(Locale.ROOT))
            .distinct()
            .toList();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("services required");
        }
        for (var svc : normalized) {
            if (!ALLOWED_SERVICES.contains(svc)) {
                throw new IllegalArgumentException("service not allowlisted: " + svc);
            }
        }
        var joined = normalized.stream().collect(Collectors.joining(","));
        var cmd =
            ".\\scripts\\qemu-guest-compose.ps1 -Guest integrations -Action " + act + " -Services " + joined;
        var note =
            "Run on Windows host while QEMU integrations guest is up. "
                + "Does not execute compose from core-api (scaffold).";
        return new Plan("accepted", cmd, note);
    }
}
