package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SupportBoundary(
    @JsonProperty("deployment_owner") String deploymentOwner,
    @JsonProperty("backup_owner") String backupOwner,
    @JsonProperty("ha_owner") String haOwner,
    @JsonProperty("upgrade_owner") String upgradeOwner,
    @JsonProperty("incident_owner") String incidentOwner,
    @JsonProperty("vendor_support_required") boolean vendorSupportRequired,
    @JsonProperty("korus_support_scope") String korusSupportScope
) {
    public static SupportBoundary bundled(String owner) {
        return new SupportBoundary(owner, owner, owner, owner, owner, false, "bundled");
    }

    public static SupportBoundary externalByo(String owner) {
        return new SupportBoundary(owner, owner, owner, owner, owner, true, "connector-validation");
    }
}
