package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ConnectorCompatibilityPacks {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private static final Map<String, List<String>> EVIDENCE_BY_COMPONENT = Map.ofEntries(
        Map.entry("relational-db-hot", List.of("h2_or_lab_migration_green")),
        Map.entry("relational-db-archive", List.of("archive_query_smoke_green")),
        Map.entry("object-storage", List.of("multipart_checksum_smoke")),
        Map.entry("messaging", List.of("jetstream_contract_green")),
        Map.entry("idp", List.of("jwks_contract_green")),
        Map.entry("cache", List.of("redis_command_subset_green")),
        Map.entry("web-edge", List.of("security_headers_green")),
        Map.entry("media", List.of("room_join_smoke")),
        Map.entry("turn", List.of("relay_reachability_green")),
        Map.entry("notifications", List.of("vapid_config_green")),
        Map.entry("dlp", List.of("verdict_schema_contract_green")),
        Map.entry("integrations", List.of("webhook_schema_contract_green")),
        Map.entry("bots", List.of("bot_event_schema_contract_green")),
        Map.entry("search", List.of("search_reindex_contract_green"))
    );

    private static final List<ConnectorCompatibilityPack> CATALOG = loadCatalog();

    private static final Map<String, ConnectorCompatibilityPack> BY_ID = CATALOG.stream()
        .collect(Collectors.toUnmodifiableMap(ConnectorCompatibilityPack::profileId, p -> p, (first, ignored) -> first));

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

    public static ExternalStackCatalogHealthReport healthReport() {
        var failures = new ArrayList<String>();
        var components = CATALOG.stream()
            .map(ConnectorCompatibilityPack::component)
            .distinct()
            .toList();
        var contractComponents = ExternalStackComponentContracts.catalog().keySet();
        components.stream()
            .filter(component -> !contractComponents.contains(component))
            .forEach(component -> failures.add("component " + component + " has no validation contract"));
        var candidateCount = (int) CATALOG.stream()
            .filter(pack -> pack.lifecycleStatus() == LifecycleStatus.candidate
                || pack.lifecycleStatus() == LifecycleStatus.integration_candidate)
            .count();
        var warnings = candidateCount > 0
            ? List.of("candidate profiles require explicit promotion before production use")
            : List.<String>of();
        return new ExternalStackCatalogHealthReport(
            failures.isEmpty(),
            components.size(),
            CATALOG.size(),
            candidateCount,
            failures,
            warnings
        );
    }

    public static ExternalStackComponentProfileSummaryCatalog componentSummaries() {
        var summaries = CATALOG.stream()
            .collect(Collectors.groupingBy(
                ConnectorCompatibilityPack::component,
                Collectors.collectingAndThen(Collectors.toList(), ConnectorCompatibilityPacks::componentSummary)
            ));
        return new ExternalStackComponentProfileSummaryCatalog(Map.copyOf(summaries));
    }

    public static ExternalStackComponentProfileSummary componentSummary(String component) {
        var packs = CATALOG.stream()
            .filter(pack -> pack.component().equals(component))
            .toList();
        if (packs.isEmpty()) {
            throw new IllegalArgumentException("No external stack component profiles: " + component);
        }
        return componentSummary(packs);
    }

    private static ExternalStackComponentProfileSummary componentSummary(List<ConnectorCompatibilityPack> packs) {
        var component = packs.getFirst().component();
        var supported = (int) packs.stream().filter(ConnectorCompatibilityPack::supported).count();
        var candidates = (int) packs.stream()
            .filter(pack -> pack.lifecycleStatus() == LifecycleStatus.candidate
                || pack.lifecycleStatus() == LifecycleStatus.integration_candidate)
            .count();
        var rejected = (int) packs.stream()
            .filter(pack -> pack.lifecycleStatus() == LifecycleStatus.rejected)
            .count();
        String warning = null;
        if (candidates > 0) {
            warning = "candidate profiles require explicit promotion";
        } else if (supported == 0) {
            warning = "no production-supported profile";
        }
        return new ExternalStackComponentProfileSummary(
            component,
            packs.size(),
            supported,
            candidates,
            rejected,
            warning
        );
    }

    private static List<ConnectorCompatibilityPack> loadCatalog() {
        var packs = new ArrayList<ConnectorCompatibilityPack>();
        var root = readCatalogRoot();
        var components = root.path("components").fields();
        while (components.hasNext()) {
            var component = components.next();
            var componentId = component.getKey();
            var componentNode = component.getValue();
            var profiles = componentNode.path("profiles").fields();
            while (profiles.hasNext()) {
                var profile = profiles.next();
                packs.add(packFromYaml(componentId, profile.getKey(), profile.getValue(), componentNode));
            }
        }
        var deduped = new ArrayList<>(packs.stream()
            .collect(Collectors.toMap(ConnectorCompatibilityPack::profileId, p -> p, (first, ignored) -> first))
            .values());
        addAliases(deduped, root.path("compatibility_aliases"));
        return List.copyOf(deduped);
    }

    private static ConnectorCompatibilityPack packFromYaml(
        String component,
        String profileId,
        JsonNode profileNode,
        JsonNode componentNode
    ) {
        var lifecycleStatus = LifecycleStatus.valueOf(profileNode.path("lifecycle_status").asText());
        return new ConnectorCompatibilityPack(
            profileId,
            component,
            lifecycleStatus,
            contractChecks(component),
            stringList(profileNode.path("promotion_evidence"), promotionEvidence(component, lifecycleStatus)),
            stringList(profileNode.path("unsupported_modes"), unsupportedModes(lifecycleStatus, componentNode))
        );
    }

    private static void addAliases(List<ConnectorCompatibilityPack> packs, JsonNode aliases) {
        var byId = packs.stream()
            .collect(Collectors.toMap(ConnectorCompatibilityPack::profileId, p -> p, (first, ignored) -> first));
        aliases.fields().forEachRemaining(entry -> {
            var alias = entry.getKey();
            var target = entry.getValue().asText();
            var pack = byId.get(target);
            if (pack != null && !byId.containsKey(alias)) {
                packs.add(new ConnectorCompatibilityPack(
                    alias,
                    pack.component(),
                    pack.lifecycleStatus(),
                    pack.requiredChecks(),
                    pack.promotionEvidence(),
                    pack.unsupportedModes()
                ));
            }
        });
    }

    private static List<String> stringList(JsonNode node, List<String> fallback) {
        if (node == null || !node.isArray()) {
            return fallback;
        }
        var values = new ArrayList<String>();
        node.forEach(item -> values.add(item.asText()));
        return List.copyOf(values);
    }

    private static List<String> contractChecks(String component) {
        if ("search".equals(component)) {
            return List.of("query_contract", "acl_filtering", "reindex_cursor_version", "no_silent_fallback");
        }
        try {
            return ExternalStackComponentContracts.contractFor(component).requiredChecks();
        } catch (IllegalArgumentException ignored) {
            return List.of("profile_contract", "endpoint_auth_tls", "no_silent_fallback");
        }
    }

    private static List<String> promotionEvidence(String component, LifecycleStatus lifecycleStatus) {
        var evidence = new ArrayList<>(EVIDENCE_BY_COMPONENT.getOrDefault(component, List.of("profile_contract_green")));
        if (lifecycleStatus == LifecycleStatus.supported_bundled) {
            evidence.add("korus_bundled_runbook");
        } else if (lifecycleStatus == LifecycleStatus.supported_external_byo) {
            evidence.add("customer_profile_evidence");
        } else {
            evidence.add("vendor_certification_required");
        }
        return List.copyOf(evidence);
    }

    private static List<String> unsupportedModes(LifecycleStatus lifecycleStatus, JsonNode componentNode) {
        if (lifecycleStatus == LifecycleStatus.supported_bundled) {
            return List.of();
        }
        if (lifecycleStatus == LifecycleStatus.supported_external_byo) {
            return List.of("silent_fallback");
        }
        var fallbackAllowed = componentNode.path("degradation").path("fallback_allowed").asText("");
        if ("explicit_degraded_status_only".equals(fallbackAllowed)) {
            return List.of("production_without_reindex_gate", "supported_bundled_claim");
        }
        return List.of("supported_bundled_claim", "production_without_profile_gate");
    }

    private static JsonNode readCatalogRoot() {
        var repoPath = Path.of("docs", "external-stack-profiles.yaml");
        try {
            if (Files.isRegularFile(repoPath)) {
                return YAML_MAPPER.readTree(repoPath.toFile());
            }
            try (InputStream input = ConnectorCompatibilityPacks.class
                .getResourceAsStream("/external-stack-profiles.yaml")) {
                if (input != null) {
                    return YAML_MAPPER.readTree(input);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read external stack profile catalog", e);
        }
        throw new IllegalStateException("external-stack-profiles.yaml not found");
    }
}
