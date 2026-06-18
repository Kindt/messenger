package com.avandocmsg.messenger.api.admin.fleet;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Allowlisted HTTP probe target for fleet snapshot (env {@code FLEET_TARGETS_JSON}).
 */
public record FleetTarget(
    String id,
    String role,
    @JsonProperty("base_url") String baseUrl,
    @JsonProperty("health_path") String healthPath,
    @JsonProperty("enabled") Boolean enabled
) {
    public FleetTarget {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id required");
        }
        if (role == null || role.isBlank()) {
            role = "unknown";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("base_url required for " + id);
        }
    }

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public String healthPathOrDefault() {
        if (healthPath == null || healthPath.isBlank()) {
            return "/health";
        }
        return healthPath.startsWith("/") ? healthPath : "/" + healthPath;
    }

    public String probeUrl() {
        var base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + healthPathOrDefault();
    }
}
