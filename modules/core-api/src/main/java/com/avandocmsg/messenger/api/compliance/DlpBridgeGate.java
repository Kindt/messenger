package com.avandocmsg.messenger.api.compliance;

import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.api.plugins.PluginPlatformService;
import com.avandocmsg.messenger.api.plugins.PluginRepository;
import com.avandocmsg.messenger.common.plugin.PluginEvent;
import com.avandocmsg.messenger.core.port.UserLookupPort;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Optional L2 DLP bridge gate on message send (ADR dlp-compliance-vs-bridge). */
public final class DlpBridgeGate {
    private static final String DLP_PRESET = "dlp-mock";

    private final PluginRepository pluginRepository;
    private final PluginPlatformService pluginPlatformService;
    private final UserLookupPort userLookupPort;

    public DlpBridgeGate(
        PluginRepository pluginRepository,
        PluginPlatformService pluginPlatformService,
        UserLookupPort userLookupPort
    ) {
        this.pluginRepository = pluginRepository;
        this.pluginPlatformService = pluginPlatformService;
        this.userLookupPort = userLookupPort;
    }

    public Optional<String> blockReason(UUID senderId, UUID chatId, SendMessageRequest request) {
        if (request == null || request.content() == null) {
            return Optional.empty();
        }
        var profile = userLookupPort.findById(senderId).orElse(null);
        if (profile == null || profile.orgId() == null || profile.orgId().isBlank()) {
            return Optional.empty();
        }
        var orgId = UUID.fromString(profile.orgId());
        var instance = pluginRepository.listInstances(orgId).stream()
            .filter(i -> i.enabled() && DLP_PRESET.equals(i.presetId()))
            .findFirst();
        if (instance.isEmpty()) {
            return Optional.empty();
        }
        var row = instance.get();
        var event = new PluginEvent(
            UUID.randomUUID().toString(),
            row.id(),
            row.pluginClass(),
            "message.send",
            senderId,
            chatId,
            request.content(),
            Map.of("message_type", request.type() != null ? request.type() : "text"),
            null);
        var result = pluginPlatformService.invoke(row.id(), event);
        if (result.outcome() != PluginPlatformService.InvokeOutcome.SUCCESS || result.response() == null) {
            return Optional.empty();
        }
        var verdict = result.response().dlpVerdict();
        if (verdict == null || verdict.isBlank()) {
            return Optional.empty();
        }
        return switch (verdict.trim().toLowerCase()) {
            case "block" -> Optional.of("error.message.dlp_blocked");
            case "quarantine" -> Optional.of("error.message.dlp_quarantine");
            default -> Optional.empty();
        };
    }
}
