package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ConnectorProfile(
    @JsonProperty("profile_id") String profileId,
    @JsonProperty("backend_family") String backendFamily,
    @JsonProperty("connector") String connector,
    @JsonProperty("lifecycle_status") LifecycleStatus lifecycleStatus,
    @JsonProperty("deployment_modes") List<DeploymentMode> deploymentModes,
    @JsonProperty("required_capabilities") List<String> requiredCapabilities,
    @JsonProperty("validation_contract") String validationContract,
    @JsonProperty("support_boundary") SupportBoundary supportBoundary,
    @JsonProperty("impact_model") ImpactModel impactModel
) {
    public ConnectorProfile {
        deploymentModes = deploymentModes == null ? List.of() : List.copyOf(deploymentModes);
        requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities);
    }
}
