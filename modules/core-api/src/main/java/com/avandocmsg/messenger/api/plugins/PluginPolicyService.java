package com.avandocmsg.messenger.api.plugins;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PluginPolicyService {

    private static final Set<String> LLM_MODES = Set.of("on_prem_only", "cloud_allowed", "hybrid");

    private final PluginRepository repository;

    public PluginPolicyService(PluginRepository repository) {
        this.repository = repository;
    }

    public record PolicyJson(
        UUID orgId,
        List<String> allowedPresetIds,
        String llmMode,
        boolean ocrOnPremOnly
    ) {}

    public record UpdatePolicyRequest(
        List<String> allowedPresetIds,
        String llmMode,
        Boolean ocrOnPremOnly
    ) {}

    public PluginRepository.OrgPolicyRow getOrDefault(UUID orgId) {
        return repository.findOrgPolicy(orgId).orElse(defaultPolicy(orgId));
    }

    public Optional<PluginRepository.OrgPolicyRow> update(UUID orgId, UpdatePolicyRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        var current = getOrDefault(orgId);
        var llmMode = request.llmMode() != null ? request.llmMode().trim() : current.llmMode();
        if (!LLM_MODES.contains(llmMode)) {
            return Optional.empty();
        }
        var allowed = request.allowedPresetIds() != null ? request.allowedPresetIds() : current.allowedPresetIds();
        var ocrOnPrem = request.ocrOnPremOnly() != null ? request.ocrOnPremOnly() : current.ocrOnPremOnly();
        var row = new PluginRepository.OrgPolicyRow(orgId, allowed, llmMode, ocrOnPrem, current.updatedAt());
        if (!repository.upsertOrgPolicy(row)) {
            return Optional.empty();
        }
        return repository.findOrgPolicy(orgId);
    }

    public boolean isPresetAllowed(UUID orgId, String presetId) {
        var policy = getOrDefault(orgId);
        if (policy.allowedPresetIds() == null || policy.allowedPresetIds().isEmpty()) {
            return true;
        }
        return policy.allowedPresetIds().contains(presetId);
    }

    public void applyPolicyToSnapshot(java.util.Map<String, Object> snapshot, UUID orgId) {
        var policy = getOrDefault(orgId);
        snapshot.put("org_llm_mode", policy.llmMode());
        snapshot.put("ocr_on_prem_only", policy.ocrOnPremOnly());
    }

    public static PolicyJson toJson(PluginRepository.OrgPolicyRow row) {
        return new PolicyJson(row.orgId(), row.allowedPresetIds(), row.llmMode(), row.ocrOnPremOnly());
    }

    private static PluginRepository.OrgPolicyRow defaultPolicy(UUID orgId) {
        return new PluginRepository.OrgPolicyRow(orgId, List.of(), "on_prem_only", true, java.time.Instant.EPOCH);
    }
}
