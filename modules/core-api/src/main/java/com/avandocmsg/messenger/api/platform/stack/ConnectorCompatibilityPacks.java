package com.avandocmsg.messenger.api.platform.stack;

import java.util.List;
import java.util.Map;

public final class ConnectorCompatibilityPacks {

    private static final List<ConnectorCompatibilityPack> CATALOG = List.of(
        pack("postgres-16-bundled", "relational-db-hot", LifecycleStatus.supported_bundled,
            "h2_or_lab_migration_green", "korus_bundled_runbook"),
        pack("postgres-16-external", "relational-db-hot", LifecycleStatus.supported_external_byo,
            "h2_or_lab_migration_green", "customer_backup_and_wal_evidence"),
        candidate("postgres-pro-candidate", "relational-db-hot", "vendor_certification_required"),
        candidate("tantor-postgres-candidate", "relational-db-hot", "vendor_certification_required"),
        candidate("arenadata-postgres-candidate", "relational-db-hot", "vendor_certification_required"),
        pack("s3-minio-bundled", "object-storage", LifecycleStatus.supported_bundled,
            "multipart_checksum_smoke", "korus_bundled_runbook"),
        pack("s3-compatible-external", "object-storage", LifecycleStatus.supported_external_byo,
            "multipart_checksum_smoke", "customer_inventory_and_retention_evidence"),
        pack("nats-2.10-bundled", "messaging", LifecycleStatus.supported_bundled,
            "jetstream_contract_green", "korus_bundled_runbook"),
        pack("nats-2.x-external", "messaging", LifecycleStatus.supported_external_byo,
            "jetstream_contract_green", "customer_stream_offset_evidence"),
        pack("keycloak-24-bundled", "idp", LifecycleStatus.supported_bundled,
            "jwks_contract_green", "korus_bundled_runbook"),
        pack("oidc-generic", "idp", LifecycleStatus.supported_external_byo,
            "jwks_contract_green", "claim_mapping_evidence"),
        pack("redis-7-bundled", "cache", LifecycleStatus.supported_bundled,
            "redis_command_subset_green", "korus_bundled_runbook"),
        pack("redis-compatible-external", "cache", LifecycleStatus.supported_external_byo,
            "redis_command_subset_green", "customer_ttl_policy_evidence"),
        pack("web-edge", "web-edge", LifecycleStatus.supported_bundled,
            "security_headers_green", "korus_bundled_runbook"),
        candidate("angie-candidate", "web-edge", "security_headers_green"),
        pack("livekit-bundled", "media", LifecycleStatus.supported_bundled,
            "room_join_smoke", "korus_bundled_runbook"),
        candidate("vks-integration-candidate", "media", "media_integration_spike"),
        pack("dlp-external", "dlp", LifecycleStatus.supported_external_byo,
            "verdict_schema_contract_green", "tenant_policy_evidence"),
        pack("integrations-bundled", "integrations", LifecycleStatus.supported_bundled,
            "webhook_schema_contract_green", "korus_bundled_runbook"),
        candidate("opensearch-candidate", "search", "search_reindex_contract_green"),
        candidate("elasticsearch-candidate", "search", "search_reindex_contract_green")
    );

    private static final Map<String, ConnectorCompatibilityPack> BY_ID = CATALOG.stream()
        .collect(java.util.stream.Collectors.toUnmodifiableMap(ConnectorCompatibilityPack::profileId, p -> p));

    private ConnectorCompatibilityPacks() {
    }

    public static List<ConnectorCompatibilityPack> catalog() {
        return CATALOG;
    }

    public static ConnectorCompatibilityPack packFor(String profileId) {
        var pack = BY_ID.get(profileId);
        if (pack == null) {
            throw new IllegalArgumentException("No connector compatibility pack: " + profileId);
        }
        return pack;
    }

    private static ConnectorCompatibilityPack pack(
        String profileId,
        String component,
        LifecycleStatus lifecycleStatus,
        String... promotionEvidence
    ) {
        var contract = contractChecks(component);
        return new ConnectorCompatibilityPack(
            profileId,
            component,
            lifecycleStatus,
            contract,
            List.of(promotionEvidence),
            lifecycleStatus == LifecycleStatus.supported_bundled ? List.of() : List.of("silent_fallback")
        );
    }

    private static ConnectorCompatibilityPack candidate(
        String profileId,
        String component,
        String... promotionEvidence
    ) {
        return new ConnectorCompatibilityPack(
            profileId,
            component,
            LifecycleStatus.integration_candidate,
            contractChecks(component),
            List.of(promotionEvidence),
            List.of("production_without_reindex_gate", "supported_bundled_claim")
        );
    }

    private static List<String> contractChecks(String component) {
        if ("search".equals(component)) {
            return List.of("query_contract", "acl_filtering", "reindex_cursor_version", "no_silent_fallback");
        }
        return ExternalStackComponentContracts.contractFor(component).requiredChecks();
    }
}
