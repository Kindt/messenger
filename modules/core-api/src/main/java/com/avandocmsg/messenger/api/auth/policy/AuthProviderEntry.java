package com.avandocmsg.messenger.api.auth.policy;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/** One auth provider entry stored in org_auth_policy.providers_json. */
public record AuthProviderEntry(
    @JsonProperty("id") String id,
    @JsonProperty("type") String type,
    @JsonProperty("alias") String alias,
    @JsonProperty("display_name") @JsonAlias("displayName") String displayName,
    @JsonProperty("priority") int priority,
    @JsonProperty("enabled") boolean enabled,
    @JsonProperty("kc_component_id") @JsonAlias("kcComponentId") String kcComponentId,
    @JsonProperty("status") String status,
    @JsonProperty("secret_ref") @JsonAlias("secretRef") String secretRef,
    @JsonProperty("settings") Map<String, String> settings
) {
    public AuthProviderEntry withKcComponentId(String componentId, String newStatus) {
        return new AuthProviderEntry(
            id, type, alias, displayName, priority, enabled, componentId, newStatus, secretRef, settings);
    }

    public AuthProviderEntry withStatus(String newStatus) {
        return new AuthProviderEntry(
            id, type, alias, displayName, priority, enabled, kcComponentId, newStatus, secretRef, settings);
    }
}
