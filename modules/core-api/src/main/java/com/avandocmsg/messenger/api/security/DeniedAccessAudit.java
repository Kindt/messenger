package com.avandocmsg.messenger.api.security;

import com.avandocmsg.messenger.api.metrics.ApiDeniedMetrics;
import com.avandocmsg.messenger.core.port.AuditPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.UUID;

/** Metrics + audit sink for authenticated access denials (FSTEC-14). */
public class DeniedAccessAudit {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AuditPort auditPort;

    public DeniedAccessAudit(AuditPort auditPort) {
        this.auditPort = auditPort;
    }

    public void fileAccessDenied(UUID actorUserId, UUID fileId) {
        ApiDeniedMetrics.fileAccessDenied();
        auditPort.record(
            actorUserId,
            "access.file.denied",
            "file",
            fileId.toString(),
            detailsJson(node -> node.put("file_id", fileId.toString()))
        );
    }

    public void messageSendDenied(UUID actorUserId, UUID chatId, String reasonKey) {
        ApiDeniedMetrics.messageSendDenied();
        auditPort.record(
            actorUserId,
            "access.message_send.denied",
            "chat",
            chatId.toString(),
            detailsJson(node -> {
                node.put("chat_id", chatId.toString());
                if (reasonKey != null && !reasonKey.isBlank()) {
                    node.put("reason_key", reasonKey);
                }
            })
        );
    }

    public void ipAllowlistDenied(UUID actorUserId, UUID orgId, String clientIp) {
        ApiDeniedMetrics.ipAllowlistDenied();
        auditPort.record(
            actorUserId,
            "access.ip_allowlist.denied",
            "organization",
            orgId.toString(),
            detailsJson(node -> {
                node.put("org_id", orgId.toString());
                if (clientIp != null && !clientIp.isBlank()) {
                    node.put("client_ip", clientIp);
                }
            })
        );
    }

    public void geoDenied(UUID actorUserId, UUID orgId, String countryCode) {
        ApiDeniedMetrics.geoDenied();
        auditPort.record(
            actorUserId,
            "access.geo.denied",
            "organization",
            orgId.toString(),
            detailsJson(node -> {
                node.put("org_id", orgId.toString());
                if (countryCode != null && !countryCode.isBlank()) {
                    node.put("country_code", countryCode);
                }
            })
        );
    }

    private static String detailsJson(java.util.function.Consumer<ObjectNode> mutator) {
        var node = JSON.createObjectNode();
        mutator.accept(node);
        try {
            return JSON.writeValueAsString(node);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
