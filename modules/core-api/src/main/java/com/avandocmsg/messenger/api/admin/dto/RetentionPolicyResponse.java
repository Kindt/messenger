package com.avandocmsg.messenger.api.admin.dto;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.port.RetentionPolicyPort;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Эффективная политика ретенции для организации: слияние строки БД (если есть) с дефолтами {@link AppConfig}.
 */
@Schema(description = "Эффективная политика ретенции организации (GET и ответ PATCH; см. docs/RETENTION_AND_DEEP_ARCHIVE.md)")
public record RetentionPolicyResponse(
    @Schema(description = "ID организации (null = только дефолты платформы, поле может отсутствовать в JSON)")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("org_id") String orgId,
    @Schema(description = "Макс. возраст тела сообщения в Hot DB (дни); null = не ограничено на уровне платформы")
    @JsonProperty("hot_message_body_max_age_days") Integer hotMessageBodyMaxAgeDays,
    @Schema(description = "Мин. возраст метаданных в Hot (дни); null = не задано")
    @JsonProperty("hot_metadata_min_age_days") Integer hotMetadataMinAgeDays,
    @JsonProperty("archive_metadata_enabled") boolean archiveMetadataEnabled,
    @JsonProperty("deep_archive_enabled") boolean deepArchiveEnabled,
    @JsonProperty("legal_hold") boolean legalHold,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("updated_by") String updatedBy
) {
    /** Дефолты платформы без привязки к org (для слияния политики чата, когда базовая org не выведена). */
    public static RetentionPolicyResponse platformDefaults(AppConfig app) {
        return new RetentionPolicyResponse(
            null,
            app.retentionDefaultHotBodyMaxAgeDays(),
            app.retentionDefaultHotMetadataMinAgeDays(),
            app.retentionDefaultArchiveMetadataEnabled(),
            app.retentionDefaultDeepArchiveEnabled(),
            app.retentionDefaultLegalHold(),
            null,
            null
        );
    }

    public static RetentionPolicyResponse resolved(UUID orgId, AppConfig app, Optional<RetentionPolicyPort.StoredRow> stored) {
        if (stored.isEmpty()) {
            return new RetentionPolicyResponse(
                orgId.toString(),
                app.retentionDefaultHotBodyMaxAgeDays(),
                app.retentionDefaultHotMetadataMinAgeDays(),
                app.retentionDefaultArchiveMetadataEnabled(),
                app.retentionDefaultDeepArchiveEnabled(),
                app.retentionDefaultLegalHold(),
                null,
                null
            );
        }
        var r = stored.get();
        return new RetentionPolicyResponse(
            orgId.toString(),
            r.hotMessageBodyMaxAgeDays() != null ? r.hotMessageBodyMaxAgeDays() : app.retentionDefaultHotBodyMaxAgeDays(),
            r.hotMetadataMinAgeDays() != null ? r.hotMetadataMinAgeDays() : app.retentionDefaultHotMetadataMinAgeDays(),
            r.archiveMetadataEnabled(),
            r.deepArchiveEnabled(),
            r.legalHold(),
            r.updatedAt(),
            r.updatedBy()
        );
    }
}
