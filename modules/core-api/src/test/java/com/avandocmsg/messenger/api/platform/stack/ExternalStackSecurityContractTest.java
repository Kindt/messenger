package com.avandocmsg.messenger.api.platform.stack;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalStackSecurityContractTest {

    @Test
    void idpContractRequiresClaimsAndFailsClosed() {
        var contract = ExternalStackComponentContracts.contractFor("idp");

        assertEquals("fail_closed", contract.failurePolicy());
        assertTrue(contract.requiredChecks().contains("issuer_jwks_tls"));
        assertTrue(contract.requiredChecks().contains("token_signature_audience_issuer"));
        assertTrue(contract.requiredChecks().contains("required_claims_user_org_roles"));
    }

    @Test
    void idpRejectsFailOpenPolicy() {
        var result = ExternalStackPolicyValidator.validateFailurePolicy("idp", "fail_open");

        assertFalse(result.passed());
        assertTrue(result.failures().contains("component idp does not allow fail_open"));
    }

    @Test
    void cacheContractSeparatesReadCacheAndRateLimitPolicies() {
        var contract = ExternalStackComponentContracts.contractFor("cache");

        assertEquals("read_cache_fail_open_rate_limit_policy", contract.failurePolicy());
        assertTrue(contract.requiredChecks().contains("command_subset_get_set_del_expire_counters_ttl"));
        assertTrue(contract.requiredChecks().contains("key_prefix_isolation"));
        assertTrue(ExternalStackPolicyValidator.validateFailurePolicy("cache:read-cache", "fail_open").passed());
        assertTrue(ExternalStackPolicyValidator.validateFailurePolicy("cache:rate-limit", "fail_closed").passed());
    }

    @Test
    void webEdgeContractRequiresRoutingWebSocketTlsAndHeaders() {
        var contract = ExternalStackComponentContracts.contractFor("web-edge");

        assertEquals("app_or_realtime_degraded", contract.failurePolicy());
        assertTrue(contract.requiredChecks().contains("health_routing"));
        assertTrue(contract.requiredChecks().contains("websocket_upgrade"));
        assertTrue(contract.requiredChecks().contains("forwarded_headers"));
        assertTrue(contract.requiredChecks().contains("tls_chain"));
        assertTrue(contract.requiredChecks().contains("security_headers"));
    }

    @Test
    void angieCandidatesAreVisibleButNotSupported() {
        var profile = new ConnectorProfile(
            "angie-adc",
            "reverse-proxy",
            "enterprise-adc",
            LifecycleStatus.candidate,
            List.of(DeploymentMode.rf_candidate),
            List.of("health_routing", "websocket_upgrade"),
            "web-edge",
            SupportBoundary.externalByo("vendor"),
            null
        );

        var status = new ExternalStackStatusService().profileStatus(List.of(profile));

        assertFalse(status.profiles().get("angie-adc").supported());
        assertEquals("candidate", status.profiles().get("angie-adc").lifecycleStatus());
    }
}
