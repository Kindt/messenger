package com.avandocmsg.messenger.api.compliance;

import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.api.plugins.PluginPlatformService;
import com.avandocmsg.messenger.api.plugins.PluginRepository;
import com.avandocmsg.messenger.api.users.dto.UserProfile;
import com.avandocmsg.messenger.common.plugin.PluginResponse;
import com.avandocmsg.messenger.core.port.UserLookupPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DlpBridgeGateTest {

    @Test
    void blockVerdictReturnsI18nKey() {
        var orgId = UUID.randomUUID();
        var senderId = UUID.randomUUID();
        var chatId = UUID.randomUUID();
        var instanceId = UUID.randomUUID();

        var pluginRepository = mock(PluginRepository.class);
        var pluginPlatformService = mock(PluginPlatformService.class);
        var userLookupPort = mock(UserLookupPort.class);

        when(userLookupPort.findById(senderId)).thenReturn(Optional.of(profile(senderId, orgId)));
        when(pluginRepository.listInstances(orgId)).thenReturn(List.of(instanceRow(instanceId, orgId)));
        when(pluginPlatformService.invoke(eq(instanceId), any())).thenReturn(
            new PluginPlatformService.InvokeResult(
                PluginPlatformService.InvokeOutcome.SUCCESS,
                new PluginResponse(null, null, null, "block"),
                null));

        var gate = new DlpBridgeGate(pluginRepository, pluginPlatformService, userLookupPort);
        var reason = gate.blockReason(senderId, chatId, new SendMessageRequest("text", "password leak", null, null, null, null, null, "legacy", null));

        assertTrue(reason.isPresent());
        assertEquals("error.message.dlp_blocked", reason.get());
    }

    @Test
    void skipsWhenNoDlpInstance() {
        var orgId = UUID.randomUUID();
        var senderId = UUID.randomUUID();
        var pluginRepository = mock(PluginRepository.class);
        var userLookupPort = mock(UserLookupPort.class);

        when(userLookupPort.findById(senderId)).thenReturn(Optional.of(profile(senderId, orgId)));
        when(pluginRepository.listInstances(orgId)).thenReturn(List.of());

        var gate = new DlpBridgeGate(pluginRepository, mock(PluginPlatformService.class), userLookupPort);
        var reason = gate.blockReason(senderId, UUID.randomUUID(), new SendMessageRequest("text", "hello", null, null, null, null, null, "legacy", null));

        assertTrue(reason.isEmpty());
    }

    private static UserProfile profile(UUID userId, UUID orgId) {
        return new UserProfile(
            userId.toString(), "u", "User", null, null, null, false, null, null, null, orgId.toString(),
            false, null, null, null);
    }

    private static PluginRepository.InstanceRow instanceRow(UUID id, UUID orgId) {
        return new PluginRepository.InstanceRow(
            id, orgId, "dlp-mock", "@dlp", "DLP", true, "L2", "http://127.0.0.1:8098",
            null, null, null, null, null, null);
    }
}
