package com.avandocmsg.messenger.api.plugins;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.core.application.MessageApplicationService;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

public class PluginOutboundService {

    private final PluginRepository repository;
    private final MessageApplicationService messageApplicationService;
    private final UserMessageSource messages;

    public PluginOutboundService(
        PluginRepository repository,
        MessageApplicationService messageApplicationService,
        UserMessageSource messages
    ) {
        this.repository = repository;
        this.messageApplicationService = messageApplicationService;
        this.messages = messages;
    }

    public enum Outcome { DELIVERED, NOT_FOUND, UNAUTHORIZED, MISCONFIGURED, SEND_FAILED }

    public record DeliverResult(Outcome outcome, MessageResponse message, String errorKey) {}

    public record OutboundRequest(String text, String format) {}

    public DeliverResult deliver(UUID instanceId, String token, OutboundRequest request) {
        var instance = repository.findInstance(instanceId);
        if (instance.isEmpty()) {
            return new DeliverResult(Outcome.NOT_FOUND, null, "error.plugin.instance_not_found");
        }
        var row = instance.get();
        if (!row.enabled()) {
            return new DeliverResult(Outcome.MISCONFIGURED, null, "error.plugin.instance_disabled");
        }
        if (!verifyToken(token, row.outboundTokenHash())) {
            return new DeliverResult(Outcome.UNAUTHORIZED, null, "error.plugin.outbound_unauthorized");
        }
        if (row.outboundTargetChatId() == null || row.outboundActorUserId() == null) {
            return new DeliverResult(Outcome.MISCONFIGURED, null, "error.plugin.outbound_not_configured");
        }
        if (request == null || request.text() == null || request.text().isBlank()) {
            return new DeliverResult(Outcome.MISCONFIGURED, null, "error.plugin.outbound_empty");
        }
        try {
            var send = new SendMessageRequest(
                "text",
                request.text(),
                null,
                "plugin-outbound-" + UUID.randomUUID(),
                null,
                null,
                null
            );
            var msg = messageApplicationService.sendMessage(
                row.outboundTargetChatId(),
                row.outboundActorUserId(),
                send,
                null
            );
            return new DeliverResult(Outcome.DELIVERED, msg, null);
        } catch (Exception e) {
            return new DeliverResult(Outcome.SEND_FAILED, null, "error.plugin.outbound_send_failed");
        }
    }

    public String localizedError(String key) {
        return messages.get(key);
    }

    static String hashToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = digest.digest(token.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean verifyToken(String token, String expectedHash) {
        if (expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        return expectedHash.equals(hashToken(token));
    }
}
