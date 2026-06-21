package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record ComponentBackendManifest(
    @JsonProperty("component") String component,
    @JsonProperty("backend_family") String backendFamily,
    @JsonProperty("connector") String connector,
    @JsonProperty("version") String version,
    @JsonProperty("role") ExternalStackRole role,
    @JsonProperty("endpoint") String endpoint,
    @JsonProperty("resource_name_or_alias") String resourceNameOrAlias,
    @JsonProperty("schema_or_protocol_version") String schemaOrProtocolVersion,
    @JsonProperty("compatibility_profile") String compatibilityProfile,
    @JsonProperty("topology") String topology,
    @JsonProperty("config_revision") String configRevision,
    @JsonProperty("capabilities") List<String> capabilities,
    @JsonProperty("data_classification") String dataClassification,
    @JsonProperty("support_boundary") SupportBoundary supportBoundary,
    @JsonProperty("metadata") Map<String, String> metadata
) {
    public ComponentBackendManifest {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public ComponentBackendManifest withRole(ExternalStackRole newRole) {
        return new ComponentBackendManifest(component, backendFamily, connector, version, newRole, endpoint,
            resourceNameOrAlias, schemaOrProtocolVersion, compatibilityProfile, topology, configRevision,
            capabilities, dataClassification, supportBoundary, metadata);
    }

    public ComponentBackendManifest withCompatibilityProfile(String newCompatibilityProfile) {
        return new ComponentBackendManifest(component, backendFamily, connector, version, role, endpoint,
            resourceNameOrAlias, schemaOrProtocolVersion, newCompatibilityProfile, topology, configRevision,
            capabilities, dataClassification, supportBoundary, metadata);
    }

    public ComponentBackendManifest withCapabilities(List<String> newCapabilities) {
        return new ComponentBackendManifest(component, backendFamily, connector, version, role, endpoint,
            resourceNameOrAlias, schemaOrProtocolVersion, compatibilityProfile, topology, configRevision,
            newCapabilities, dataClassification, supportBoundary, metadata);
    }

    public ComponentBackendManifest withResourceNameOrAlias(String newResourceNameOrAlias) {
        return new ComponentBackendManifest(component, backendFamily, connector, version, role, endpoint,
            newResourceNameOrAlias, schemaOrProtocolVersion, compatibilityProfile, topology, configRevision,
            capabilities, dataClassification, supportBoundary, metadata);
    }

    public ComponentBackendManifest withEndpoint(String newEndpoint) {
        return new ComponentBackendManifest(component, backendFamily, connector, version, role, newEndpoint,
            resourceNameOrAlias, schemaOrProtocolVersion, compatibilityProfile, topology, configRevision,
            capabilities, dataClassification, supportBoundary, metadata);
    }

    public ComponentBackendManifest withMetadata(Map<String, String> newMetadata) {
        return new ComponentBackendManifest(component, backendFamily, connector, version, role, endpoint,
            resourceNameOrAlias, schemaOrProtocolVersion, compatibilityProfile, topology, configRevision,
            capabilities, dataClassification, supportBoundary, newMetadata);
    }
}
