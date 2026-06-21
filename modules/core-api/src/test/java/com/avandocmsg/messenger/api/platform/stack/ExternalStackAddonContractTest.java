package com.avandocmsg.messenger.api.platform.stack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalStackAddonContractTest {

    @Test
    void mediaContractRequiresLiveKitTokenJoinAndVksGate() {
        var contract = ExternalStackComponentContracts.contractFor("media");

        assertEquals("calls_degraded_to_text_chat", contract.failurePolicy());
        assertTrue(contract.requiredChecks().contains("livekit_token_issue"));
        assertTrue(contract.requiredChecks().contains("room_join"));
        assertTrue(contract.requiredChecks().contains("vks_integration_candidate_gate"));
    }

    @Test
    void turnContractRequiresRealmSecretAndRelayReachability() {
        var contract = ExternalStackComponentContracts.contractFor("turn");

        assertEquals("call_relay_degraded", contract.failurePolicy());
        assertTrue(contract.requiredChecks().contains("realm_secret"));
        assertTrue(contract.requiredChecks().contains("relay_reachability"));
        assertTrue(contract.requiredChecks().contains("udp_tcp_ports"));
    }

    @Test
    void notificationContractIsBestEffortWithExplicitGatewayChecks() {
        var contract = ExternalStackComponentContracts.contractFor("notifications");

        assertEquals("best_effort_degraded", contract.failurePolicy());
        assertTrue(contract.requiredChecks().contains("vapid_config"));
        assertTrue(contract.requiredChecks().contains("gateway_config"));
        assertTrue(ExternalStackPolicyValidator.validateFailurePolicy("notifications", "fail_open").passed());
    }

    @Test
    void dlpContractRequiresTenantPolicyAndE2eeBoundary() {
        var contract = ExternalStackComponentContracts.contractFor("dlp");

        assertEquals("policy_configured_fail_open_or_quarantine", contract.failurePolicy());
        assertTrue(contract.requiredChecks().contains("verdict_schema"));
        assertTrue(contract.requiredChecks().contains("payload_limits"));
        assertTrue(contract.requiredChecks().contains("tenant_policy"));
        assertTrue(contract.requiredChecks().contains("e2ee_plaintext_boundary"));
        assertTrue(ExternalStackPolicyValidator.validateFailurePolicy("dlp", "quarantine").passed());
    }

    @Test
    void integrationsContractRequiresEndpointSchemaRetryAndAuditBoundary() {
        var contract = ExternalStackComponentContracts.contractFor("integrations");

        assertEquals("audit_and_retry_degraded", contract.failurePolicy());
        assertTrue(contract.requiredChecks().contains("webhook_plugin_bot_gateway_endpoint"));
        assertTrue(contract.requiredChecks().contains("event_schema"));
        assertTrue(contract.requiredChecks().contains("retry_timeout"));
        assertTrue(contract.requiredChecks().contains("audit_boundary"));
    }
}
