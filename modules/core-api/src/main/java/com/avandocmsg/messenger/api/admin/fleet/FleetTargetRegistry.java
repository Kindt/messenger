package com.avandocmsg.messenger.api.admin.fleet;

import com.avandocmsg.messenger.common.json.MessengerJson;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses {@code FLEET_TARGETS_JSON} allowlist. Rejects non-http(s) schemes and malformed URLs.
 */
public final class FleetTargetRegistry {

    private static final Logger log = LoggerFactory.getLogger(FleetTargetRegistry.class);
    private static final ObjectMapper MAPPER = MessengerJson.mapper();

    private final List<FleetTarget> targets;

    private FleetTargetRegistry(List<FleetTarget> targets) {
        this.targets = List.copyOf(targets);
    }

    public List<FleetTarget> targets() {
        return targets;
    }

    public static FleetTargetRegistry fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new FleetTargetRegistry(List.of());
        }
        try {
            var raw = MAPPER.readValue(json.trim(), new TypeReference<List<FleetTarget>>() {});
            var validated = new ArrayList<FleetTarget>();
            for (var t : raw) {
                if (t != null && t.isEnabled() && isAllowedUrl(t.baseUrl())) {
                    validated.add(t);
                } else if (t != null && t.isEnabled()) {
                    log.warn("Fleet target {} skipped: disallowed base_url {}", t.id(), t.baseUrl());
                }
            }
            return new FleetTargetRegistry(validated);
        } catch (Exception e) {
            log.warn("Invalid FLEET_TARGETS_JSON: {}", e.getMessage());
            return new FleetTargetRegistry(List.of());
        }
    }

    private static boolean isAllowedUrl(String baseUrl) {
        try {
            var uri = URI.create(baseUrl.trim());
            var scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (Exception e) {
            return false;
        }
    }
}
