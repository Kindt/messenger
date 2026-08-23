package com.avandocmsg.messenger.desktop.sdk.capabilities;

import com.avandocmsg.messenger.desktop.sdk.model.CapabilitiesResponse;
import java.util.Map;
import java.util.Set;

/** Mirrors web-client capability gating (hide when addon off). */
public final class CapabilityGate {

    public enum Feature {
        SEARCH("addon-search"),
        PUSH("addon-engage"),
        LIVE_CALLS("base"),
        PRODUCTIVITY("addon-productivity"),
        INTEGRATIONS("addon-integrations"),
        COLLABORATION("addon-collaboration"),
        E2EE("addon-e2ee"),
        ENTERPRISE_AUTH("addon-enterprise-auth");

        private final String addonId;

        Feature(String addonId) {
            this.addonId = addonId;
        }
    }

    private final CapabilitiesResponse caps;

    public CapabilityGate(CapabilitiesResponse caps) {
        this.caps = caps == null ? new CapabilitiesResponse() : caps;
    }

    public boolean isEnabled(Feature feature) {
        if ("base".equals(feature.addonId)) {
            return true;
        }
        Map<String, ?> addons = caps.addons();
        if (addons == null || addons.isEmpty()) {
            return false;
        }
        var entry = addons.get(feature.addonId);
        if (entry instanceof com.avandocmsg.messenger.desktop.sdk.model.AddonCapability ac) {
            return ac.enabled();
        }
        if (entry instanceof Map<?, ?> map) {
            Object v = map.get("enabled");
            return Boolean.TRUE.equals(v);
        }
        return false;
    }

    public Set<String> capabilityKeys() {
        return caps.capabilities() == null ? Set.of() : Set.copyOf(caps.capabilities());
    }
}
