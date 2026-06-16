package com.avandocmsg.messenger.api.plugins;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginPolicyServiceTest {

    @Test
    void allowAllWhenPresetListEmpty() {
        var repo = new StubRepo();
        var service = new PluginPolicyService(repo);
        assertTrue(service.isPresetAllowed(UUID.randomUUID(), "jira-connector"));
    }

    @Test
    void denyWhenPresetNotInAllowlist() {
        var orgId = UUID.randomUUID();
        var repo = new StubRepo();
        repo.policy = new PluginRepository.OrgPolicyRow(orgId, List.of("echo-sidecar"), "on_prem_only", true, null);
        var service = new PluginPolicyService(repo);
        assertFalse(service.isPresetAllowed(orgId, "jira-connector"));
        assertTrue(service.isPresetAllowed(orgId, "echo-sidecar"));
    }

    static final class StubRepo extends PluginRepository {
        PluginRepository.OrgPolicyRow policy;

        StubRepo() {
            super(null);
        }

        @Override
        public Optional<OrgPolicyRow> findOrgPolicy(UUID orgId) {
            return policy != null && policy.orgId().equals(orgId) ? Optional.of(policy) : Optional.empty();
        }
    }
}
