package com.avandocmsg.messenger.api.plugins;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginIntegrationsComposeServiceTest {

    private final PluginIntegrationsComposeService service = new PluginIntegrationsComposeService();

    @Test
    void plan_returnsQemuGuestComposeCommand() {
        var plan = service.plan("up", List.of("connector-runtime"));
        assertTrue(plan.recommendedCommand().contains("qemu-guest-compose.ps1"));
        assertTrue(plan.recommendedCommand().contains("connector-runtime"));
        assertTrue(plan.recommendedCommand().contains("-Action up"));
    }

    @Test
    void plan_rejectsUnknownService() {
        try {
            service.plan("up", List.of("evil-bridge"));
            org.junit.jupiter.api.Assertions.fail("expected exception");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("evil-bridge"));
        }
    }
}
