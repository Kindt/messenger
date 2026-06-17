package com.avandocmsg.messenger.api.plugins;

import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.plugin.PluginEvent;
import com.avandocmsg.messenger.common.plugin.PluginResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PluginPlatformService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PluginRepository repository;
    private final IntegrationRouterClient routerClient;
    private final PluginPolicyService policyService;
    private final UserMessageSource messages;

    public PluginPlatformService(
        PluginRepository repository,
        IntegrationRouterClient routerClient,
        PluginPolicyService policyService,
        UserMessageSource messages
    ) {
        this.repository = repository;
        this.routerClient = routerClient;
        this.policyService = policyService;
        this.messages = messages;
    }

    public enum InvokeOutcome { SUCCESS, NOT_FOUND, DISABLED, RUNTIME_ERROR, POLICY_DENIED }

    public record InvokeResult(InvokeOutcome outcome, PluginResponse response, String errorKey) {}

    public InvokeResult invoke(UUID instanceId, PluginEvent event) {
        var instance = repository.findInstance(instanceId);
        return invokeRow(instance, event);
    }

    /** Resolve @bot mention in org without scanning all instances (uses uq org+bot_name). */
    public InvokeResult invokeByOrgBot(UUID orgId, String botName, PluginEvent event) {
        var instance = repository.findInstanceByOrgAndBotName(orgId, botName);
        return invokeRow(instance, event);
    }

    private InvokeResult invokeRow(Optional<PluginRepository.InstanceRow> instance, PluginEvent event) {
        if (instance.isEmpty()) {
            return new InvokeResult(InvokeOutcome.NOT_FOUND, null, "error.plugin.instance_not_found");
        }
        var row = instance.get();
        if (!row.enabled()) {
            return new InvokeResult(InvokeOutcome.DISABLED, null, "error.plugin.instance_disabled");
        }
        if (!policyService.isPresetAllowed(row.orgId(), row.presetId())) {
            return new InvokeResult(InvokeOutcome.POLICY_DENIED, null, "error.plugin.preset_not_allowed");
        }
        var enriched = enrichEvent(event, row);
        try {
            if ("L0".equals(row.pluginClass())) {
                return new InvokeResult(InvokeOutcome.SUCCESS, L0MenuHandler.handle(enriched, row.configJson()), null);
            }
            var response = routerClient.forward(enriched, row.runtimeEndpoint());
            return new InvokeResult(InvokeOutcome.SUCCESS, response, null);
        } catch (IntegrationRouterClient.IntegrationRouterException e) {
            return new InvokeResult(InvokeOutcome.RUNTIME_ERROR, null, e.getMessage());
        } catch (Exception e) {
            return new InvokeResult(InvokeOutcome.RUNTIME_ERROR, null, "error.plugin.runtime_error");
        }
    }

    public Optional<PluginRepository.InstanceRow> createL0Instance(
        UUID orgId,
        String botName,
        String displayName,
        ObjectNode config
    ) {
        var id = UUID.randomUUID();
        var row = new PluginRepository.InstanceRow(
            id,
            orgId,
            "l0-faq-menu",
            botName,
            displayName,
            true,
            "L0",
            null,
            config,
            Instant.now(),
            Instant.now(),
            null,
            null,
            null
        );
        if (!repository.insertInstance(row)) {
            return Optional.empty();
        }
        return repository.findInstance(id);
    }

    public String localizedError(String key) {
        return messages.get(key);
    }

    public Optional<PluginRepository.InstanceRow> configureOutbound(
        UUID instanceId,
        UUID targetChatId,
        UUID actorUserId,
        String plainToken
    ) {
        if (plainToken == null || plainToken.isBlank()) {
            return Optional.empty();
        }
        if (!repository.configureOutbound(
            instanceId,
            targetChatId,
            actorUserId,
            PluginOutboundService.hashToken(plainToken)
        )) {
            return Optional.empty();
        }
        return repository.findInstance(instanceId);
    }

    private PluginEvent enrichEvent(PluginEvent event, PluginRepository.InstanceRow row) {
        Map<String, Object> snapshot = new HashMap<>();
        if (row.configJson() != null) {
            snapshot.putAll(MAPPER.convertValue(row.configJson(), Map.class));
        }
        snapshot.put("preset_id", row.presetId());
        policyService.applyPolicyToSnapshot(snapshot, row.orgId());
        return new PluginEvent(
            event.eventId(),
            row.id(),
            row.pluginClass(),
            event.type(),
            event.userId(),
            event.chatId(),
            event.text(),
            event.payload(),
            snapshot
        );
    }
}
