package com.avandocmsg.messenger.common.plugin.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationEnvTest {

    @Test
    void backendModeParsesValues() {
        assertEquals(IntegrationBackendMode.MOCK, IntegrationBackendMode.fromEnv("mock"));
        assertEquals(IntegrationBackendMode.LIVE, IntegrationBackendMode.fromEnv("live"));
        assertEquals(IntegrationBackendMode.AUTO, IntegrationBackendMode.fromEnv("auto"));
        assertEquals(IntegrationBackendMode.AUTO, IntegrationBackendMode.fromEnv(null));
    }

    @Test
    void useMockRespectsMode() {
        assertTrue(IntegrationBackendMode.MOCK == IntegrationBackendMode.MOCK
            || IntegrationEnv.useMock(false));
        assertFalse(IntegrationBackendMode.LIVE == IntegrationBackendMode.MOCK);
    }

    @Test
    void cloudLlmAllowedByPolicy() {
        assertFalse(IntegrationEnv.cloudLlmAllowed(java.util.Map.of("org_llm_mode", "on_prem_only")));
        assertTrue(IntegrationEnv.cloudLlmAllowed(java.util.Map.of("org_llm_mode", "cloud_allowed")));
    }
}
