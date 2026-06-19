package com.avandocmsg.messenger.api.admin.dto;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.port.ChatRetentionPolicyPort;
import com.avandocmsg.messenger.core.port.RetentionPolicyPort;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Эффективная политика ретенции для чата: платформа → org (по владельцу/участникам) → строка {@code chat_retention_policy}.
 */
@Schema(description = "Эффективная политика ретенции чата (GET и ответ PATCH)")
public record ChatRetentionPolicyResponse(
    @JsonProperty("chat_id") String chatId,
    @Schema(description = "Организация, по которой подтянут слой org (nullable)")
    @JsonProperty("base_org_id") String baseOrgId,
    @JsonProperty("hot_message_body_max_age_days") Integer hotMessageBodyMaxAgeDays,
    @JsonProperty("hot_metadata_min_age_days") Integer hotMetadataMinAgeDays,
    @JsonProperty("archive_metadata_enabled") boolean archiveMetadataEnabled,
    @JsonProperty("deep_archive_enabled") boolean deepArchiveEnabled,
    @JsonProperty("legal_hold") boolean legalHold,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("updated_by") String updatedBy
) {
    public static ChatRetentionPolicyResponse resolved(
        UUID chatId,
        Optional<UUID> baseOrgId,
        AppConfig app,
        Optional<RetentionPolicyPort.StoredRow> orgStored,
        Optional<ChatRetentionPolicyPort.StoredRow> chatStored
    ) {
        RetentionPolicyResponse orgLayer = baseOrgId
            .map(oid -> RetentionPolicyResponse.resolved(oid, app, orgStored))
            .orElseGet(() -> RetentionPolicyResponse.platformDefaults(app));

        if (chatStored.isEmpty()) {
            return new ChatRetentionPolicyResponse(
                chatId.toString(),
                baseOrgId.map(UUID::toString).orElse(null),
                orgLayer.hotMessageBodyMaxAgeDays(),
                orgLayer.hotMetadataMinAgeDays(),
                orgLayer.archiveMetadataEnabled(),
                orgLayer.deepArchiveEnabled(),
                orgLayer.legalHold(),
                orgLayer.updatedAt(),
                orgLayer.updatedBy()
            );
        }
        var ch = chatStored.get();
        Integer body = ch.hotMessageBodyMaxAgeDays() != null ? ch.hotMessageBodyMaxAgeDays() : orgLayer.hotMessageBodyMaxAgeDays();
        Integer meta = ch.hotMetadataMinAgeDays() != null ? ch.hotMetadataMinAgeDays() : orgLayer.hotMetadataMinAgeDays();
        return new ChatRetentionPolicyResponse(
            chatId.toString(),
            baseOrgId.map(UUID::toString).orElse(null),
            body,
            meta,
            ch.archiveMetadataEnabled(),
            ch.deepArchiveEnabled(),
            ch.legalHold(),
            ch.updatedAt(),
            ch.updatedBy()
        );
    }
}
